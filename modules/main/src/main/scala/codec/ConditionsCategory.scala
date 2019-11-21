// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package itac.codec

import atto._, Atto._
import cats.implicits._
import edu.gemini.tac.qengine.api.config.ConditionsCategory
import edu.gemini.tac.qengine.api.config.ConditionsCategory._
import edu.gemini.tac.qengine.p1.{ io => _, _ }
import io.circe.{ Decoder, Encoder }

/** Encode/decoder unnamed ConditionsCategory in the form "CC50 IQ20 <=SB50". */
trait ConditionsCategoryCodec extends ConditionsCategoryParser {

  implicit val EncoderConditionsCategory: Encoder[ConditionsCategory] =
    Encoder[String].contramap(formatDiscardingName)

  implicit val DecoderConditionsCategory: Decoder[ConditionsCategory] =
    Decoder[String].emap(parseUnnamed)

}

trait ConditionsCategoryParser {

  private sealed trait Comparator
  private case object Gte extends Comparator
  private case object Lte extends Comparator

  private val comparator: Parser[Option[Comparator]] =
    opt(string(">=").as(Gte) | string("<=").as(Lte))

  private def fromToString[A](as: List[A]): Parser[A] =
    as.foldRight(err[A](s"Expected one of ${as.mkString(" ")}")) { (cc, p) =>
      string(cc.toString).as(cc) | p
    }

  private def spec[A <: Var[A]](cond: Parser[A]): Parser[Spec[A]] =
    (comparator, cond).mapN {
      case (None,      c) => Eq(c)
      case (Some(Gte), c) => Le(c)
      case (Some(Lte), c) => Ge(c)
    }

  private val cc: Parser[Spec[CloudCover]]    =
    opt(spec[CloudCover](fromToString(CloudCover.values))).map(_.getOrElse(UnspecifiedCC))

  private val iq: Parser[Spec[ImageQuality]]  =
    opt(spec[ImageQuality](fromToString(ImageQuality.values))).map(_.getOrElse(UnspecifiedIQ))

  private val sb: Parser[Spec[SkyBackground]] =
    opt(spec[SkyBackground](fromToString(SkyBackground.values))).map(_.getOrElse(UnspecifiedSB))

  private val wv: Parser[Spec[WaterVapor]]    =
    opt(spec[WaterVapor](fromToString(WaterVapor.values))).map(_.getOrElse(UnspecifiedWV))

  private val cat: Parser[ConditionsCategory] =
    (cc.token, iq.token, sb.token, wv.token).mapN(ConditionsCategory(_, _, _, _, None))

  private def formatSpec(a: Spec[_]): String =
    a match {
      case _: Unspecified[_] => ""
      case Eq(a) => a.toString
      case Le(a) => s"<=$a"
      case Ge(a) => s">=$a"
    }

  def parseUnnamed(s: String): Either[String, ConditionsCategory] =
    (cat <~ endOfInput).parseOnly(s).either

  def formatDiscardingName(cat: ConditionsCategory): String =
    List(cat.ccSpec, cat.iqSpec, cat.sbSpec, cat.wvSpec).filter {
      case _: Unspecified[_] => false
      case _                 => true
    } .map(formatSpec).mkString(" ")

}

object conditionscategory extends ConditionsCategoryCodec