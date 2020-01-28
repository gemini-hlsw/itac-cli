/*
 * Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
 * For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause
 */

//
// $
//

package edu.gemini.qengine.skycalc;


import edu.gemini.tac.qengine.ctx.Site;

/**
 * A single place for finding the edu.gemini.spModel.core.Site that corresponds to a Site.
 */
public final class SiteLookup {
    private SiteLookup() {}

    public static edu.gemini.spModel.core.Site get(Site site) {
        return (site == Site.north) ? edu.gemini.spModel.core.Site.GN : edu.gemini.spModel.core.Site.GS;
    }
}
