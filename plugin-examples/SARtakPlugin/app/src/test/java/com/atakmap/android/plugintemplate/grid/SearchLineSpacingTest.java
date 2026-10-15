package com.atakmap.android.plugintemplate.grid;

import com.atakmap.coremap.maps.coords.GeoPoint;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The Home tab's Spacing box: how far apart searchers are along the line,
 * measured from their real positions. It used to be fixed text.
 */
public class SearchLineSpacingTest {

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

    @Test
    public void beforeTheLineStarts_saysWhenItWillBeMeasured() {
        addMemberAt("A", "ALPHA", 300_340, 6_200_750);
        addMemberAt("B", "BRAVO", 300_355, 6_200_750);

        assertEquals("Measured when search line is active",
                manager.getSpacingSummary());
    }

    @Test
    public void northLine_measuresAcrossTheLineInEasting() {
        addMemberAt("A", "ALPHA", 300_340, 6_200_750);
        addMemberAt("B", "BRAVO", 300_355, 6_200_760);
        startLine(SearchLineDirection.NORTH);

        // 15 m apart across the line; the 10 m north-south offset is how far
        // ahead one of them is, not spacing.
        assertEquals("15 m apart", manager.getSpacingSummary());
    }

    @Test
    public void eastLine_measuresAcrossTheLineInNorthing() {
        addMemberAt("A", "ALPHA", 300_340, 6_200_750);
        addMemberAt("B", "BRAVO", 300_355, 6_200_760);
        startLine(SearchLineDirection.EAST);

        assertEquals("10 m apart", manager.getSpacingSummary());
    }

    @Test
    public void searchersInSingleFile_areZeroApart() {
        addMemberAt("A", "ALPHA", 300_350, 6_200_740);
        addMemberAt("B", "BRAVO", 300_350, 6_200_770);
        startLine(SearchLineDirection.NORTH);

        assertEquals("0 m apart", manager.getSpacingSummary());
    }

    @Test
    public void threeSearchers_reportTheRangeAndAverageOfNeighbourGaps() {
        // Lanes are numbered by UID, not position: C stands between A and B.
        addMemberAt("A", "ALPHA", 300_310, 6_200_750);
        addMemberAt("B", "BRAVO", 300_340, 6_200_750);
        addMemberAt("C", "CHARLIE", 300_320, 6_200_750);
        startLine(SearchLineDirection.NORTH);

        assertEquals("10-20 m apart, avg 15 m", manager.getSpacingSummary());
    }

    @Test
    public void searcherWithoutAPosition_isLeftOutAndCounted() {
        addMemberAt("A", "ALPHA", 300_340, 6_200_750);
        addMemberAt("B", "BRAVO", 300_355, 6_200_750);
        assignment.addConfirmedRosterMember("C", "CHARLIE",
                SearchTeamMember.TeamRole.SEARCHER);
        startLine(SearchLineDirection.NORTH);

        assertEquals("15 m apart | 1 not located",
                manager.getSpacingSummary());
    }

    @Test
    public void oneLocatedSearcher_cannotBeMeasured() {
        addMemberAt("A", "ALPHA", 300_340, 6_200_750);
        assignment.addConfirmedRosterMember("B", "BRAVO",
                SearchTeamMember.TeamRole.SEARCHER);
        startLine(SearchLineDirection.NORTH);

        assertEquals("Needs 2 located searchers | 1 not located",
                manager.getSpacingSummary());
    }

    @Test
    public void afterTheLineEnds_goesBackToThePlaceholder() {
        addMemberAt("A", "ALPHA", 300_340, 6_200_750);
        addMemberAt("B", "BRAVO", 300_355, 6_200_750);
        startLine(SearchLineDirection.NORTH);

        manager.end();

        assertEquals("Measured when search line is active",
                manager.getSpacingSummary());
    }

    private void startLine(SearchLineDirection direction) {
        GeoPoint leader = converter.toGeoPoint(ZONE, LEADER_EASTING,
                LEADER_NORTHING);
        manager.setDirection(direction, leader);
        manager.start(cell, leader);
    }

    private void addMemberAt(String uid, String callsign, double easting,
            double northing) {
        SearchTeamMember member = assignment.addConfirmedRosterMember(uid,
                callsign, SearchTeamMember.TeamRole.SEARCHER);
        GeoPoint p = converter.toGeoPoint(ZONE, easting, northing);
        member.updatePosition(p.getLatitude(), p.getLongitude(), 0.0,
                "gps", "alt", "cell-1", "now", "10 m", "2 m");
    }
}
