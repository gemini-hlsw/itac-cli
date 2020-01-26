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
import itac.codec.site
import edu.gemini.tac.qengine.log.RejectNoTime
import java.nio.file.Path
// import edu.gemini.tac.qengine.log.ProposalLog.Entry

object Queue {

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
              binConfig  = new SiteSemesterConfig(
                site       = qc.site,
                semester   = cc.semester,
                raLimits   = qc.engine.raLimits,
                decLimits  = qc.engine.decLimits,
                shutdowns  = cc.engine.shutdowns(qc.site),
                conditions = qc.engine.conditionsBins
              ),
              restrictedBinConfig = RestrictionConfig(
                relativeTimeRestrictions = Nil, // TODO
                absoluteTimeRestrictions = Nil, // TODO
                bandRestrictions         = Nil, // TODO
              )
            ),
          )
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
                    case Some(lm) => println(s"- ${pid.reference} - $lm")
                  }
                }
              }

              QueueBand.values.foreach { qb =>
                val q = queueCalc.queue
                println(s"${Console.BOLD}The following proposals were accepted for Band ${qb.number}.${Console.RESET}")
                q.bandedQueue.get(qb).orEmpty.foreach { p =>
                  println(s"- ${p.id.reference} -> ${qc.site.abbreviation()}-${cc.semester}-${p.mode.programId}-${q.positionOf(p).get.programNumber}")
                }
              }

              println(s"${Console.BOLD}Partner Fairness${Console.RESET}")
              QueueBand.Category.values.foreach { qc =>
                val pf = queueCalc.queue.partnerFairness(qc, partners)
                println(s"${qc.name}: ")
                partners.foreach { p =>
                  println(s"  ${p.id} -> ${pf.errorPercent(p)}")
                }
              }


            }
          }
        } yield ExitCode.Success

  }

}