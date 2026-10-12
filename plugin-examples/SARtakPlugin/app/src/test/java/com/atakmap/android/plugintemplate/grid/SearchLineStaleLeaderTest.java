package com.atakmap.android.plugintemplate.grid;

import com.atakmap.android.plugintemplate.runtime.SearchLineCotMessage;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A member's copy of the leader's line once the leader goes quiet.
 *
 * <p>The member keeps the line through a dropout, but must say it is no
 * longer being updated instead of showing it as current for good.
 */
public class SearchLineStaleLeaderTest {

    private static final String ZONE = "55H";
    private static final long SENT_AT = 1_000_000_000L;

    private GridCoordinateConverter converter;
    private SearchGridCell cell;
    private SearchLineManager member;

    @Before
    public void setUp() {
        converter = new GridCoordinateConverter();
        cell = new SearchGridCell("agg-1", "cell-1", 7, 3, ZONE,
                300_300.0, 6_200_700.0, 300_400.0, 6_200_800.0,
                SearchGridStatus.IN_PROGRESS);
        SearchPartyAssignmentManager assignment =
                new SearchPartyAssignmentManager(null);
        assignment.setTeamCreated(true);
        member = new SearchLineManager(converter, assignment);
    }

    private SearchLineCotMessage leaderMessage(String action, long created) {
        return new SearchLineCotMessage("msg-" + created, action, "team-1",
                "UID-LEAD", "Lead", ZONE, cell.getAggregateId(),
                cell.getId(), cell.getRow(), cell.getColumn(),
                cell.getWest(), cell.getSouth(), cell.getEast(),
                cell.getNorth(), 6_200_750.0, SearchLineColorOption.CYAN,
                10.0, created, "op-1", SearchLineDirection.NORTH);
    }

    @Test
    public void freshLeaderLineIsNotMarkedStale() {
        member.applyRemote(leaderMessage(SearchLineCotMessage.ACTION_UPDATE,
                SENT_AT));

        long now = SENT_AT + 10_000L;
        assertFalse(member.isRemoteStale(now));
        assertTrue(member.getSummary(now)
                .startsWith("Leader line ACTIVE | Lead"));
        assertFalse(member.getWarningSummary(now).contains("NO UPDATE"));
    }

    @Test
    public void silentLeaderLineIsMarkedStaleButKept() {
        member.applyRemote(leaderMessage(SearchLineCotMessage.ACTION_UPDATE,
                SENT_AT));

        long now = SENT_AT + 125_000L;
        assertTrue(member.isRemoteStale(now));
        assertEquals(SearchLineState.ACTIVE, member.getState());
        assertTrue(member.getSummary(now).startsWith(
                "Leader line ACTIVE (no update for 2 min) | Lead"));
        assertTrue(member.getWarningSummary(now).startsWith(
                "NO UPDATE: Lead has not updated the line for 2 min."));
    }

    @Test
    public void staleMarkClearsWhenTheLeaderPublishesAgain() {
        member.applyRemote(leaderMessage(SearchLineCotMessage.ACTION_UPDATE,
                SENT_AT));
        long later = SENT_AT + 90_000L;
        assertTrue(member.isRemoteStale(later));

        member.applyRemote(leaderMessage(SearchLineCotMessage.ACTION_UPDATE,
                later - 1_000L));

        assertFalse(member.isRemoteStale(later));
        assertTrue(member.getSummary(later)
                .startsWith("Leader line ACTIVE | Lead"));
    }

    @Test
    public void pausedLeaderLineAlsoGoesStale() {
        member.applyRemote(leaderMessage(SearchLineCotMessage.ACTION_PAUSE,
                SENT_AT));

        assertTrue(member.getSummary(SENT_AT + 45_000L).startsWith(
                "Leader line PAUSED (no update for 45 s) | Lead"));
    }

    @Test
    public void endedOrOwnLinesAreNeverStale() {
        member.applyRemote(leaderMessage(SearchLineCotMessage.ACTION_END,
                SENT_AT));
        assertFalse(member.isRemoteStale(SENT_AT + 600_000L));

        member.start(cell, converter.toGeoPoint(ZONE, 300_350.0,
                6_200_750.0));
        assertFalse(member.isRemoteStale(Long.MAX_VALUE / 2));
        assertTrue(member.getSummary(Long.MAX_VALUE / 2)
                .startsWith("Line ACTIVE |"));
    }

    @Test
    public void ageIsShownInSecondsThenMinutes() {
        assertEquals("31 s", SearchLineManager.formatAge(31_400L));
        assertEquals("1 min", SearchLineManager.formatAge(119_000L));
        assertEquals("0 s", SearchLineManager.formatAge(-5L));
    }
}
