/*
 * Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
 * For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause
 */

//
// $
//

package edu.gemini.qengine.skycalc;

import edu.gemini.tac.qengine.ctx.Semester;
import edu.gemini.tac.qengine.ctx.Site;

import java.util.Date;
import java.util.List;

/**
 * Describes the contract for calculating the RaBin hour values.  There are
 * different methods for computing this value.
 */
public interface RaBinCalc {
    List<Hours> calc(Site site, Date start, Date end, RaBinSize size);
}
