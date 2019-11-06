package itac.codec

import io.circe._
import io.circe.generic.semiauto._
import itac.data.CommonConfig
import itac.data.CommonConfig._


trait CommonConfigCodec {
  import partner._
  import semester._
  import localdaterange._

  implicit val EncodeSequence: Encoder[Sequence] = deriveEncoder
  implicit val DecodeSequence: Decoder[Sequence] = deriveDecoder

  implicit val EncodeShutdowns: Encoder[Shutdowns] = deriveEncoder
  implicit val DecodeShutdowns: Decoder[Shutdowns] = deriveDecoder

  implicit lazy val EncodeCommon: Encoder[CommonConfig] = deriveEncoder
  implicit lazy val DecodeCommon: Decoder[CommonConfig] = deriveDecoder

}

object commonconfig extends CommonConfigCodec