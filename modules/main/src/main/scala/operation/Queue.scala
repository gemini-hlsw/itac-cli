// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package itac
package operation

import cats._
import cats.effect._
import cats.implicits._
import edu.gemini.tac.qengine.api.config._
import edu.gemini.tac.qengine.api.QueueEngine
import edu.gemini.tac.qengine.p2.rollover.RolloverReport
import io.chrisdavenport.log4cats.Logger
import edu.gemini.tac.qengine.p1.QueueBand
import edu.gemini.tac.qengine.log.AcceptMessage
import edu.gemini.tac.qengine.log.RejectPartnerOverAllocation
import edu.gemini.tac.qengine.log.RejectNotBand3
import itac.codec.all._
import edu.gemini.tac.qengine.log.RejectNoTime
import java.nio.file.Path
import _root_.edu.gemini.tac.qengine.log.RejectCategoryOverAllocation
import edu.gemini.tac.qengine.log.RejectTarget
import edu.gemini.tac.qengine.log.RejectConditions
import edu.gemini.tac.qengine.ctx.Context
import edu.gemini.qengine.skycalc.RaBinSize
import edu.gemini.qengine.skycalc.DecBinSize
import edu.gemini.qengine.skycalc.RaDecBinCalc
import edu.gemini.qengine.skycalc.Hours
import edu.gemini.tac.qengine.util.Time
import edu.gemini.tac.qengine.util.Percent
import edu.gemini.tac.qservice.impl.shutdown.ShutdownCalc

object Queue {

    private def shutdownHours(shutdowns : List[Shutdown], ctx: Context, size: RaBinSize): List[Time] =
      ShutdownCalc.sumHoursPerRa(ShutdownCalc.trim(shutdowns, ctx), size)

    def createConfig(ctx: Context, ra: RaBinSize, dec: DecBinSize, shutdowns: List[Shutdown]): SiteSemesterConfig = {
      import scala.collection.JavaConverters._
      val calc = RaDecBinCalc.get(ctx.site, ctx.semester, ra, dec)
      val hrs  = calc.getRaHours.asScala.map((h: Hours) => Time.hours(h.getHours))
      println("hrs = " + hrs)
      val perc = calc.getDecPercentages.asScala.map(p => Percent(p.getAmount.round.toInt.toDouble))
      val adjHrs = hrs.zip(shutdownHours(shutdowns, ctx, ra)).map { case (t1, t2) => (t1 - t2).max(Time.ZeroHours) }
      val sc = new SiteSemesterConfig(ctx.site, ctx.semester, RaBinGroup(adjHrs), DecBinGroup(perc), shutdowns)
      println(sc)
      sys.exit(-1)
    }

  /**
    * @param siteConfig path to site-specific configuration file, which can be absolute or relative
    *   (in which case it will be resolved relative to the workspace directory).
    */
  def apply[F[_]: Sync: Parallel](
    qe:        QueueEngine,
    siteConfig: Path
  ): Operation[F] =
    new Operation[F] {


      def run(ws: Workspace[F], log: Logger[F]): F[ExitCode] =
        for {
          cc <- ws.commonConfig
          qc <- ws.queueConfig(siteConfig)
          ps <- ws.proposals
          partners  = cc.engine.partners
          queueCalc = qe.calc(
            proposals = ps,
            queueTime = qc.engine.queueTime(partners),
            partners  = partners,
            config    = QueueEngineConfig(
              partners   = partners,
              partnerSeq = cc.engine.partnerSequence(qc.site),
              rollover   = RolloverReport(Nil), // TODO
              // binConfig  = new SiteSemesterConfig(
              //   site       = qc.site,
              //   semester   = cc.semester,
              //   raLimits   = qc.engine.raLimits,
              //   decLimits  = qc.engine.decLimits,
              //   shutdowns  = cc.engine.shutdowns(qc.site),
              //   conditions = qc.engine.conditionsBins
              // ),
              binConfig = createConfig(
                ctx = Context(qc.site, cc.semester),
                ra  = new RaBinSize(3 * 60),
                dec = new DecBinSize(20),
                shutdowns = cc.engine.shutdowns(qc.site)
              ),
              restrictedBinConfig = RestrictionConfig(
                relativeTimeRestrictions = Nil, // TODO
                absoluteTimeRestrictions = Nil, // TODO
                bandRestrictions         = Nil, // TODO
              )
            ),
          )
          qf <- ws.newQueueFolder(qc.site)
          _  <- ws.writeData(qf.resolve("common.yaml"), cc)
          _  <- ws.writeData(qf.resolve(s"${qc.site.abbreviation}.yaml"), qc)
          _  <- ws.writeData(qf.resolve("queue.yaml"), queueCalc)

          _  <- {
            val log = queueCalc.proposalLog
            Sync[F].delay {

              val pids = log.proposalIds // proposals that were considered

              println(s"${Console.BOLD}The following proposals were not considered due to site, mode, or lack of awarded time or observations.${Console.RESET}")
              ps.filterNot(p => pids.contains(p.id)).foreach { p =>
                println(f"- ${p.id.reference} (${p.site.abbreviation}, ${p.mode.programId}%2s, ${p.ntac.awardedTime.toHours.value}%4.1fh ${p.ntac.partner.id}, ${p.obsList.length}%3d obs)")
              }

              QueueBand.Category.values.foreach { qc =>
                println(s"${Console.BOLD}The following proposals were rejected for $qc.${Console.RESET}")
                pids.foreach { pid =>
                  log.get(pid, qc) match {
                    case None =>
                    case Some(AcceptMessage(_, _, _)) =>
                    case Some(m: RejectPartnerOverAllocation) => println(s"- ${pid.reference} - ${m.detail}")
                    case Some(m: RejectNotBand3) => println(s"- ${pid.reference} - ${m.detail}")
                    case Some(m: RejectNoTime) => println(s"- ${pid.reference} - ${m.detail}")
                    case Some(m: RejectCategoryOverAllocation) => println(s"- ${pid.reference} - ${m.detail}")
                    case Some(m: RejectTarget) => println(s"- ${pid.reference} - ${m.detail}")
                    case Some(m: RejectConditions) => println(s"- ${pid.reference} - ${m.detail}")
                    case Some(lm) => println(s"- ${pid.reference} - $lm")
                  }
                }
              }

              QueueBand.values.foreach { qb =>
                val q = queueCalc.queue
                println(s"${Console.BOLD}The following proposals were accepted for Band ${qb.number}.${Console.RESET}")
                println(qb.number match {
                  case 1 => Console.YELLOW
                  case 2 => Console.GREEN
                  case 3 => Console.BLUE
                  case 4 => Console.RED
                })
                q.bandedQueue.get(qb).orEmpty.foreach { p =>
                  println(f"- ${p.id.reference}%-20s -> ${qc.site.abbreviation}-${cc.semester}-${p.mode.programId}-${q.positionOf(p).get.programNumber} ${p.piName.orEmpty}")
                }
                println(Console.RESET)
              }

            }
          }
        } yield ExitCode.Success

  }

}