package com.atakmap.android.plugintemplate.grid;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The second half of the no-fabricated-position rule for the search line.
 *
 * SearchLineManager builds one of these per rostered member. When ATAK has not
 * reported a fix the member is still included -- a leader needs to know they
 * exist and which lane they hold -- but the distances are NaN and every
 * question answered from them must refuse to answer rather than guess.
 *
 * The return mark is deliberately still populated for such a member: it is
 * derived from the lane assignment, not from where the member is, so it stays
 * a real place on the map.
 *
 * These tests pass null for the return mark rather than constructing a
 * GeoPoint, which cannot be loaded off-device.
 */
public class SearchLineMemberStatusTest {

    private static final double THRESHOLD = 8.0;

    private SearchTeamMember member() {
        return new SearchTeamMember("UID-1", "Rescue 1",
                SearchTeamMember.TeamRole.SEARCHER, "Blue", 0xFF4AA3FF,
                SearchTeamMember.MembershipStatus.ACTIVE_MEMBER,
                SearchTeamMember.ConnectionStatus.STALE, 1,
                0.0, 0.0, 0.0,
                "No GPS Signal", "No GPS Signal", "No GPS Signal",
                "No GPS Signal", "No GPS Signal", "No GPS Signal");
    }

    private SearchLineMemberStatus unmeasured() {
        return new SearchLineMemberStatus(member(), Double.NaN, Double.NaN,
                0.0, null, false);
    }

    private SearchLineMemberStatus measured(double fromLine,
            double fromReturnMark) {
        return new SearchLineMemberStatus(member(), fromLine, fromReturnMark,
                31.0, null, true);
    }

    /**
     * Flagged as having no position but carrying real-looking numbers.
     *
     * <p>This is the case the guards actually exist for. SearchLineManager
     * currently pairs "position unknown" with NaN, and NaN fails every
     * comparison on its own, so a test built only on NaN passes whether the
     * guard is present or not. A caller that ever pairs the flag with a stale
     * distance -- the obvious future mistake -- is caught only here.
     */
    private SearchLineMemberStatus unmeasuredButNumeric(double fromLine,
            double fromReturnMark) {
        return new SearchLineMemberStatus(member(), fromLine, fromReturnMark,
                31.0, null, false);
    }

    @Test
    public void unmeasuredMember_reportsNoKnownPosition() {
        assertFalse(unmeasured().hasKnownPosition());
    }

    /**
     * Without this guard a placeholder distance would be compared against the
     * threshold, raising a Slow/Hold warning about a searcher nobody can find.
     */
    @Test
    public void unmeasuredMember_isNeverTooFarAhead() {
        assertFalse(unmeasured().isTooFarAhead(THRESHOLD));
    }

    @Test
    public void unmeasuredMember_isNeverOffTheReturnMark() {
        assertFalse(unmeasured().isOffReturnMark(10.0));
    }

    /**
     * The flag decides, not the number. A status marked as having no position
     * must refuse to answer even when it is carrying a distance that would
     * otherwise clear the threshold.
     */
    @Test
    public void unmeasuredMember_ignoresARealLookingDistanceFromTheLine() {
        assertFalse("the position flag must win over the number",
                unmeasuredButNumeric(12.0, 0.0).isTooFarAhead(THRESHOLD));
    }

    @Test
    public void unmeasuredMember_ignoresARealLookingDistanceFromTheMark() {
        assertFalse("the position flag must win over the number",
                unmeasuredButNumeric(0.0, 25.0).isOffReturnMark(10.0));
    }

    @Test
    public void measuredMember_stillReportsBeingTooFarAhead() {
        assertTrue("a real position must still raise a real warning",
                measured(12.0, 0.0).isTooFarAhead(THRESHOLD));
        assertTrue(measured(0.0, 25.0).isOffReturnMark(10.0));
    }

    @Test
    public void measuredMember_withinThresholdRaisesNoWarning() {
        assertFalse(measured(3.0, 0.0).isTooFarAhead(THRESHOLD));
        assertFalse(measured(0.0, 4.0).isOffReturnMark(10.0));
    }

    @Test
    public void measuredMember_reportsAKnownPosition() {
        assertTrue(measured(1.0, 1.0).hasKnownPosition());
    }

    // ---- isOffLine, which the marker outline uses -----------------------

    @Test
    public void unmeasuredMember_isNeverOffTheLine() {
        assertFalse(unmeasured().isOffLine(THRESHOLD));
        assertFalse("the position flag must win over the number",
                unmeasuredButNumeric(12.0, 0.0).isOffLine(THRESHOLD));
        assertFalse("and in the behind direction too",
                unmeasuredButNumeric(-12.0, 0.0).isOffLine(THRESHOLD));
    }

    /** Unlike isTooFarAhead, this one is two-sided: behind counts as off. */
    @Test
    public void measuredMember_isOffTheLineInEitherDirection() {
        assertTrue(measured(12.0, 0.0).isOffLine(THRESHOLD));
        assertTrue(measured(-12.0, 0.0).isOffLine(THRESHOLD));
    }

    @Test
    public void measuredMember_onTheLineIsNotOffIt() {
        assertFalse(measured(3.0, 0.0).isOffLine(THRESHOLD));
        assertFalse(measured(-3.0, 0.0).isOffLine(THRESHOLD));
    }
}
