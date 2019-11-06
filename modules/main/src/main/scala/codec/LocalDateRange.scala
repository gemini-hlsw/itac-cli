package itac.codec

import cats.implicits._
import io.circe._
import java.time.LocalDate
import java.time.format.DateTimeFormatter.BASIC_ISO_DATE
import java.time.format.DateTimeParseException

/**
 * Codec for local date range, which overrides the default provided by Circe. The data type is
 * simply a `(LocalDate, LocalDate)` and it is encoded as a string like `20191031-20191031`.
 */
trait LocalDateRangeCodec {

  implicit lazy val EncodeLocalDateRange: Encoder[(LocalDate, LocalDate)] =
    Encoder[String].contramap { case (a, b) =>
      s"${a.format(BASIC_ISO_DATE)}-${b.format(BASIC_ISO_DATE)}"
    }

  implicit lazy val DecodeLocalDateRange: Decoder[(LocalDate, LocalDate)] =
    Decoder[String].emap { case s =>
      s.split("-") match {
        case Array(sa, sb) =>
          Either.catchOnly[DateTimeParseException] {
            (LocalDate.parse(sa, BASIC_ISO_DATE), LocalDate.parse(sb, BASIC_ISO_DATE))
          } .leftMap(_.getMessage)
        case _ => Left(s"Invalid date range: $s")
      }
    }

}

object localdaterange extends LocalDateRangeCodec