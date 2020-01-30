// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package itac.config

import cats.implicits._
import io.circe._
import io.circe.generic.semiauto._
import edu.gemini.tac.qengine.api.config.Shutdown
import java.{util => ju}
import java.time.LocalDate

final case class Common(
  semester: Semester,
  shutdown: PerSite[List[LocalDateRange]],
  partners: Partner => PartnerConfig,
  sequence: PerSite[List[Partner]]
) { self =>

  object engine {
    import edu.gemini.tac.qengine.ctx.{ Partner => ItacPartner }
    import edu.gemini.tac.qengine.api.config.{ PartnerSequence => ItacPartnerSequence }

    val partnersMap: Map[Partner, ItacPartner] =
      Partner.all.map { p =>
        val cfg = Common.this.partners(p)
        p -> ItacPartner(p.id, p.name, cfg.percent, cfg.sites.toSet)
      } .toMap

    val partners: List[ItacPartner] =
      partnersMap.values.toList

    def partnerSequence(site: Site): ItacPartnerSequence =
      new ItacPartnerSequence {
        def sequence = self.sequence.forSite(site).map(partnersMap).toStream #::: sequence
        override def toString = s"ItacPartnerSequence(...)"
      }

    def shutdowns(site: Site): List[Shutdown] =
      shutdown.forSite(site).map { ldr =>
        // Turn a LocalDate to a ju.Date at noon at `site`.
        def date(ldt: LocalDate): ju.Date = {
          val zid = site.timezone.toZoneId
          val zdt = ldt.atStartOfDay(zid).plusHours(12L)
          new ju.Date(zdt.toEpochSecond * 1000)
        }
        Shutdown(site, date(ldr.start), date(ldr.end))
      }

  }

}

object Common {
  import Partner._ // need higher-priority implicit for sequence
  import itac.codec.semester._

  implicit val encodePartners: Encoder[Partner => PartnerConfig] =
    Encoder[Map[Partner, PartnerConfig]].contramap(Partner.all.fproduct(_).toMap)

  implicit val decodePartners: Decoder[Partner => PartnerConfig] =
    Decoder[Map[Partner, PartnerConfig]].emap { m =>
      val undef = Partner.all.filterNot(m.isDefinedAt).map(_.id)
      Either.cond(undef.isEmpty, m, undef.mkString("Missing partner config: ", " ", ""))
    }

  implicit val encoderCommon: Encoder[Common] = deriveEncoder
  implicit val decoderCommon: Decoder[Common] = deriveDecoder

  /** A dummy configuration, for `init` when no prior config is available. */
  def dummy(semester: Semester): Common =
    Common(
      semester = semester,
      shutdown = PerSite(
        gn = List(LocalDateRange.dummy(semester, GN)),
        gs = List(LocalDateRange.dummy(semester, GS))
      ),
      partners = Partner.all.fproduct(_.dummyConfig).toMap,
      sequence = PerSite(Partner.all, Partner.all)
    )

}