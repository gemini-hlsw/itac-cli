// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package itac.config

import cats.implicits._
import io.circe._
import io.circe.generic.semiauto._

// queue configuration
final case class Queue(
  site:       Site,
  totalHours: Double,
  bands:      BandPercentages,
  overfill:   Option[Percent],
  raBins:     Binned[Double],
  decBins:    Binned[Double]
) {

  object engine {
    import edu.gemini.tac.qengine.util.Time
    import edu.gemini.tac.qengine.ctx.{ Partner => ItacPartner }
    import edu.gemini.tac.qengine.api.queue.time.{ QueueTime => ItacQueueTime }
    import edu.gemini.tac.qengine.api.queue.time.{ PartnerTime => ItacPartnerTime }

    def fullPartnerTime(allPartners: List[ItacPartner]): ItacPartnerTime =
      ItacPartnerTime(
        allPartners,
        allPartners.fproduct(p => Time.hours(totalHours * p.share.value.toDouble)).toMap
      )

    def queueTime(allPartners: List[ItacPartner]): ItacQueueTime =
      new ItacQueueTime(
        site,
        fullPartnerTime(allPartners),
        bands.engine.queueBandPercentages,
        overfill
      )

  }

}

object Queue {
  import itac.codec.percent._
  import itac.codec.site._

  implicit val EncoderQueue: Encoder[Queue] = deriveEncoder
  implicit val DecoderQueue: Decoder[Queue] =
    Decoder { c =>
      for {
        site       <- c.downField("site").as[Site]
        totalHours <- c.downField("totalHours").as[Double]
        bands      <- c.downField("bands").as[BandPercentages]
        overfill   <- c.downField("overfill").as[Option[Percent]]
        raBins     <- c.downField("raBins").as(Binned.decoder[Double](0, 24))
        decBins    <- c.downField("decBins").as(Binned.decoder[Double](-90, 90))
      } yield Queue(site, totalHours, bands, overfill, raBins, decBins)
    }

}