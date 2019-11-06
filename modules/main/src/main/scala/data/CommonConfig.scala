package itac.data

import edu.gemini.tac.qengine.ctx.Semester
import java.time.LocalDate
import edu.gemini.tac.qengine.ctx.Partner
import CommonConfig._
import edu.gemini.tac.qengine.util.Percent
import edu.gemini.tac.qengine.ctx.Site

/** CommonConfig configuration. */
case class CommonConfig(
  semester:  Semester,
  shutdowns: Shutdowns,
  partners:  List[Partner],
  sequence:  Sequence
)

object CommonConfig {
  case class Sequence(gn: List[Partner], gs: List[Partner])
  case class Shutdowns(gn: List[(LocalDate, LocalDate)], gs: List[(LocalDate, LocalDate)])


  def default(semester: Semester): CommonConfig =
    CommonConfig(
      semester,
      Shutdowns(Nil, Nil),
      List(
        Partner("US", "United States", Percent(0.5), Set(Site.north, Site.south)),
        Partner("CL", "Chile", Percent(0.5), Set(Site.north, Site.south)),
        Partner("AR", "Argentina", Percent(0.5), Set(Site.north, Site.south)),
        Partner("CA", "Canada", Percent(0.5), Set(Site.north, Site.south)),
        Partner("BR", "Brazil", Percent(0.5), Set(Site.north, Site.south)),
        Partner("KR", "Korea", Percent(0.5), Set(Site.north, Site.south)),
      ),
      Sequence(Nil, Nil)
    )

}

