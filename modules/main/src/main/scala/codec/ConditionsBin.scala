package itac.codec

import io.circe._
import io.circe.syntax._
import edu.gemini.tac.qengine.api.config.ConditionsBin
import edu.gemini.tac.qengine.api.config.ConditionsCategory

trait ConditionsBinCodec {
  import conditionscategory._

  implicit def EncoderConditionsBin[A: Encoder]: Encoder[ConditionsBin[A]] =
    Encoder.instance { b =>
      Json.obj(
        "conditions" -> b.cat.asJson,
        "name"       -> b.cat.name.asJson,
        "value"      -> b.binValue.asJson
      )
    }

  implicit def DecoderConditionsBin[A: Decoder]: Decoder[ConditionsBin[A]] =
    Decoder.instance { c =>
      for {
        cat   <- c.downField("conditions").as[ConditionsCategory]
        name  <- c.downField("name").as[Option[String]]
        value <- c.downField("value").as[A]
      } yield ConditionsBin(cat.copy(name = name), value)
    }

}

object conditionsbin extends ConditionsBinCodec