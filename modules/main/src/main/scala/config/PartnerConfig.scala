package itac.config

import io.circe._
import io.circe.generic.semiauto._
import edu.gemini.tac.qengine.util.Percent

final case class PartnerConfig(
  email:   Email,
  percent: Percent,
  sites:   List[Site]
)

object PartnerConfig {
  import itac.codec.percent._
  import itac.codec.site._

  // TODO: encode sites as a string like GN GS

  implicit val encoderPartnerConfig: Encoder[PartnerConfig] = deriveEncoder
  implicit val decoderPartnerConfig: Decoder[PartnerConfig] = deriveDecoder
}
