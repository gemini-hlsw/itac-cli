// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package itac
package config

import cats.implicits._
import io.circe._
import io.circe.generic.semiauto._
import edu.gemini.tac.qengine.api.config.RaBinGroup
import edu.gemini.tac.qengine.api.config.DecBinGroup
import edu.gemini.tac.qengine.api.config.ConditionsBin
import edu.gemini.tac.qengine.api.config.ConditionsBinGroup
// import edu.gemini.tac.qengine.api.queue.time.PartnerTime

// queue configuration
final case class QueueConfig(
  site:       Site,
  totalHours: Double,
  bands:      BandPercentages,
  overfill:   Option[Percent],
  raBins:     Binned[Double],
  decBins:    Binned[Percent],
  conditionsBins: List[ConditionsBin[Percent]]
) {

  object engine {
    import edu.gemini.tac.qengine.util.Time
    import edu.gemini.tac.qengine.ctx.{ Partner => ItacPartner }
    import edu.gemini.tac.qengine.api.queue.time.{ QueueTime => ItacQueueTime }
    import edu.gemini.tac.qengine.api.queue.time.{ PartnerTime => ItacPartnerTime }

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

    lazy val raLimits: RaBinGroup[Time] =
      raBins.map(Time.hours).engine.toRaBinGroup(Time.hours(0.0)).right.get // TODO: validate on decoding!

    lazy val decLimits: DecBinGroup[Percent] =
      decBins.map(d => Percent(d.doubleValue)).engine.toDecBinGroup(Percent.Zero).right.get // TODO: validate on decoding!

    lazy val conditionsBins: ConditionsBinGroup[Percent] =
      ConditionsBinGroup.of(QueueConfig.this.conditionsBins)

  }

}

object QueueConfig {
  import itac.codec.percent._
  import itac.codec.site._
  import itac.codec.conditionsbin._

  implicit val EncoderQueue: Encoder[QueueConfig] = deriveEncoder
  implicit val DecoderQueue: Decoder[QueueConfig] =
    Decoder { c =>
      for {
        site       <- c.downField("site").as[Site]
        totalHours <- c.downField("totalHours").as[Double]
        bands      <- c.downField("bands").as[BandPercentages]
        overfill   <- c.downField("overfill").as[Option[Percent]]
        raBins     <- c.downField("raBins").as(Binned.decoder[Double](0, 24))
        decBins    <- c.downField("decBins").as(Binned.decoder[Percent](-90, 90))
        condBins   <- c.downField("conditionsBins").as[List[ConditionsBin[Percent]]]
      } yield QueueConfig(site, totalHours, bands, overfill, raBins, decBins, condBins)
    }

}