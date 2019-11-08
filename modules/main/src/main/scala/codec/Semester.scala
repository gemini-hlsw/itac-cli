package itac.codec

import cats.implicits._
import io.circe._
import edu.gemini.tac.qengine.ctx.Semester
import java.text.ParseException

trait SemesterCodec {

  implicit val EncoderSemester: Encoder[Semester] =
    Encoder[String].contramap(_.toString)

  implicit val DecoderSemester: Decoder[Semester] =
    Decoder[String].emap { s =>
      Either.catchOnly[ParseException](Semester.parse(s)).leftMap(_.getMessage)
    }

}

object semester extends SemesterCodec