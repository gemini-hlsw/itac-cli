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

object Queue {

  def apply[F[_]: Sync: Parallel](qe: QueueEngine): Operation[F] =
    new Operation[F] {

      def run(ws: Workspace[F], log: Logger[F]): F[ExitCode] =
        for {
          cc <- ws.commonConfig
          qc <- ws.queueConfig("example")
          ps <- ws.proposals
          partners  = cc.engine.partners
          // _ = println(s">> partners = $partners")
          // _ = println(s">> partnersSequence = ${cc.engine.partnerSequence(qc.site)}")
          // _ = println(s">> partner quanta = ${qc.engine.queueTime(partners).partnerQuanta}")
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
            val q = queueCalc.queue
            q.bandedQueue.toList.traverse { case (b, ps) =>
                  Sync[F].delay { println(b); ps.foreach(p => println(" " + p.id.reference + s" -> ${qc.site.abbreviation()}-${cc.semester}-Q-" + (q.positionOf(p).get.programNumber))) }
                }
              }
        } yield ExitCode.Success

  }

}