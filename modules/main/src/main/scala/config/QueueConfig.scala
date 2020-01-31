// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package itac
package config

import cats.implicits._
import io.circe._
import io.circe.generic.semiauto._
import edu.gemini.tac.qengine.api.config.ConditionsBin
import edu.gemini.tac.qengine.api.config.ConditionsBinGroup
import edu.gemini.qengine.skycalc.RaBinSize
import edu.gemini.qengine.skycalc.DecBinSize
// import edu.gemini.tac.qengine.api.queue.time.PartnerTime

// queue configuration
final case class QueueConfig(
  site:       Site,
  totalHours: Double,
  bands:      BandPercentages,
  overfill:   Option[Percent],
  raBinSize:  RaBinSize,
  decBinSize: DecBinSize,
  conditionsBins: List[ConditionsBin[Percent]]
) {

  object engine {
    import edu.gemini.tac.qengine.util.Time
    import edu.gemini.tac.qengine.ctx.{ Partner => ItacPartner }
    import edu.gemini.tac.qengine.api.queue.time.{ QueueTime => ItacQueueTime }
    import edu.gemini.tac.qengine.api.queue.time.{ PartnerTimes => ItacPartnerTime }

    def fullPartnerTime(allPartners: List[ItacPartner]): ItacPartnerTime =
      // PartnerTime.distribute(Time.hours(totalHours), site, allPartners)
    {
      val pt = ItacPartnerTime(
        allPartners,
        allPartners.fproduct(p => Time.hours(totalHours * p.percentAt(site) / 100.0)).toMap
      )
      // println(s">> fullPartnerTime: $pt")
      pt
    }

    def queueTime(allPartners: List[ItacPartner]): ItacQueueTime =
      new ItacQueueTime(
        site,
        fullPartnerTime(allPartners),
        bands.engine.queueBandPercentages,
        overfill
      )

    lazy val conditionsBins: ConditionsBinGroup[Percent] =
      ConditionsBinGroup.of(QueueConfig.this.conditionsBins)

  }

}

object QueueConfig {
  import itac.codec.all._

  implicit val EncoderQueue: Encoder[QueueConfig] = deriveEncoder
  implicit val DecoderQueue: Decoder[QueueConfig] = deriveDecoder

}