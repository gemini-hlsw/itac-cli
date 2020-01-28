// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package edu.gemini.tac.qengine.p2.rollover

import edu.gemini.tac.qengine.p2.ObservationId
import edu.gemini.tac.qengine.util.Time
import edu.gemini.tac.qengine.p1.{CategorizedTime, ObsConditions, Target}
import edu.gemini.tac.qengine.ctx.Partner
import edu.gemini.spModel.core.Site

/**
 * A class that represents a rollover time observation.  The time for each
 * rollover observation should be subtracted from the corresponding bins.
 */
case class RolloverObservation(
  partner: Partner,
  obsId: ObservationId,
  target: Target,
  conditions: ObsConditions,
  time: Time
) extends CategorizedTime {

  def site: Site = obsId.site
}
