// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package edu.gemini.tac.qengine.ctx

import edu.gemini.spModel.core.Site
import edu.gemini.spModel.core.Semester

/**
 * Put this object's content into scope to define orderings based upon
 * Site and Semester.
 */
object ContextOrderingImplicits {
  implicit val siteOrdering = new Ordering[Site] {
    def compare(s1: Site, s2: Site): Int = s1.compareTo(s2)
  }

  implicit val semesterOrdering = new Ordering[Semester] {
    def compare(s1: Semester, s2: Semester): Int = s1.compareTo(s2)
  }

  implicit val contextOrdering = new Ordering[Context] {
    def compare(c1: Context, c2: Context): Int = c1.compareTo(c2)
  }

  implicit val semesterWrapper = (semester: Semester) => new RichSemester(semester)
}
