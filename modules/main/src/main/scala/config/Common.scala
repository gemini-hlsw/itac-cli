package itac.config

import cats.implicits._
import io.circe._
import io.circe.generic.semiauto._

final case class Common(
  semester: Semester,
  shutdown: PerSite[List[LocalDateRange]],
  partners: Partner => PartnerConfig,
  sequence: PerSite[List[Partner]]
) {

  object engine {
    import edu.gemini.tac.qengine.ctx.{ Partner => ItacPartner }

    def partners: List[ItacPartner] =
      Partner.all.map { p =>
        val cfg = Common.this.partners(p)
        ItacPartner(p.id, p.name, cfg.percent, cfg.sites.toSet)
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