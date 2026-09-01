package com.atakmap.android.plugintemplate.grid;

import java.util.List;

import com.atakmap.coremap.maps.coords.GeoPoint;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end cover for the no-fabricated-position rule on the search line.
 *
 * <p>Where {@link MemberPositionPolicyTest} proves the rule, this proves the
 * call site actually applies it: a real SearchLineManager, a real
 * GridCoordinateConverter, a real cell, and members with and without a fix.
 *
 * <p>These tests construct GeoPoint and UTMPoint, which throw VerifyError
 * under the default verifier because the ATAK SDK jar predates stackmap
 * frames. The unit test task runs with {@code -noverify} (see
 * app/build.gradle), which is what makes this file possible -- the constraint
 * was a missing JVM flag, not a property of the SDK.
 *
 * <p>A member with no fix carries placeholder coordinates of 0, 0. In UTM zone
 * 55H that is thousands of kilometres from the search cell, so any code that
 * measured from it would produce enormous, confident-looking distances. That
 * is precisely what these tests assert never happens.
 */
public class SearchLineNoFabricationTest {

    private static final String ZONE = "55H";
    private static final double CELL_WEST = 300_300.0;
    private static final double CELL_SOUTH = 6_200_700.0;
    private static final double CELL_EAST = 300_400.0;
    private static final double CELL_NORTH = 6_200_800.0;
    private static final double LEADER_EASTING = 300_350.0;
    private static final double LEADER_NORTHING = 6_200_750.0;

    private GridCoordinateConverter converter;
    private SearchPartyAssignmentManager assignment;
    private SearchLineManager manager;
    private SearchGridCell cell;

    @Before
    public void setUp() {
        converter = new GridCoordinateConverter();
        assignment = new SearchPartyAssignmentManager();
        assignment.setTeamCreated(true);
        manager = new SearchLineManager(converter, assignment);
        cell = new SearchGridCell("agg-1", "cell-1", 7, 3, ZONE,
                CELL_WEST, CELL_SOUTH, CELL_EAST, CELL_NORTH,
                SearchGridStatus.IN_PROGRESS);
    }

    private void startLine() {
        manager.start(cell, converter.toGeoPoint(ZONE, LEADER_EASTING,
                LEADER_NORTHING));
    }

    /** A rostered member ATAK has never reported a position for. */
    private SearchTeamMember addMemberWithoutFix(String uid, String callsign) {
        return assignment.addConfirmedRosterMember(uid, callsign,
                SearchTeamMember.TeamRole.SEARCHER);
    }

    /** A rostered member ATAK has reported a real position for. */
    private SearchTeamMember addMemberWithFix(String uid, String callsign,
            double easting, double northing) {
        SearchTeamMember member = addMemberWithoutFix(uid, callsign);
        GeoPoint point = converter.toGeoPoint(ZONE, easting, northing);
        member.updatePosition(point.getLatitude(), point.getLongitude(), 0.0,
                "gps", "alt", "cell-1", "now", "10 m", "2 m");
        return member;
    }

    private SearchLineMemberStatus statusFor(String uid) {
        for (SearchLineMemberStatus status : manager.getMemberStatuses()) {
            if (status.getMember().getUniqueId().equals(uid))
                return status;
        }
        return null;
    }

    // ---- The member is kept, but not measured ---------------------------

    /**
     * Dropping an unlocatable member from the line would hide a searcher from
     * their leader, which is its own kind of dishonesty. They stay listed.
     */
    @Test
    public void memberWithoutFix_isStillListedOnTheLine() {
        addMemberWithoutFix("UID-A", "Rescue A");
        startLine();

        assertNotNull("an unlocatable member must not vanish from the line",
                statusFor("UID-A"));
    }

    @Test
    public void memberWithoutFix_reportsNoKnownPosition() {
        addMemberWithoutFix("UID-A", "Rescue A");
        startLine();

        assertFalse(statusFor("UID-A").hasKnownPosition());
    }

    @Test
    public void memberWithoutFix_hasNoDistanceMeasured() {
        addMemberWithoutFix("UID-A", "Rescue A");
        startLine();

        SearchLineMemberStatus status = statusFor("UID-A");
        assertTrue("distance from line must not be a number",
                Double.isNaN(status.getDistanceFromLineMeters()));
        assertTrue("distance from return mark must not be a number",
                Double.isNaN(status.getDistanceFromReturnMarkMeters()));
    }

    /**
     * The return mark is derived from the lane assignment, not from where the
     * member is, so it stays a real place even with no fix. Losing it would
     * remove information we genuinely have.
     */
    @Test
    public void memberWithoutFix_stillGetsARealReturnMark() {
        addMemberWithoutFix("UID-A", "Rescue A");
        startLine();

        GeoPoint mark = statusFor("UID-A").getReturnMark();
        assertNotNull("the return mark comes from the lane, not the member",
                mark);
        assertTrue(mark.getLatitude() != 0.0 || mark.getLongitude() != 0.0);
    }

    @Test
    public void memberWithFix_isMeasuredNormally() {
        addMemberWithFix("UID-B", "Rescue B", LEADER_EASTING,
                LEADER_NORTHING + 30.0);
        startLine();

        SearchLineMemberStatus status = statusFor("UID-B");
        assertTrue(status.hasKnownPosition());
        assertFalse(Double.isNaN(status.getDistanceFromLineMeters()));
        assertEquals("measured from the real position",
                30.0, status.getDistanceFromLineMeters(), 1.0);
    }

    // ---- No fabricated warnings -----------------------------------------

    /**
     * The placeholder 0, 0 is thousands of kilometres from the cell. Measured
     * naively it would clear the 8 m threshold by a wide margin and raise a
     * Slow/Hold warning about a searcher nobody can locate.
     */
    @Test
    public void memberWithoutFix_neverRaisesASlowDownWarning() {
        addMemberWithoutFix("UID-A", "Rescue A");
        startLine();

        assertFalse(statusFor("UID-A").isTooFarAhead(
                SearchLineManager.SLOW_DOWN_THRESHOLD_METERS));
        assertFalse("no HOLD/SLOW may name an unlocatable searcher",
                manager.getWarningSummary().contains("Rescue A"));
    }

    @Test
    public void memberWithoutFix_isNotReportedOffTheReturnMark() {
        addMemberWithoutFix("UID-A", "Rescue A");
        startLine();
        manager.pause();

        for (SearchLineMemberStatus status
                : manager.getMembersOffReturnMark()) {
            assertFalse("an unlocatable member cannot be off their mark",
                    "UID-A".equals(status.getMember().getUniqueId()));
        }
    }

    @Test
    public void memberWithFix_stillRaisesARealSlowDownWarning() {
        addMemberWithFix("UID-B", "Rescue B", LEADER_EASTING,
                LEADER_NORTHING + 40.0);
        startLine();

        assertTrue("a real position must still raise a real warning",
                manager.getWarningSummary().contains("Rescue B"));
    }

    // ---- The summaries say so out loud ----------------------------------

    /**
     * The trap this closes. Math.round turns NaN into 0, and the old formatter
     * reported anything within two metres as "On line", so an unlocatable
     * searcher was displayed as standing exactly where they should be.
     */
    @Test
    public void lineSummary_saysPositionUnknownRatherThanOnLine() {
        addMemberWithoutFix("UID-A", "Rescue A");
        startLine();

        String summary = manager.getMemberLineSummary();
        assertTrue("expected the unknown-position label, got: " + summary,
                summary.contains(
                        MemberPositionPolicy.UNKNOWN_POSITION_LABEL));
        assertFalse("an unlocatable member must never read as On line",
                summary.contains("On line"));
    }

    @Test
    public void memberCardSummary_saysPositionUnknown() {
        addMemberWithoutFix("UID-A", "Rescue A");
        startLine();

        String summary = manager.getMemberCardLineSummary("UID-A");
        assertTrue("expected the unknown-position label, got: " + summary,
                summary.contains(
                        MemberPositionPolicy.UNKNOWN_POSITION_LABEL));
        assertFalse(summary.contains("On line"));
    }

    /**
     * A paused line prints a return-mark distance. Math.round(NaN) is 0, so
     * without a guard an unmeasured member reads as standing exactly on their
     * mark.
     */
    @Test
    public void pausedLineSummary_reportsNoReturnMarkDistanceWithoutAFix() {
        addMemberWithoutFix("UID-A", "Rescue A");
        startLine();
        manager.pause();

        String summary = manager.getMemberLineSummary();
        assertFalse("expected no fabricated return-mark distance, got: "
                + summary, summary.contains("Return mark 0 m"));
    }

    // ---- Lane membership survives a lost fix ----------------------------

    /**
     * Pins the design decision behind this fix, end to end. Lane count divides
     * the cell into lanes, so if a lost fix removed a member from the line the
     * remaining searchers' lanes would silently move mid-search.
     */
    @Test
    public void losingAFix_doesNotChangeWhoIsOnTheLine() {
        addMemberWithFix("UID-A", "Rescue A", LEADER_EASTING - 20.0,
                LEADER_NORTHING);
        addMemberWithFix("UID-B", "Rescue B", LEADER_EASTING + 20.0,
                LEADER_NORTHING);
        startLine();

        int before = manager.getMemberStatuses().size();
        int laneBefore = statusFor("UID-B").getMember().getLaneNumber();

        assignment.findMemberById("UID-A")
                .markLocationUnavailable("No GPS Signal");

        List<SearchLineMemberStatus> after = manager.getMemberStatuses();
        assertEquals("the line roster must not change when a fix drops",
                before, after.size());
        assertEquals("another searcher's lane must not move",
                laneBefore, statusFor("UID-B").getMember().getLaneNumber());
        assertFalse("but the unlocatable member is no longer measured",
                statusFor("UID-A").hasKnownPosition());
        assertTrue("while the located one still is",
                statusFor("UID-B").hasKnownPosition());
    }
}
