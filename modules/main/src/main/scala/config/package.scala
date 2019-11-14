package itac

import edu.gemini.tac.qengine.ctx.Site

package object config {

  type Semester = edu.gemini.tac.qengine.ctx.Semester
  type Site     = edu.gemini.tac.qengine.ctx.Site
  type Time     = edu.gemini.tac.qengine.util.Time

  // N.B. `Site` is a Java class so it has no companion. So alias these directly.
  final val GN = Site.north
  final val GS = Site.south

}
