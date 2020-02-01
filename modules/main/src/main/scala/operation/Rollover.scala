// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package itac
package operation

import cats.effect._
import cats.implicits._
import edu.gemini.spModel.core.Site
import edu.gemini.tac.qengine.p2.rollover.RolloverReport
import io.chrisdavenport.log4cats.Logger
import java.net.URL
import scala.xml.Elem
import scala.xml.XML
import cats.Parallel
import java.nio.file.{ Path, Paths }

object Rollover {

  def apply[F[_]: ContextShift: Parallel: Sync](
    site: Site,
    out:  Option[Path]
  ): Operation[F] =
    new Operation[F] {

      def run(ws: Workspace[F], log: Logger[F], b: Blocker): F[ExitCode] = {

        val fetchXml: F[Elem] = {
          val host = if (site == Site.GN) "gnodb" else "gsodb"
          val url  = s"http://$host.gemini.edu:8442/rollover"
          b.blockOn(Sync[F].delay(XML.load(new URL(url))))
        }

        val fetchRolloverReport: F[Either[String, RolloverReport]] =
          (ws.commonConfig, fetchXml).parMapN { (cc, xml) =>
            RolloverReport.fromXml(xml, cc.engine.partners)
          }

        fetchRolloverReport.flatMap {
          case Left(msg) => Sync[F].raiseError(ItacException(msg))
          case Right(rr) =>
            for {
              _   <- log.info(s"Fetched rollover report for ${rr.semester} from ${rr.site.displayName}.")
              outʹ = out.getOrElse(Paths.get(s"${site.abbreviation}-rollover.csv"))
              _   <- ws.writeRolloveReport(outʹ, rr)
            } yield ExitCode.Success
        }

      }

    }


}


// import gsp.math.{ Angle => GAngle, HourAngle }
// ros.foreach { o =>
//   val ra = HourAngle.HMS(HourAngle.fromDoubleHours(o.target.ra.toHr.mag)).format
//   val dec = GAngle.DMS(GAngle.fromDoubleDegrees(o.target.dec.toDeg.mag)).format
//   println(f"${o.partner.id}%-7s ${o.obsId}%-17s $ra%16s $dec%17s ${o.conditions.cc}%-6s ${o.conditions.iq}%-6s ${o.conditions.sb}%-6s ${o.conditions.wv}%-6s ${o.time.toMinutes}")
// }



