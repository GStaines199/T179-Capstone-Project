package com.atakmap.android.plugintemplate.grid;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * What a searcher is told, and what they are deliberately not told.
 *
 * <p>Two rules under test. Guidance is two-sided: a searcher falling behind
 * opens a gap in the swept ground just as surely as one running ahead, and the
 * line used to warn only about the latter. And nothing is reported that was
 * not measured: an absent pace or an untrustworthy bearing is withheld rather
 * than rounded to a plausible-looking zero.
 *
 * <p>Touches no ATAK types, so it runs on a plain JVM.
 */
public class SearchLineGuidanceTest {

    private static final double FAR = 20.0;

    // ---- bands ----------------------------------------------------------

    @Test
    public void withinTolerance_isOnLine() {
        assertEquals(SearchLineGuidance.Band.ON_LINE,
                SearchLineGuidance.bandFor(true, 1.4));
        assertEquals(SearchLineGuidance.Band.ON_LINE,
                SearchLineGuidance.bandFor(true, -2.0));
    }

    @Test
    public void tolerableDrift_isReportedButNotCorrected() {
        assertEquals(SearchLineGuidance.Band.AHEAD,
                SearchLineGuidance.bandFor(true, 5.0));
        assertEquals(SearchLineGuidance.Band.BEHIND,
                SearchLineGuidance.bandFor(true, -5.0));
        assertFalse(SearchLineGuidance.bandFor(true, 5.0).needsCorrection());
        assertFalse(SearchLineGuidance.bandFor(true, -5.0).needsCorrection());
    }

    @Test
    public void runningAhead_asksTheSearcherToHold() {
        assertEquals(SearchLineGuidance.Band.HOLD,
                SearchLineGuidance.bandFor(true, FAR));
        assertTrue(SearchLineGuidance.bandFor(true, FAR).needsCorrection());
    }

    /**
     * The half that did not exist. A searcher who drops back was previously
     * classified the same as one holding station, and told nothing.
     */
    @Test
    public void fallingBehind_asksTheSearcherToCatchUp() {
        assertEquals(SearchLineGuidance.Band.CATCH_UP,
                SearchLineGuidance.bandFor(true, -FAR));
        assertTrue(SearchLineGuidance.bandFor(true, -FAR).needsCorrection());
    }

    @Test
    public void bandsAreSymmetric_aboutTheLine() {
        for (double metres : new double[] { 0.0, 1.0, 5.0, 20.0, 400.0 }) {
            SearchLineGuidance.Band ahead =
                    SearchLineGuidance.bandFor(true, metres);
            SearchLineGuidance.Band behind =
                    SearchLineGuidance.bandFor(true, -metres);
            assertEquals("drift of " + metres + " m must be treated the same "
                    + "in both directions", ahead.needsCorrection(),
                    behind.needsCorrection());
        }
    }

    // ---- no position, no band -------------------------------------------

    @Test
    public void noPosition_yieldsNoBand() {
        assertEquals(SearchLineGuidance.Band.UNKNOWN,
                SearchLineGuidance.bandFor(false, Double.NaN));
    }

    /**
     * Math.abs(NaN) compares false against every threshold, so a band chosen
     * from the number alone would fall through to ON_LINE -- reporting an
     * unlocatable searcher as standing exactly where they should be. The
     * position flag has to be consulted first.
     */
    @Test
    public void aNaNDistance_neverReadsAsOnLine() {
        assertEquals(SearchLineGuidance.Band.UNKNOWN,
                SearchLineGuidance.bandFor(true, Double.NaN));
        assertEquals(MemberPositionPolicy.UNKNOWN_POSITION_LABEL,
                SearchLineGuidance.guidanceLabel(true, Double.NaN));
    }

    /**
     * The flag wins even when the number looks entirely plausible -- the
     * obvious future mistake being a caller that pairs "position unknown" with
     * a stale distance rather than NaN.
     */
    @Test
    public void aStaleDistanceFlaggedUnknown_isStillRefused() {
        assertEquals(SearchLineGuidance.Band.UNKNOWN,
                SearchLineGuidance.bandFor(false, 4.0));
        assertEquals(MemberPositionPolicy.UNKNOWN_POSITION_LABEL,
                SearchLineGuidance.guidanceLabel(false, 4.0));
    }

    @Test
    public void anUnknownBand_neverAsksForACorrection() {
        assertFalse(SearchLineGuidance.Band.UNKNOWN.needsCorrection());
    }

    // ---- guidance wording -----------------------------------------------

    @Test
    public void guidance_namesTheSideAndTheDistance() {
        assertEquals("5 m ahead", SearchLineGuidance.guidanceLabel(true, 5.0));
        assertEquals("5 m behind",
                SearchLineGuidance.guidanceLabel(true, -5.0));
    }

    @Test
    public void guidance_addsTheInstructionOnlyWhenItIsNeeded() {
        assertEquals("20 m ahead - slow down",
                SearchLineGuidance.guidanceLabel(true, 20.0));
        assertEquals("20 m behind - catch up",
                SearchLineGuidance.guidanceLabel(true, -20.0));
        assertEquals("On line", SearchLineGuidance.guidanceLabel(true, 0.4));
    }

    /**
     * Distances read as unsigned metres plus a word. A signed count reads as a
     * coordinate, and in the field the word is what gets acted on.
     */
    @Test
    public void guidance_neverPrintsAMinusSign() {
        assertFalse(SearchLineGuidance.guidanceLabel(true, -12.0)
                .contains("-1"));
    }

    // ---- pace -----------------------------------------------------------

    /**
     * The distinction the old formatter could not make. Both cases used to
     * arrive as zero and print "unavailable", so a leader was told their
     * searcher's radio had failed when the searcher had simply halted.
     */
    @Test
    public void aStoppedSearcher_readsAsStationaryNotUnavailable() {
        assertEquals(SearchLineGuidance.STATIONARY_LABEL,
                SearchLineGuidance.paceLabel(0.0));
    }

    @Test
    public void anUnreportedPace_readsAsUnavailableNotStationary() {
        assertEquals(SearchLineGuidance.UNAVAILABLE_LABEL,
                SearchLineGuidance.paceLabel(Double.NaN));
    }

    @Test
    public void stoppedAndUnreported_areNeverTheSameLabel() {
        assertFalse(SearchLineGuidance.paceLabel(0.0)
                .equals(SearchLineGuidance.paceLabel(Double.NaN)));
    }

    @Test
    public void aRealPace_isPrinted() {
        assertEquals("31.0 m/min", SearchLineGuidance.paceLabel(31.0));
    }

    /**
     * GPS noise moves a standing searcher's fix around, so ATAK reports small
     * non-zero speeds for them. Printing "0.3 m/min" implies a precision the
     * reading does not have.
     */
    @Test
    public void gpsNoiseOnAStandingSearcher_readsAsStationary() {
        assertEquals(SearchLineGuidance.STATIONARY_LABEL,
                SearchLineGuidance.paceLabel(0.3));
    }

    @Test
    public void anImpossiblePace_isRefusedRatherThanPrinted() {
        assertEquals(SearchLineGuidance.UNAVAILABLE_LABEL,
                SearchLineGuidance.paceLabel(-4.0));
        assertEquals(SearchLineGuidance.UNAVAILABLE_LABEL,
                SearchLineGuidance.paceLabel(Double.POSITIVE_INFINITY));
    }

    // ---- bearing --------------------------------------------------------

    /**
     * ATAK derives heading from successive fixes, so a stationary device
     * produces a heading that is arithmetically valid and physically
     * meaningless. It is withheld rather than shown.
     */
    @Test
    public void anUnreliableBearing_isWithheld() {
        assertEquals(SearchLineGuidance.UNAVAILABLE_LABEL,
                SearchLineGuidance.bearingLabel(false, 47.0));
    }

    @Test
    public void anUnreliableBearing_isNotRoundedToDueNorth() {
        assertFalse(SearchLineGuidance.bearingLabel(false, 0.0)
                .contains("000"));
    }

    @Test
    public void aReliableBearing_isPrintedAsATrueBearing() {
        assertEquals("047 deg T",
                SearchLineGuidance.bearingLabel(true, 47.0));
    }

    @Test
    public void aBearingOutsideTheCircle_isNormalised() {
        assertEquals("010 deg T",
                SearchLineGuidance.bearingLabel(true, 370.0));
        assertEquals("350 deg T",
                SearchLineGuidance.bearingLabel(true, -10.0));
    }

    @Test
    public void aNaNBearing_isWithheldEvenWhenFlaggedReliable() {
        assertEquals(SearchLineGuidance.UNAVAILABLE_LABEL,
                SearchLineGuidance.bearingLabel(true, Double.NaN));
    }

    // ---- the thresholds agree with the manager --------------------------

    /**
     * The warning list and the per-searcher label read the same threshold, so
     * they can never disagree about who is out of position -- one saying
     * "catch up" while the other omits the searcher entirely.
     */
    @Test
    public void correctionThreshold_matchesTheManagersWarningThreshold() {
        assertEquals(SearchLineManager.SLOW_DOWN_THRESHOLD_METERS,
                SearchLineGuidance.CORRECTION_THRESHOLD_METERS, 0.0);
    }
}
