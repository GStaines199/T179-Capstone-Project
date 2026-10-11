package com.atakmap.android.plugintemplate.grid;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The axis arithmetic behind a directional search line.
 *
 * <p>Every one of these questions used to have a single hard-coded answer:
 * progress was northing, lanes were easting, and forward was north. The tests
 * that matter here are the ones for south and west, where the sign flips --
 * get that wrong and the plugin confidently tells a searcher who has overrun
 * the line to speed up.
 *
 * <p>Touches no ATAK types, so it runs on a plain JVM.
 */
public class SearchLineDirectionTest {

    private static final double EASTING = 300_350.0;
    private static final double NORTHING = 6_200_750.0;

    private SearchGridCell cell() {
        return new SearchGridCell("agg-1", "cell-1", 7, 3, "55H",
                300_300.0, 6_200_700.0, 300_400.0, 6_200_800.0,
                SearchGridStatus.IN_PROGRESS);
    }

    // ---- which coordinate is which --------------------------------------

    @Test
    public void northAndSouth_travelAlongNorthing() {
        assertTrue(SearchLineDirection.NORTH.isNorthSouth());
        assertTrue(SearchLineDirection.SOUTH.isNorthSouth());
        assertEquals(NORTHING, SearchLineDirection.NORTH.travelCoordinate(
                EASTING, NORTHING), 0.0001);
        assertEquals(EASTING, SearchLineDirection.NORTH.lineCoordinate(
                EASTING, NORTHING), 0.0001);
    }

    @Test
    public void eastAndWest_travelAlongEasting() {
        assertFalse(SearchLineDirection.EAST.isNorthSouth());
        assertFalse(SearchLineDirection.WEST.isNorthSouth());
        assertEquals(EASTING, SearchLineDirection.EAST.travelCoordinate(
                EASTING, NORTHING), 0.0001);
        assertEquals(NORTHING, SearchLineDirection.EAST.lineCoordinate(
                EASTING, NORTHING), 0.0001);
    }

    /**
     * The two axes must never name the same coordinate. If they ever did, the
     * line would be drawn along the direction the team is walking rather than
     * across it, and every lane would sit on top of every other.
     */
    @Test
    public void travelAndLineAxes_areAlwaysPerpendicular() {
        for (SearchLineDirection direction : SearchLineDirection.values()) {
            double travel = direction.travelCoordinate(EASTING, NORTHING);
            double line = direction.lineCoordinate(EASTING, NORTHING);
            assertTrue(direction + " must use different axes",
                    travel != line);
        }
    }

    // ---- progress, and the sign flip ------------------------------------

    @Test
    public void north_countsNorthwardMovementAsAhead() {
        assertEquals(30.0, SearchLineDirection.NORTH.progressMeters(
                EASTING, NORTHING + 30.0, NORTHING), 0.0001);
    }

    /**
     * The core of the whole change. At a fixed northing a searcher to the
     * north is ahead of a north-bound line and behind a south-bound one. The
     * original code subtracted coordinates directly, which made the two
     * indistinguishable.
     */
    @Test
    public void south_countsNorthwardMovementAsBehind() {
        assertEquals(-30.0, SearchLineDirection.SOUTH.progressMeters(
                EASTING, NORTHING + 30.0, NORTHING), 0.0001);
    }

    @Test
    public void east_countsEastwardMovementAsAhead() {
        assertEquals(30.0, SearchLineDirection.EAST.progressMeters(
                EASTING + 30.0, NORTHING, EASTING), 0.0001);
    }

    @Test
    public void west_countsEastwardMovementAsBehind() {
        assertEquals(-30.0, SearchLineDirection.WEST.progressMeters(
                EASTING + 30.0, NORTHING, EASTING), 0.0001);
    }

    /**
     * Movement across the line is not progress along it. A searcher who steps
     * sideways into the next lane has not advanced, and must not be told they
     * have.
     */
    @Test
    public void movementAcrossTheLine_isNotProgress() {
        assertEquals(0.0, SearchLineDirection.NORTH.progressMeters(
                EASTING + 45.0, NORTHING, NORTHING), 0.0001);
        assertEquals(0.0, SearchLineDirection.EAST.progressMeters(
                EASTING, NORTHING + 45.0, EASTING), 0.0001);
    }

    @Test
    public void opposingDirections_reportOppositeProgress() {
        double north = SearchLineDirection.NORTH.progressMeters(
                EASTING, NORTHING + 17.0, NORTHING);
        double south = SearchLineDirection.SOUTH.progressMeters(
                EASTING, NORTHING + 17.0, NORTHING);
        assertEquals(north, -south, 0.0001);
    }

    // ---- advancing the line ---------------------------------------------

    @Test
    public void advanceOffset_movesForwardAlongTravelAxis() {
        assertEquals(NORTHING + 14.0, SearchLineDirection.NORTH
                .advanceOffset(NORTHING, 14.0), 0.0001);
        assertEquals(NORTHING - 14.0, SearchLineDirection.SOUTH
                .advanceOffset(NORTHING, 14.0), 0.0001);
        assertEquals(EASTING + 14.0, SearchLineDirection.EAST
                .advanceOffset(EASTING, 14.0), 0.0001);
        assertEquals(EASTING - 14.0, SearchLineDirection.WEST
                .advanceOffset(EASTING, 14.0), 0.0001);
    }

    // ---- cell bounds ----------------------------------------------------

    @Test
    public void northSouth_boundsTravelBySouthAndNorthEdges() {
        SearchGridCell cell = cell();
        assertEquals(cell.getSouth(),
                SearchLineDirection.NORTH.travelMin(cell), 0.0001);
        assertEquals(cell.getNorth(),
                SearchLineDirection.NORTH.travelMax(cell), 0.0001);
        assertEquals(cell.getWest(),
                SearchLineDirection.NORTH.lineMin(cell), 0.0001);
        assertEquals(cell.getEast(),
                SearchLineDirection.NORTH.lineMax(cell), 0.0001);
    }

    @Test
    public void eastWest_boundsTravelByWestAndEastEdges() {
        SearchGridCell cell = cell();
        assertEquals(cell.getWest(),
                SearchLineDirection.EAST.travelMin(cell), 0.0001);
        assertEquals(cell.getEast(),
                SearchLineDirection.EAST.travelMax(cell), 0.0001);
        assertEquals(cell.getSouth(),
                SearchLineDirection.EAST.lineMin(cell), 0.0001);
        assertEquals(cell.getNorth(),
                SearchLineDirection.EAST.lineMax(cell), 0.0001);
    }

    // ---- round-tripping line-relative coordinates -----------------------

    /**
     * Whatever a direction takes apart, it must put back together. This is the
     * invariant that keeps the line drawn across the direction of travel
     * rather than along it.
     */
    @Test
    public void splittingAndRebuildingAPoint_returnsTheSamePoint() {
        for (SearchLineDirection direction : SearchLineDirection.values()) {
            double alongLine = direction.lineCoordinate(EASTING, NORTHING);
            double travel = direction.travelCoordinate(EASTING, NORTHING);
            assertEquals(direction + " easting", EASTING,
                    direction.eastingAt(alongLine, travel), 0.0001);
            assertEquals(direction + " northing", NORTHING,
                    direction.northingAt(alongLine, travel), 0.0001);
        }
    }

    // ---- bearings and cycling -------------------------------------------

    @Test
    public void compassDegrees_matchTheirDirections() {
        assertEquals(0.0, SearchLineDirection.NORTH.getCompassDegrees(), 0.0);
        assertEquals(90.0, SearchLineDirection.EAST.getCompassDegrees(), 0.0);
        assertEquals(180.0, SearchLineDirection.SOUTH.getCompassDegrees(), 0.0);
        assertEquals(270.0, SearchLineDirection.WEST.getCompassDegrees(), 0.0);
    }

    @Test
    public void next_cyclesClockwiseAndWrapsAround() {
        assertEquals(SearchLineDirection.EAST,
                SearchLineDirection.NORTH.next());
        assertEquals(SearchLineDirection.SOUTH,
                SearchLineDirection.EAST.next());
        assertEquals(SearchLineDirection.WEST,
                SearchLineDirection.SOUTH.next());
        assertEquals(SearchLineDirection.NORTH,
                SearchLineDirection.WEST.next());
    }

    // ---- parsing --------------------------------------------------------

    @Test
    public void fromName_readsItsOwnNames() {
        for (SearchLineDirection direction : SearchLineDirection.values()) {
            assertEquals(direction,
                    SearchLineDirection.fromName(direction.name()));
        }
    }

    @Test
    public void fromName_isCaseAndWhitespaceTolerant() {
        assertEquals(SearchLineDirection.WEST,
                SearchLineDirection.fromName(" west "));
    }

    /**
     * A message from a peer running a build that predates directional lines
     * carries no direction at all. North is what that peer's line actually
     * was, so reading the gap as north is the accurate interpretation rather
     * than merely a safe default.
     */
    @Test
    public void fromName_treatsAMissingDirectionAsNorth() {
        assertEquals(SearchLineDirection.NORTH,
                SearchLineDirection.fromName(null));
        assertEquals(SearchLineDirection.NORTH,
                SearchLineDirection.fromName(""));
        assertEquals(SearchLineDirection.NORTH,
                SearchLineDirection.fromName("sideways"));
    }
}
