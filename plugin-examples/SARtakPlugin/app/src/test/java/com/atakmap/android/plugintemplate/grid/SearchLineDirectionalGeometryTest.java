package com.atakmap.android.plugintemplate.grid;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.UTMPoint;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The directional search line end to end: a real SearchLineManager, a real
 * GridCoordinateConverter, a real cell, and searchers placed around it.
 *
 * <p>Where {@link SearchLineDirectionTest} proves the axis arithmetic, this
 * proves the manager applies it -- that the line is drawn across the direction
 * of travel, the lanes divide across it too, the arrow points along it, and
 * "ahead" means ahead for all four directions rather than only for north.
 *
 * <p>Constructs GeoPoint and UTMPoint, which throw VerifyError under the
 * default verifier because the ATAK SDK jar predates stackmap frames. The unit
 * test task runs with {@code -noverify} (see app/build.gradle).
 */
public class SearchLineDirectionalGeometryTest {

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
        assignment = new SearchPartyAssignmentManager(null);
        assignment.setTeamCreated(true);
        manager = new SearchLineManager(converter, assignment);
        cell = new SearchGridCell("agg-1", "cell-1", 7, 3, ZONE,
                CELL_WEST, CELL_SOUTH, CELL_EAST, CELL_NORTH,
                SearchGridStatus.IN_PROGRESS);
    }

    private GeoPoint point(double easting, double northing) {
        return converter.toGeoPoint(ZONE, easting, northing);
    }

    private GeoPoint leaderPoint() {
        return point(LEADER_EASTING, LEADER_NORTHING);
    }

    private void startLine(SearchLineDirection direction) {
        manager.setDirection(direction, leaderPoint());
        manager.start(cell, leaderPoint());
    }

    private SearchTeamMember addMemberAt(String uid, String callsign,
            double easting, double northing) {
        SearchTeamMember member = assignment.addConfirmedRosterMember(uid,
                callsign, SearchTeamMember.TeamRole.SEARCHER);
        GeoPoint p = point(easting, northing);
        member.updatePosition(p.getLatitude(), p.getLongitude(), 0.0,
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

    private UTMPoint utm(GeoPoint p) {
        return UTMPoint.fromGeoPoint(p);
    }

    // ---- the line is drawn across the direction of travel ---------------

    @Test
    public void northLine_spansTheCellWestToEast() {
        startLine(SearchLineDirection.NORTH);

        UTMPoint start = utm(manager.getLineStart());
        UTMPoint end = utm(manager.getLineEnd());
        assertEquals("a north line runs along easting",
                CELL_WEST, start.getEasting(), 1.0);
        assertEquals(CELL_EAST, end.getEasting(), 1.0);
        assertEquals("both ends sit at the line's northing",
                start.getNorthing(), end.getNorthing(), 1.0);
    }

    /**
     * The case the original code could not express. An east-bound line runs
     * south to north; drawn west to east it would lie along the direction the
     * team is walking instead of across it.
     */
    @Test
    public void eastLine_spansTheCellSouthToNorth() {
        startLine(SearchLineDirection.EAST);

        UTMPoint start = utm(manager.getLineStart());
        UTMPoint end = utm(manager.getLineEnd());
        assertEquals("an east line runs along northing",
                CELL_SOUTH, start.getNorthing(), 1.0);
        assertEquals(CELL_NORTH, end.getNorthing(), 1.0);
        assertEquals("both ends sit at the line's easting",
                start.getEasting(), end.getEasting(), 1.0);
    }

    @Test
    public void westLine_alsoRunsSouthToNorth() {
        startLine(SearchLineDirection.WEST);

        UTMPoint start = utm(manager.getLineStart());
        UTMPoint end = utm(manager.getLineEnd());
        assertEquals(CELL_SOUTH, start.getNorthing(), 1.0);
        assertEquals(CELL_NORTH, end.getNorthing(), 1.0);
        assertEquals(start.getEasting(), end.getEasting(), 1.0);
    }

    @Test
    public void theLine_isAnchoredToWhereTheLeaderStands() {
        startLine(SearchLineDirection.NORTH);
        assertEquals(LEADER_NORTHING, manager.getLineOffset(), 1.0);

        startLine(SearchLineDirection.EAST);
        assertEquals("an east line is anchored to the leader's easting",
                LEADER_EASTING, manager.getLineOffset(), 1.0);
    }

    // ---- the arrow points along the direction of travel -----------------

    @Test
    public void theArrow_pointsTheWayTheTeamIsWalking() {
        startLine(SearchLineDirection.NORTH);
        assertTrue("north arrow must advance in northing",
                utm(manager.getDirectionEnd()).getNorthing()
                        > utm(manager.getDirectionStart()).getNorthing());

        startLine(SearchLineDirection.SOUTH);
        assertTrue("south arrow must retreat in northing",
                utm(manager.getDirectionEnd()).getNorthing()
                        < utm(manager.getDirectionStart()).getNorthing());

        startLine(SearchLineDirection.EAST);
        assertTrue("east arrow must advance in easting",
                utm(manager.getDirectionEnd()).getEasting()
                        > utm(manager.getDirectionStart()).getEasting());

        startLine(SearchLineDirection.WEST);
        assertTrue("west arrow must retreat in easting",
                utm(manager.getDirectionEnd()).getEasting()
                        < utm(manager.getDirectionStart()).getEasting());
    }

    /**
     * An arrow drawn past the cell boundary would point at ground this team is
     * not searching, so it shortens instead.
     */
    @Test
    public void theArrow_staysInsideTheCellAtTheFarEdge() {
        manager.setDirection(SearchLineDirection.NORTH, leaderPoint());
        manager.start(cell, point(LEADER_EASTING, CELL_NORTH));

        double tip = utm(manager.getDirectionEnd()).getNorthing();
        assertTrue("arrow tip left the cell: " + tip, tip <= CELL_NORTH + 1.0);
    }

    @Test
    public void theArrow_isRootedOnTheLine() {
        startLine(SearchLineDirection.EAST);
        assertEquals("the arrow starts at the line's easting",
                manager.getLineOffset(),
                utm(manager.getDirectionStart()).getEasting(), 1.0);
    }

    // ---- ahead and behind follow the direction --------------------------

    @Test
    public void north_treatsANorthwardSearcherAsAhead() {
        addMemberAt("UID-A", "Rescue A", LEADER_EASTING,
                LEADER_NORTHING + 30.0);
        startLine(SearchLineDirection.NORTH);

        assertEquals(30.0, statusFor("UID-A").getDistanceFromLineMeters(), 1.5);
        assertEquals(SearchLineGuidance.Band.HOLD,
                statusFor("UID-A").getGuidanceBand());
    }

    /**
     * The same searcher on the same ground, with the team walking the other
     * way. Measuring by northing alone would tell them to slow down when they
     * are in fact thirty metres behind the line.
     */
    @Test
    public void south_treatsTheSameSearcherAsBehind() {
        addMemberAt("UID-A", "Rescue A", LEADER_EASTING,
                LEADER_NORTHING + 30.0);
        startLine(SearchLineDirection.SOUTH);

        assertEquals(-30.0, statusFor("UID-A").getDistanceFromLineMeters(),
                1.5);
        assertEquals(SearchLineGuidance.Band.CATCH_UP,
                statusFor("UID-A").getGuidanceBand());
    }

    @Test
    public void east_treatsAnEastwardSearcherAsAhead() {
        addMemberAt("UID-A", "Rescue A", LEADER_EASTING + 30.0,
                LEADER_NORTHING);
        startLine(SearchLineDirection.EAST);

        assertEquals(30.0, statusFor("UID-A").getDistanceFromLineMeters(), 1.5);
    }

    @Test
    public void west_treatsTheSameSearcherAsBehind() {
        addMemberAt("UID-A", "Rescue A", LEADER_EASTING + 30.0,
                LEADER_NORTHING);
        startLine(SearchLineDirection.WEST);

        assertEquals(-30.0, statusFor("UID-A").getDistanceFromLineMeters(),
                1.5);
    }

    /**
     * Walking across an east-bound line is not progress along it. Before the
     * axes were separated, this searcher's northward offset would have been
     * read as thirty metres of advance.
     */
    @Test
    public void movingAcrossAnEastLine_isNotCountedAsProgress() {
        addMemberAt("UID-A", "Rescue A", LEADER_EASTING,
                LEADER_NORTHING + 30.0);
        startLine(SearchLineDirection.EAST);

        assertEquals("sideways movement is not advance",
                0.0, statusFor("UID-A").getDistanceFromLineMeters(), 1.5);
        assertEquals(SearchLineGuidance.Band.ON_LINE,
                statusFor("UID-A").getGuidanceBand());
    }

    // ---- warnings name both failures ------------------------------------

    @Test
    public void aSearcherFarBehind_isToldToCatchUp() {
        addMemberAt("UID-A", "Rescue A", LEADER_EASTING,
                LEADER_NORTHING - 40.0);
        startLine(SearchLineDirection.NORTH);

        String warnings = manager.getWarningSummary();
        assertTrue("expected a catch-up warning, got: " + warnings,
                warnings.contains("CATCH UP"));
        assertTrue(warnings.contains("Rescue A"));
        assertFalse("a lagging searcher must not be told to slow down",
                warnings.contains("HOLD/SLOW"));
    }

    @Test
    public void aSearcherFarAhead_isStillToldToHold() {
        addMemberAt("UID-A", "Rescue A", LEADER_EASTING,
                LEADER_NORTHING + 40.0);
        startLine(SearchLineDirection.NORTH);

        String warnings = manager.getWarningSummary();
        assertTrue(warnings.contains("HOLD/SLOW"));
        assertFalse(warnings.contains("CATCH UP"));
    }

    /**
     * Warning distances are printed unsigned. A raw signed value would read
     * "-40 m behind the line", which is a double negative at the moment a
     * leader is trying to act on it.
     */
    @Test
    public void warningDistances_arePrintedUnsigned() {
        addMemberAt("UID-A", "Rescue A", LEADER_EASTING,
                LEADER_NORTHING - 40.0);
        startLine(SearchLineDirection.NORTH);

        assertFalse(manager.getWarningSummary().contains("-"));
    }

    // ---- lanes divide across the line, not along it ---------------------

    /**
     * Lanes are spread across the direction of travel. Dividing along easting
     * regardless of direction -- as the code did when the line was north-only
     * -- would put an east-bound team in single file down one strip of ground
     * instead of abreast across it.
     */
    @Test
    public void eastLine_spreadsLanesAlongNorthing() {
        addMemberAt("UID-A", "Rescue A", LEADER_EASTING,
                LEADER_NORTHING - 20.0);
        addMemberAt("UID-B", "Rescue B", LEADER_EASTING,
                LEADER_NORTHING + 20.0);
        assignment.findMemberById("UID-A").setLaneNumber(1);
        assignment.findMemberById("UID-B").setLaneNumber(2);
        startLine(SearchLineDirection.EAST);

        UTMPoint markA = utm(statusFor("UID-A").getReturnMark());
        UTMPoint markB = utm(statusFor("UID-B").getReturnMark());
        assertTrue("east-bound lanes must separate along northing",
                Math.abs(markA.getNorthing() - markB.getNorthing()) > 10.0);
        assertEquals("east-bound lanes must share the line's easting",
                markA.getEasting(), markB.getEasting(), 1.0);
    }

    @Test
    public void northLine_stillSpreadsLanesAlongEasting() {
        addMemberAt("UID-A", "Rescue A", LEADER_EASTING - 20.0,
                LEADER_NORTHING);
        addMemberAt("UID-B", "Rescue B", LEADER_EASTING + 20.0,
                LEADER_NORTHING);
        assignment.findMemberById("UID-A").setLaneNumber(1);
        assignment.findMemberById("UID-B").setLaneNumber(2);
        startLine(SearchLineDirection.NORTH);

        UTMPoint markA = utm(statusFor("UID-A").getReturnMark());
        UTMPoint markB = utm(statusFor("UID-B").getReturnMark());
        assertTrue("north-bound lanes must separate along easting",
                Math.abs(markA.getEasting() - markB.getEasting()) > 10.0);
        assertEquals(markA.getNorthing(), markB.getNorthing(), 1.0);
    }

    @Test
    public void returnMarks_sitOnTheLine() {
        addMemberAt("UID-A", "Rescue A", LEADER_EASTING,
                LEADER_NORTHING + 30.0);
        startLine(SearchLineDirection.EAST);

        GeoPoint mark = statusFor("UID-A").getReturnMark();
        assertNotNull(mark);
        assertEquals("a return mark is on the line, not where the searcher is",
                manager.getLineOffset(), utm(mark).getEasting(), 1.0);
    }

    // ---- turning the line -----------------------------------------------

    /**
     * A northing reinterpreted as an easting lands the line hundreds of
     * kilometres outside the cell, so a turn re-anchors to the leader rather
     * than carrying the old offset over.
     */
    @Test
    public void turningTheLine_reAnchorsItToTheLeader() {
        startLine(SearchLineDirection.NORTH);
        assertEquals(LEADER_NORTHING, manager.getLineOffset(), 1.0);

        manager.setDirection(SearchLineDirection.EAST, leaderPoint());

        assertEquals("the offset must be re-read as an easting",
                LEADER_EASTING, manager.getLineOffset(), 1.0);
        assertTrue("the turned line must stay inside the cell",
                manager.getLineOffset() >= CELL_WEST
                        && manager.getLineOffset() <= CELL_EAST);
    }

    @Test
    public void turningTheLine_doesNotDisturbTheRoster() {
        addMemberAt("UID-A", "Rescue A", LEADER_EASTING, LEADER_NORTHING);
        startLine(SearchLineDirection.NORTH);
        int before = manager.getMemberStatuses().size();

        manager.setDirection(SearchLineDirection.WEST, leaderPoint());

        assertEquals("turning must not drop anybody from the line",
                before, manager.getMemberStatuses().size());
    }

    @Test
    public void cycleDirection_walksThroughAllFour() {
        startLine(SearchLineDirection.NORTH);
        assertEquals(SearchLineDirection.EAST,
                manager.cycleDirection(leaderPoint()));
        assertEquals(SearchLineDirection.SOUTH,
                manager.cycleDirection(leaderPoint()));
        assertEquals(SearchLineDirection.WEST,
                manager.cycleDirection(leaderPoint()));
        assertEquals(SearchLineDirection.NORTH,
                manager.cycleDirection(leaderPoint()));
    }

    // ---- the direction is visible ---------------------------------------

    @Test
    public void theSummary_namesTheDirectionOfTravel() {
        startLine(SearchLineDirection.SOUTH);

        String summary = manager.getSummary();
        assertTrue("expected the heading in the summary, got: " + summary,
                summary.contains("South"));
        assertTrue("expected the bearing too, got: " + summary,
                summary.contains("180"));
    }

    @Test
    public void aNewLine_defaultsToNorth() {
        assertEquals(SearchLineDirection.NORTH, manager.getDirection());
    }

    // ---- pace and bearing are withheld when unmeasured ------------------

    /**
     * A member with no live ATAK fix has no pace either. Reporting zero there
     * made a searcher whose radio had gone quiet indistinguishable from one
     * standing still.
     */
    @Test
    public void aMemberWithoutAFix_hasNoPaceOrBearing() {
        assignment.addConfirmedRosterMember("UID-A", "Rescue A",
                SearchTeamMember.TeamRole.SEARCHER);
        startLine(SearchLineDirection.NORTH);

        SearchLineMemberStatus status = statusFor("UID-A");
        assertTrue("pace must not be a number without a fix",
                Double.isNaN(status.getPaceMetersPerMinute()));
        assertEquals(SearchLineGuidance.UNAVAILABLE_LABEL,
                status.getPaceLabel());
        assertFalse(status.hasReliableBearing());
        assertEquals(SearchLineGuidance.UNAVAILABLE_LABEL,
                status.getBearingLabel());
    }

    @Test
    public void aStoppedSearcher_isNotReportedAsUnavailable() {
        SearchTeamMember member = addMemberAt("UID-A", "Rescue A",
                LEADER_EASTING, LEADER_NORTHING);
        member.setConnectionStatus(
                SearchTeamMember.ConnectionStatus.CONNECTED);
        member.updateMovement(0.0, false, 0.0);
        startLine(SearchLineDirection.NORTH);

        assertEquals("a reported halt is a measurement, not a gap",
                SearchLineGuidance.STATIONARY_LABEL,
                statusFor("UID-A").getPaceLabel());
    }

    @Test
    public void aMovingSearcher_reportsPaceAndBearing() {
        SearchTeamMember member = addMemberAt("UID-A", "Rescue A",
                LEADER_EASTING, LEADER_NORTHING);
        member.setConnectionStatus(
                SearchTeamMember.ConnectionStatus.CONNECTED);
        member.updateMovement(47.0, true, 1.0);
        startLine(SearchLineDirection.NORTH);

        SearchLineMemberStatus status = statusFor("UID-A");
        assertEquals("60.0 m/min", status.getPaceLabel());
        assertTrue(status.hasReliableBearing());
        assertEquals("047 deg T", status.getBearingLabel());
    }

    /**
     * The member summary is where the "slow down" suffix used to be appended
     * independently of the distance label, which let it say "Position unknown"
     * and "Slow down" about the same searcher in the same line.
     */
    @Test
    public void theMemberSummary_neverInstructsAnUnlocatableSearcher() {
        assignment.addConfirmedRosterMember("UID-A", "Rescue A",
                SearchTeamMember.TeamRole.SEARCHER);
        startLine(SearchLineDirection.NORTH);

        String summary = manager.getMemberLineSummary();
        assertTrue(summary.contains(
                MemberPositionPolicy.UNKNOWN_POSITION_LABEL));
        assertFalse(summary.contains("slow down"));
        assertFalse(summary.contains("catch up"));
    }
}
