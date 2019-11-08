package itac.codec

import cats.implicits._
import io.circe._
import io.circe.syntax._
import io.circe.generic.semiauto._
import itac.data.CommonConfig
import itac.data.CommonConfig._
import edu.gemini.tac.qengine.ctx.Partner
import edu.gemini.tac.qengine.ctx.Semester
import io.circe.CursorOp.DownField

trait CommonConfigCodec {
  import partner._
  import semester._
  import localdaterange._

  implicit val EncodeSequence: Encoder[Sequence] =
    Encoder { seq =>
      Json.obj(
        "gn" -> seq.gn.map(_.id).asJson,
        "gs" -> seq.gs.map(_.id).asJson
      )
    }

  def decodeSequence(ps: List[Partner]): Decoder[Sequence] =
    Decoder.instance { hc =>
      def resolve(tag: String): Decoder.Result[List[Partner]] =
        hc.downField(tag).as[List[String]].flatMap(_.traverse { s =>
          ps.find(_.id == s)
            .toRight(DecodingFailure(s"Unknown partner Id: $s", hc.history :+ DownField(tag)))
        })
      (resolve("gn"), resolve("gs")).mapN(Sequence(_, _))
    }

  implicit val EncodeShutdowns: Encoder[Shutdowns] = deriveEncoder
  implicit val DecodeShutdowns: Decoder[Shutdowns] = deriveDecoder

  implicit lazy val EncodeCommon: Encoder[CommonConfig] = deriveEncoder
  implicit lazy val DecodeCommon: Decoder[CommonConfig] =
    Decoder.instance { hc =>
      for {
        sem <- hc.downField("semester" ).as[Semester]
        sds <- hc.downField("shutdowns").as[Shutdowns]
        ps  <- hc.downField("partners" ).as[List[Partner]].ensure(
          DecodingFailure(s"Partner shares do not add up to 1.0", hc.history :+ DownField("partners")))(
          _.foldMap(_.share.value) == BigDecimal(1)
        )
        seq <- hc.downField("sequence" ).as[Sequence](decodeSequence(ps))
      } yield CommonConfig(sem, sds, ps, seq)
    }

}

object commonconfig extends CommonConfigCodec