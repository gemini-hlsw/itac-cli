package itac.config

import io.circe._
import io.circe.generic.semiauto._

final case class PerSite[A](gn: A, gs: A)

object PerSite {
  implicit def encoderPerSite[A: Encoder]: Encoder[PerSite[A]] = deriveEncoder
  implicit def decoderPerSite[A: Decoder]: Decoder[PerSite[A]] = deriveDecoder
}