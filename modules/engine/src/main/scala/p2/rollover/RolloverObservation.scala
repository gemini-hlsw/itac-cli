// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package edu.gemini.tac.qengine.p2.rollover

import edu.gemini.spModel.core.Site
import edu.gemini.tac.qengine.ctx.Partner
import edu.gemini.tac.qengine.p1._
import edu.gemini.tac.qengine.p1.{CategorizedTime, ObservingConditions, Target}
import edu.gemini.tac.qengine.p2.ObservationId
import edu.gemini.tac.qengine.util.Angle
import edu.gemini.tac.qengine.util.Time
import gsp.math.{ Angle => GAngle, HourAngle }
import scala.util.Try
import scalaz._, Scalaz._

/**
 * A class that represents a rollover time observation.  The time for each
 * rollover observation should be subtracted from the corresponding bins.
 */
case class RolloverObservation(
  partner: Partner,
  obsId: ObservationId,
  target: Target,
  conditions: ObservingConditions,
  time: Time
) extends CategorizedTime {

  def site: Site = obsId.site

  /**
    * Convert to a whitespace-delimited string with RA/Dec in HMS/DMS. Humans will read this and
    * perhaps edit it, then it will be parsed back via fromDelimitedString. So it's ok to change if
    * users ask, but you'll neeed to change the parser as well. Lines look like this. Abundant
    * space is given to ensure that printed rows line up.
    *
    * US      GN-2019B-Q-138-40  08:05:47.659920   45:41:58.999200 CC50   IQ70   SBAny  WVAny  192.16633 min
    */
  def toDelimitedString: String = {
    val ra = HourAngle.HMS(HourAngle.fromDoubleHours(target.ra.toHr.mag)).format
    val dec = GAngle.DMS(GAngle.fromDoubleDegrees(target.dec.toDeg.mag)).format
    f"${partner.id}%-7s ${obsId}%-17s $ra%16s $dec%17s ${conditions.cc}%-6s ${conditions.iq}%-6s ${conditions.sb}%-6s ${conditions.wv}%-6s ${time.toMinutes}"
  }

}


object RolloverObservation {

  /**
   * Parse a delimited string as specifeid by toDelimitedString, yielding a RolloverObservation or a
   * failure message.
   */
  def fromDelimitedString(s: String, partners: List[Partner]): Either[String, RolloverObservation] =
    s.split("\\s+") match {
      case Array(pid, oid, ra, dec, cc, iq, sb, wv, mins, "min") =>

        def fail(field: String): Nothing =
          sys.error(s"Error parsing RolloverObservation: invalid $field\n$s")

        Try {
          val pidʹ  = partners.find(_.id == pid).getOrElse(fail("partner id"))
          val oidʹ  = ObservationId.parse(oid).getOrElse(fail("observation id"))
          val raʹ   = HourAngle.fromStringHMS.getOption(ra).getOrElse(fail("RA"))
          val decʹ  = GAngle.fromStringDMS.getOption(dec).getOrElse(fail("DEC"))
          val ccʹ   = CloudCover.values.find(_.toString == cc).getOrElse(fail("CC"))
          val iqʹ   = ImageQuality.values.find(_.toString == iq).getOrElse(fail("IQ"))
          val sbʹ   = SkyBackground.values.find(_.toString == sb).getOrElse(fail("SB"))
          val wvʹ   = WaterVapor.values.find(_.toString == wv).getOrElse(fail("WV"))
          val minsʹ = Time.minutes(mins.toDouble)
          RolloverObservation(pidʹ, oidʹ, Target(raʹ.toDoubleDegrees, decʹ.toDoubleDegrees), ObservingConditions(ccʹ, iqʹ, sbʹ, wvʹ), minsʹ)
        } .toEither.leftMap(_.getMessage)

      case arr => Left(s"Expected ten columns, found ${arr.length}: $s")
    }

  /**
   * Parse a `RolloverObservation` from an XML element, as provided by the OCS SPDB rollover
   * servlet. Such an element looks like this. Remaining time is in milliseconds.
   *
   *  <obs>
   *    <id>GS-2019B-Q-107-5</id>
   *    <partner>Argentina</partner>
   *    <target>
   *      <ra>122.252373 deg</ra>
   *      <dec>-61.302384 deg</dec>
   *    </target>
   *    <conditions>
   *      <cc>50</cc>
   *      <iq>70</iq>
   *      <sb>100</sb>
   *      <wv>100</wv>
   *    </conditions>
   *    <time>5497000</time>
   *  </obs
   *
   * You can fetch such a report yourself via
   *
   *     curl -i http://gsodb.gemini.edu:8442/rollover
   *
   * @return a RolloverObservation, or a message on failure.
   */
  def fromXml(o: scala.xml.Node, partners: List[Partner]): Either[String, RolloverObservation] =
    Try {

      def fail(field: String): Nothing =
        sys.error(s"Error parsing RolloverObservation: missing or invalid $field\n$o")

      val id   = ObservationId.parse((o \ "id").text).getOrElse(fail("observation id"))
      val p    = partners.find(_.fullName == (o \ "partner").text).getOrElse(fail("partner"))
      val time = Time.millisecs((o \ "time").text.toLong).toMinutes
      val ra   = new Angle((o \ "target" \ "ra" ).text.takeWhile(_ != ' ').toDouble, Angle.Deg)
      val dec  = new Angle((o \ "target" \ "dec").text.takeWhile(_ != ' ').toDouble, Angle.Deg)
      val t    = Target(ra, dec, None)
      val cc   = CloudCover   .values.find(_.percent == (o \ "conditions" \ "cc").text.toInt).getOrElse(fail("cc"))
      val iq   = ImageQuality .values.find(_.percent == (o \ "conditions" \ "iq").text.toInt).getOrElse(fail("iq"))
      val sb   = SkyBackground.values.find(_.percent == (o \ "conditions" \ "sb").text.toInt).getOrElse(fail("sb"))
      val wv   = WaterVapor   .values.find(_.percent == (o \ "conditions" \ "wv").text.toInt).getOrElse(fail("wv"))

      RolloverObservation(p, id, t, ObservingConditions(cc, iq, sb, wv), time)

    } .toEither.leftMap(_.getMessage)

}