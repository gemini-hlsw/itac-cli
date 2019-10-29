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
