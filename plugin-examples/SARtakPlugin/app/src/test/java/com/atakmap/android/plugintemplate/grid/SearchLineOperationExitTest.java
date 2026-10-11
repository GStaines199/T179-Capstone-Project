package com.atakmap.android.plugintemplate.grid;

import android.content.Context;

import com.atakmap.android.plugintemplate.runtime.SearchLineCotMessage;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Leaving an operation must end this device's search line. It used to clear
 * only the saved copy, so a leader who left with an ACTIVE line found it
 * still ACTIVE in the next operation, and it survived an ATAK restart once
 * the line was saved again under the new operation.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, manifest = Config.NONE)
public class SearchLineOperationExitTest {

    private static final String ZONE = "56J";
    private static final String CELL_ID = "utm-56J-c100-e502700-n6961500";

    private Context context;
    private SearchLineStateStore store;
    private SearchLineManager manager;
    private int endsPublished;
    private SearchLineState stateWhenPublished;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        store = new SearchLineStateStore(context);
        store.clear();
        manager = new SearchLineManager(new GridCoordinateConverter(),
                new SearchPartyAssignmentManager(null));
        endsPublished = 0;
        stateWhenPublished = null;
    }

    @Test
    public void leadersActiveLine_isEnded_toldToTheTeam_andForgotten() {
        startOwnLine(SearchLineState.ACTIVE);

        boolean published = SearchLineOperationExit.endForLeave(manager,
                store, true, this::publishEnd);

        assertTrue(published);
        assertEquals(1, endsPublished);
        // The END describes an ended line, as endSearchLine() publishes it.
        assertEquals(SearchLineState.NOT_STARTED, stateWhenPublished);
        assertFalse(manager.isStarted());
        assertEquals(SearchLineState.NOT_STARTED, manager.getState());
        assertNull(new SearchLineStateStore(context).load());
    }

    @Test
    public void pausedLine_isEndedToo() {
        startOwnLine(SearchLineState.PAUSED);

        assertTrue(SearchLineOperationExit.endForLeave(manager, store, true,
                this::publishEnd));
        assertFalse(manager.isStarted());
    }

    @Test
    public void memberFollowingTheLeadersLine_endsLocally_withoutPublishing() {
        manager.applyRemote(new SearchLineCotMessage("msg-1",
                SearchLineCotMessage.ACTION_UPDATE, "team-1", "UID-LEAD",
                "Lead", ZONE, "agg-1", CELL_ID, 7, 3, 502700, 6961500,
                502800, 6961600, 6961550, SearchLineColorOption.CYAN, 10.0,
                System.currentTimeMillis(), "op-1"));
        assertTrue(manager.isStarted());

        assertFalse(SearchLineOperationExit.endForLeave(manager, store, true,
                this::publishEnd));
        assertEquals(0, endsPublished);
        assertFalse(manager.isStarted());
    }

    @Test
    public void withoutATeam_theLineStillEnds_butNothingIsPublished() {
        startOwnLine(SearchLineState.ACTIVE);

        assertFalse(SearchLineOperationExit.endForLeave(manager, store,
                false, this::publishEnd));
        assertEquals(0, endsPublished);
        assertFalse(manager.isStarted());
        assertNull(new SearchLineStateStore(context).load());
    }

    @Test
    public void noLine_publishesNothing_andClearsAnyStaleSave() {
        store.save(record());

        assertFalse(SearchLineOperationExit.endForLeave(manager, store, true,
                this::publishEnd));
        assertEquals(0, endsPublished);
        assertNull(new SearchLineStateStore(context).load());
    }

    private void publishEnd() {
        endsPublished++;
        stateWhenPublished = manager.getState();
    }

    private void startOwnLine(SearchLineState state) {
        SearchGridCell cell = new SearchGridCell("agg", CELL_ID, 5, 7, ZONE,
                502700, 6961500, 502800, 6961600,
                SearchGridStatus.IN_PROGRESS);
        assertTrue(manager.restoreLeaderLine(cell, state,
                SearchLineDirection.EAST, 502750, 1000L,
                state == SearchLineState.PAUSED ? 2000L : 0L,
                SearchLineColorOption.values()[1], 25.0));
        store.save(record());
        assertTrue(manager.isStarted());
    }

    private static SearchLineStateStore.Record record() {
        return new SearchLineStateStore.Record("OP-1", "TEAM-1",
                SearchLineState.ACTIVE, CELL_ID, SearchLineDirection.EAST,
                502750, 1000L, 0L, SearchLineColorOption.values()[1], 25.0);
    }
}
