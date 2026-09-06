package com.atakmap.android.plugintemplate.grid;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The no-fabricated-position rule, as it applies to team members.
 *
 * A SearchTeamMember always carries a latitude and a longitude, because they
 * are plain doubles with no absent value. Before ATAK reports a fix those
 * numbers are a placeholder that looks exactly like a coordinate, and three
 * paths used to read them without asking: the search line distance maths, the
 * Slow/Hold marker, and the map pan on selecting a member. This class holds the
 * rule those paths now share.
 *
 * The call sites themselves cannot be unit tested -- SearchLineManager,
 * SearchLineOverlay and SARTakMapController all touch ATAK types that fail JVM
 * bytecode verification off-device -- which is exactly why the rule was split
 * into a class that touches none, following CotPublishPoint.
 *
 * These tests require only JUnit -- no ATAK SDK, no Robolectric.
 *
 * Dependencies (build.gradle):
 *   testImplementation 'junit:junit:4.13.2'
 */
public class MemberPositionPolicyTest {

    private static final double SOME_LATITUDE = -27.4705;
    private static final double SOME_LONGITUDE = 153.0260;

    /** A member as the roster builds one before ATAK has reported anything. */
    private SearchTeamMember memberWithoutFix() {
        return new SearchTeamMember("UID-1", "Rescue 1",
                SearchTeamMember.TeamRole.SEARCHER, "Blue", 0xFF4AA3FF,
                SearchTeamMember.MembershipStatus.ACTIVE_MEMBER,
                SearchTeamMember.ConnectionStatus.STALE, 1,
                0.0, 0.0, 0.0,
                "No GPS Signal", "No GPS Signal", "No GPS Signal",
                "No GPS Signal", "No GPS Signal", "No GPS Signal");
    }

    private SearchTeamMember memberWithFix() {
        SearchTeamMember member = memberWithoutFix();
        member.updatePosition(SOME_LATITUDE, SOME_LONGITUDE, 90.0,
                "27.4705 S 153.0260 E", "cell-1", "120 m", "3 m ahead");
        return member;
    }

    // ---- hasUsablePosition ----------------------------------------------

    @Test
    public void hasUsablePosition_isFalseBeforeAtakReportsAFix() {
        assertFalse(MemberPositionPolicy.hasUsablePosition(
                memberWithoutFix()));
    }

    @Test
    public void hasUsablePosition_isTrueOnceAtakReportsAFix() {
        assertTrue(MemberPositionPolicy.hasUsablePosition(memberWithFix()));
    }

    @Test
    public void hasUsablePosition_isFalseForNull() {
        assertFalse(MemberPositionPolicy.hasUsablePosition(null));
    }

    /**
     * Losing a fix must revoke permission, not merely stop refreshing it. The
     * member keeps their last coordinates in the object, so a policy that only
     * checked "have we ever had a fix" would keep authorising a stale point.
     */
    @Test
    public void hasUsablePosition_isFalseAgainAfterTheFixIsLost() {
        SearchTeamMember member = memberWithFix();
        assertTrue(MemberPositionPolicy.hasUsablePosition(member));

        member.markLocationUnavailable("No GPS Signal");

        assertFalse("a lost fix must revoke permission to use the position",
                MemberPositionPolicy.hasUsablePosition(member));
    }

    // ---- contributesLane stays a membership question ---------------------

    /**
     * Pins the design decision behind this fix. Lane count divides the search
     * cell into lanes, so gating contributesLane() on GPS would re-shape the
     * line for every other searcher whenever one person's fix dropped. A
     * searcher with no signal is still assigned to walk their lane.
     */
    @Test
    public void contributesLane_staysTrueWhileTheFixIsMissing() {
        SearchTeamMember member = memberWithoutFix();

        assertFalse(MemberPositionPolicy.hasUsablePosition(member));
        assertTrue("lane membership must not depend on GPS",
                member.contributesLane());
        assertEquals("Lane 1", member.getLaneLabel());
    }

    @Test
    public void contributesLane_tracksMembershipNotPosition() {
        SearchTeamMember member = memberWithFix();
        assertTrue(member.contributesLane());

        member.setMembershipStatus(
                SearchTeamMember.MembershipStatus.EXCLUDED_FROM_LANES);

        assertFalse(member.contributesLane());
        assertTrue("excluding from lanes must not erase a real position",
                MemberPositionPolicy.hasUsablePosition(member));
    }

    // ---- lineDistanceLabel ----------------------------------------------

    /**
     * The specific trap this fix closes. Math.round turns NaN into 0, and the
     * old formatter reported anything within two metres as "On line" -- so a
     * searcher whose position was unknown was displayed as standing exactly
     * where they were supposed to be.
     */
    @Test
    public void lineDistanceLabel_saysUnknownRatherThanOnLine() {
        assertEquals(MemberPositionPolicy.UNKNOWN_POSITION_LABEL,
                MemberPositionPolicy.lineDistanceLabel(false, Double.NaN));
    }

    @Test
    public void lineDistanceLabel_saysUnknownEvenForAZeroDistance() {
        assertEquals("an unknown position must never read as On line",
                MemberPositionPolicy.UNKNOWN_POSITION_LABEL,
                MemberPositionPolicy.lineDistanceLabel(false, 0.0));
    }

    /** Defence in depth: NaN is never a measurement, whatever the flag says. */
    @Test
    public void lineDistanceLabel_saysUnknownForNaNEvenWhenFlaggedKnown() {
        assertEquals(MemberPositionPolicy.UNKNOWN_POSITION_LABEL,
                MemberPositionPolicy.lineDistanceLabel(true, Double.NaN));
    }

    @Test
    public void lineDistanceLabel_reportsOnLineWithinTwoMetres() {
        assertEquals("On line",
                MemberPositionPolicy.lineDistanceLabel(true, 1.4));
        assertEquals("On line",
                MemberPositionPolicy.lineDistanceLabel(true, -2.0));
    }

    @Test
    public void lineDistanceLabel_reportsAheadAndBehind() {
        assertEquals("12 m ahead",
                MemberPositionPolicy.lineDistanceLabel(true, 12.0));
        assertEquals("12 m behind",
                MemberPositionPolicy.lineDistanceLabel(true, -12.0));
    }

    // ---- mayDrawSlowDownMarker ------------------------------------------

    /**
     * The marker is drawn at the member's own coordinates, so a missing fix
     * would put a "Slow/Hold" label on the map at a place the searcher has
     * never been.
     */
    @Test
    public void mayDrawSlowDownMarker_isFalseWithoutAPosition() {
        assertFalse(MemberPositionPolicy.mayDrawSlowDownMarker(
                memberWithoutFix(), true));
    }

    @Test
    public void mayDrawSlowDownMarker_isFalseWhenNotTooFarAhead() {
        assertFalse(MemberPositionPolicy.mayDrawSlowDownMarker(
                memberWithFix(), false));
    }

    @Test
    public void mayDrawSlowDownMarker_needsBothAPositionAndAReason() {
        assertTrue(MemberPositionPolicy.mayDrawSlowDownMarker(
                memberWithFix(), true));
    }

    @Test
    public void mayDrawSlowDownMarker_isFalseForNull() {
        assertFalse(MemberPositionPolicy.mayDrawSlowDownMarker(null, true));
    }
}
