// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package edu.gemini.tac.qengine.p2.rollover

import edu.gemini.tac.qengine.util.Time
import edu.gemini.spModel.core.Site
import scala.xml.Node
import edu.gemini.tac.qengine.ctx.Partner
import scala.util.Try
import scalaz._, Scalaz._
import edu.gemini.spModel.core.Semester
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A collection of rollover observations whose time values should be deducted
 * from the corresponding bins.
 */
case class RolloverReport(
  site: Site,
  semester: Semester,
  timestamp: Instant,
  obsList: List[RolloverObservation]
) {

  /**
   * Filters the report for a particular site.
   */
  def filter(site: Site): RolloverReport =
    copy(obsList = obsList.filter(_.site == site))

  /**
   * Total of all rollover observation times.  This amount of time is subtracted
   * from the available queue time.
   */
  def total: Time = obsList.foldLeft(Time.ZeroHours)(_ + _.time)

  /**
    * Convert to a hash-prefixed header followed by series of data lines as described in
    * RolloverObservation.toDelimitedText. Humans will read this and perhaps edit it, then it will
    * be parsed back via fromDelimitedString. So it's ok to change if users ask, but you'll neeed
    * to change the parser as well.
    */
    def toDelimitedText: String = {
      // ITAC person may be at either site so include both local times.
      val ldt = ZonedDateTime.ofInstant(timestamp, ZoneId.systemDefault)
      val fmt = DateTimeFormatter.ofPattern("yyyy-dd-MM HH:mm 'local time' (z)")
      s"""|
          |# This is the $semester rollover report for ${site.displayName}, generated at ${fmt.format(ldt)}.
          |# It is ok to edit this file to change the remaining time (given in minutes).
          |# Blank lines and lines beginning with # are ignored, so it's ok to add comments.
          |
          |""".stripMargin +  obsList.map(_.toDelimitedString).mkString("", "\n", "\n\n")
    }

}

object RolloverReport {

  /**
   * Parse a `RolloverReport` from an XML element, as provided by the OCS SPDB rollover servlet.
   * Such an element looks like this. Timestamp is a Unix epoch time in milliseconds. The format
   * of <obs> elements is given in RolloverObservation.
   *
   *  <rollover site="GS" semester="2020A" timestamp="1580514160465">
   *    <obs>...</obs>
   *    <obs>...</obs>
   *    ...
   *  </rollover>
   *
   * You can fetch such a report yourself via
   *
   *     curl -i http://gsodb.gemini.edu:8442/rollover
   *
   * @return a RolloverReport, or a message on failure.
   */
  def fromXml(rollover: Node, partners: List[Partner]): Either[String, RolloverReport] =
    Try {
      val site = Site.parse(rollover \@ "site")
      val sem  = Semester.parse(rollover \@ "semester")
      val ins  = Instant.ofEpochMilli((rollover \@ "timestamp").toLong)
      val ros  = (rollover \ "obs").toList.traverse { RolloverObservation.fromXml(_, partners) }
      ros match {
        case Right(ros) => RolloverReport(site, sem, ins, ros)
        case Left(e)    => sys.error(e)
      }
    } .toEither.leftMap(_.getMessage)


}
