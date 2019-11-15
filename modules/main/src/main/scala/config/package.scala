// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package itac

import edu.gemini.tac.qengine.ctx.Site

package object config {

  type Semester = edu.gemini.tac.qengine.ctx.Semester
  type Site     = edu.gemini.tac.qengine.ctx.Site

  type Time     = edu.gemini.tac.qengine.util.Time
  val  Time     = edu.gemini.tac.qengine.util.Time

  type Percent  = edu.gemini.tac.qengine.util.Percent
  val  Percent  = edu.gemini.tac.qengine.util.Percent

  // N.B. `Site` is a Java class so it has no companion. So alias these directly.
  final val GN = Site.north
  final val GS = Site.south

}
