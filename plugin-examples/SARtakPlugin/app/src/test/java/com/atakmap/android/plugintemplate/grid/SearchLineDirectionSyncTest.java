package com.atakmap.android.plugintemplate.grid;

import com.atakmap.android.plugintemplate.runtime.SearchLineCotMessage;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.UTMPoint;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * The leader's direction reaching a member's device.
 *
 * <p>Covers the part of search-line synchronisation that is decidable without
 * radios: that the direction and its offset survive the message a leader
 * publishes, and that a member applying that message ends up with the same
 * line geometry the leader is looking at. Whether the message arrives at all
 * over Ditto or a TAK server is a multi-device question and is not tested
 * here.
 *
 * <p>The trap this guards is specific. The offset is a northing for a north or
 * south line and an easting for an east or west one, so a receiver that
 * applies the offset before reading the direction clamps it against the wrong
 * pair of cell edges and snaps the line to a corner of the cell.
 */
public class SearchLineDirectionSyncTest {

    private static final String ZONE = "55H";
    private static final double CELL_WEST = 300_300.0;
    private static final double CELL_SOUTH = 6_200_700.0;
    private static final double CELL_EAST = 300_400.0;
    private static final double CELL_NORTH = 6_200_800.0;
    private static final double LEADER_EASTING = 300_350.0;
    private static final double LEADER_NORTHING = 6_200_750.0;

    private GridCoordinateConverter converter;
    private SearchGridCell cell;
    private SearchLineManager leader;
    private SearchLineManager member;

    @Before
    public void setUp() {
        converter = new GridCoordinateConverter();
        cell = new SearchGridCell("agg-1", "cell-1", 7, 3, ZONE,
                CELL_WEST, CELL_SOUTH, CELL_EAST, CELL_NORTH,
                SearchGridStatus.IN_PROGRESS);
        leader = newManager();
        member = newManager();
    }

    private SearchLineManager newManager() {
        SearchPartyAssignmentManager assignment =
                new SearchPartyAssignmentManager(null);
        assignment.setTeamCreated(true);
        return new SearchLineManager(converter, assignment);
    }

    private GeoPoint leaderPoint() {
        return converter.toGeoPoint(ZONE, LEADER_EASTING, LEADER_NORTHING);
    }

    /** The message a leader's line would publish, built the way the workflow does. */
    private SearchLineCotMessage publishedBy(SearchLineManager manager,
            String action) {
        SearchGridCell active = manager.getActiveCell();
        return new SearchLineCotMessage("msg-1", action, "team-1",
                "UID-LEAD", "Lead", active == null ? ""
                        : active.getZoneDescriptor(),
                active == null ? "" : active.getAggregateId(),
                active == null ? "" : active.getId(),
                active == null ? 0 : active.getRow(),
                active == null ? 0 : active.getColumn(),
                active == null ? 0.0 : active.getWest(),
                active == null ? 0.0 : active.getSouth(),
                active == null ? 0.0 : active.getEast(),
                active == null ? 0.0 : active.getNorth(),
                manager.getLineOffset(), manager.getColorOption(),
                manager.getReturnMarkToleranceMeters(),
                System.currentTimeMillis(), "op-1", manager.getDirection());
    }

    private void leaderStarts(SearchLineDirection direction) {
        leader.setDirection(direction, leaderPoint());
        leader.start(cell, leaderPoint());
    }

    private void sync() {
        member.applyRemote(publishedBy(leader,
                SearchLineCotMessage.ACTION_UPDATE));
    }

    // ---- the direction crosses the wire ---------------------------------

    @Test
    public void everyDirection_survivesTheMessage() {
        for (SearchLineDirection direction : SearchLineDirection.values()) {
            leader = newManager();
            member = newManager();
            leaderStarts(direction);
            sync();

            assertEquals("direction lost in transit for " + direction,
                    direction, member.getDirection());
        }
    }

    @Test
    public void theOffset_survivesTheMessage() {
        leaderStarts(SearchLineDirection.EAST);
        sync();

        assertEquals(leader.getLineOffset(), member.getLineOffset(), 0.5);
    }

    /**
     * The clamp-before-read trap. An east line's offset is an easting of about
     * 300 350; clamped against the cell's southern and northern edges it would
     * be dragged to the south edge, some 5 900 kilometres away, and the member
     * would draw the line along the wrong edge of the cell.
     */
    @Test
    public void anEastLinesOffset_isNotClampedAgainstTheNorthSouthEdges() {
        leaderStarts(SearchLineDirection.EAST);
        sync();

        double offset = member.getLineOffset();
        assertTrue("offset clamped against the wrong axis: " + offset,
                offset >= CELL_WEST && offset <= CELL_EAST);
        assertNotEquals("offset snapped to a cell corner",
                CELL_SOUTH, offset, 1.0);
    }

    // ---- the member draws what the leader drew --------------------------

    @Test
    public void theMemberDrawsTheLineWhereTheLeaderDrewIt() {
        for (SearchLineDirection direction : SearchLineDirection.values()) {
            leader = newManager();
            member = newManager();
            leaderStarts(direction);
            sync();

            UTMPoint leaderStart = UTMPoint.fromGeoPoint(leader.getLineStart());
            UTMPoint memberStart = UTMPoint.fromGeoPoint(member.getLineStart());
            UTMPoint leaderEnd = UTMPoint.fromGeoPoint(leader.getLineEnd());
            UTMPoint memberEnd = UTMPoint.fromGeoPoint(member.getLineEnd());

            assertEquals(direction + " line start easting",
                    leaderStart.getEasting(), memberStart.getEasting(), 1.0);
            assertEquals(direction + " line start northing",
                    leaderStart.getNorthing(), memberStart.getNorthing(), 1.0);
            assertEquals(direction + " line end easting",
                    leaderEnd.getEasting(), memberEnd.getEasting(), 1.0);
            assertEquals(direction + " line end northing",
                    leaderEnd.getNorthing(), memberEnd.getNorthing(), 1.0);
        }
    }

    @Test
    public void theMemberPointsTheArrowTheSameWay() {
        for (SearchLineDirection direction : SearchLineDirection.values()) {
            leader = newManager();
            member = newManager();
            leaderStarts(direction);
            sync();

            UTMPoint leaderTip =
                    UTMPoint.fromGeoPoint(leader.getDirectionEnd());
            UTMPoint memberTip =
                    UTMPoint.fromGeoPoint(member.getDirectionEnd());
            assertEquals(direction + " arrow easting",
                    leaderTip.getEasting(), memberTip.getEasting(), 1.0);
            assertEquals(direction + " arrow northing",
                    leaderTip.getNorthing(), memberTip.getNorthing(), 1.0);
        }
    }

    @Test
    public void theMemberShowsTheSameHeadingText() {
        leaderStarts(SearchLineDirection.WEST);
        sync();

        assertTrue(member.getSummary().contains("West"));
        assertTrue(member.getSummary().contains("270"));
    }

    // ---- turning mid-search ---------------------------------------------

    @Test
    public void aLeadersTurn_reachesTheMember() {
        leaderStarts(SearchLineDirection.NORTH);
        sync();
        assertEquals(SearchLineDirection.NORTH, member.getDirection());

        leader.setDirection(SearchLineDirection.SOUTH, leaderPoint());
        sync();

        assertEquals("a mid-search turn must reach the team",
                SearchLineDirection.SOUTH, member.getDirection());
    }

    // ---- older peers ----------------------------------------------------

    /**
     * A message from a build that predates directional lines carries no
     * direction. North is what that peer's line actually was, so reading the
     * gap as north reproduces their geometry rather than guessing at it.
     */
    @Test
    public void aMessageWithoutADirection_isTreatedAsNorth() {
        member.applyRemote(new SearchLineCotMessage("msg-legacy",
                SearchLineCotMessage.ACTION_UPDATE, "team-1", "UID-LEAD",
                "Lead", ZONE, "agg-1", "cell-1", 7, 3,
                CELL_WEST, CELL_SOUTH, CELL_EAST, CELL_NORTH,
                LEADER_NORTHING, SearchLineColorOption.CYAN, 10.0,
                System.currentTimeMillis(), "op-1"));

        assertEquals(SearchLineDirection.NORTH, member.getDirection());
        assertEquals("a legacy offset is a northing",
                LEADER_NORTHING, member.getLineOffset(), 1.0);
    }

    // ---- ending the line ------------------------------------------------

    @Test
    public void endingTheLine_clearsTheMembersGeometry() {
        leaderStarts(SearchLineDirection.EAST);
        sync();

        member.applyRemote(publishedBy(leader,
                SearchLineCotMessage.ACTION_END));

        assertEquals(SearchLineState.NOT_STARTED, member.getState());
        assertEquals(0.0, member.getLineOffset(), 0.0);
    }
}
