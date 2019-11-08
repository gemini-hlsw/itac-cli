package itac.data

import cats.implicits._
import CommonConfig._
import edu.gemini.tac.qengine.api.config.Shutdown
import edu.gemini.tac.qengine.ctx.Partner
import edu.gemini.tac.qengine.ctx.Semester
import edu.gemini.tac.qengine.ctx.Site
import edu.gemini.tac.qengine.util.Percent
import java.time.LocalDate
import java.util.Date

/** CommonConfig configuration. */
case class CommonConfig(
  semester:  Semester,
  shutdowns: Shutdowns,
  partners:  List[Partner],
  sequence:  Sequence
) {

  // This should be correct on construction via Circe decoders, but just in case:
  assert(sequence.gn.forall(partners.contains), "CommonConfig: `sequence.gn` contains a partner not in `partners`")
  assert(sequence.gs.forall(partners.contains), "CommonConfig: `sequence.gs` contains a partner not in `partners`")
  assert(partners.foldMap(_.share.value) == BigDecimal(1), "CommonConfig: `partner.share` values do not add up to 1.0")

}

object CommonConfig {

  case class Sequence(gn: List[Partner], gs: List[Partner])

  case class Shutdowns(gn: List[(LocalDate, LocalDate)], gs: List[(LocalDate, LocalDate)]) {

    /** Shutdowns for a given site. */
    def forSite(site: Site): List[Shutdown] = {
      (if (site == Site.north) gn else gs).map { case (start, end) =>
        // Old API wants j.u.Date … is it ok to assume noon for start/end times?
        def toSiteDate(ld: LocalDate): Date =
          new Date(ld.atStartOfDay(site.timeZone.toZoneId).plusHours(12).toEpochSecond)
        Shutdown(site, toSiteDate(start), toSiteDate(end))
      }
    }

  }

  def default(semester: Semester): CommonConfig =
    CommonConfig(
      semester,
      Shutdowns(Nil, Nil),
      List(
        Partner("US", "United States", Percent(0.5), Set(Site.north, Site.south)),
        Partner("CL", "Chile",         Percent(0.1), Set(Site.north, Site.south)),
        Partner("AR", "Argentina",     Percent(0.1), Set(Site.north, Site.south)),
        Partner("CA", "Canada",        Percent(0.1), Set(Site.north, Site.south)),
        Partner("BR", "Brazil",        Percent(0.1), Set(Site.north, Site.south)),
        Partner("KR", "Korea",         Percent(0.1), Set(Site.north, Site.south)),
      ),
      Sequence(Nil, Nil)
    )

}

