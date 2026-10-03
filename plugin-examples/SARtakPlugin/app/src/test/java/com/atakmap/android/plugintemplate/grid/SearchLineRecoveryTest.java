package com.atakmap.android.plugintemplate.grid;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A team leader's search line must survive ATAK being closed, restarted or
 * killed by Android. Each test writes through one store instance and reads
 * through a fresh one, which is what a restarted process sees.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, manifest = Config.NONE)
public class SearchLineRecoveryTest {

    private static final String ZONE = "56J";
    private static final String CELL_ID = "utm-56J-c100-e502700-n6961500";

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        new SearchLineStateStore(context).clear();
        context.getSharedPreferences("sartak_overlay_visibility",
                Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void activeLine_isLoadedByTheNextProcess() {
        new SearchLineStateStore(context).save(record(SearchLineState.ACTIVE,
                6961550, 0L));

        SearchLineStateStore.Record loaded = new SearchLineStateStore(context)
                .load();

        assertNotNull(loaded);
        assertEquals("OP-1", loaded.operationId);
        assertEquals("TEAM-1", loaded.teamId);
        assertEquals(SearchLineState.ACTIVE, loaded.state);
        assertEquals(CELL_ID, loaded.cellId);
        assertEquals(6961550, loaded.lineNorthing, 0.001);
        assertEquals(1000L, loaded.startedAt);
        assertEquals(SearchLineColorOption.values()[1], loaded.color);
        assertEquals(25.0, loaded.toleranceMeters, 0.001);
    }

    @Test
    public void pausedLine_keepsItsPauseTime() {
        new SearchLineStateStore(context).save(record(SearchLineState.PAUSED,
                6961550, 2000L));

        SearchLineStateStore.Record loaded = new SearchLineStateStore(context)
                .load();

        assertEquals(SearchLineState.PAUSED, loaded.state);
        assertEquals(2000L, loaded.pausedAt);
    }

    @Test
    public void endedLine_isNotRestored() {
        SearchLineStateStore store = new SearchLineStateStore(context);
        store.save(record(SearchLineState.ACTIVE, 6961550, 0L));
        store.save(record(SearchLineState.NOT_STARTED, 6961550, 0L));

        assertNull(new SearchLineStateStore(context).load());
    }

    @Test
    public void clear_removesTheSavedLine() {
        SearchLineStateStore store = new SearchLineStateStore(context);
        store.save(record(SearchLineState.ACTIVE, 6961550, 0L));
        store.clear();

        assertNull(new SearchLineStateStore(context).load());
    }

    @Test
    public void stateChange_isSavedImmediately() {
        SearchLineStateStore store = new SearchLineStateStore(context);
        store.save(record(SearchLineState.ACTIVE, 6961550, 0L));
        store.save(record(SearchLineState.PAUSED, 6961560, 3000L));

        assertEquals(SearchLineState.PAUSED,
                new SearchLineStateStore(context).load().state);
    }

    @Test
    public void corruptValues_areDiscardedInsteadOfCrashing() {
        context.getSharedPreferences("sartak_search_line_state",
                Context.MODE_PRIVATE).edit().putString("state", "FLYING")
                .putString("cellId", CELL_ID).commit();

        assertNull(new SearchLineStateStore(context).load());
        assertNull(new SearchLineStateStore(context).load());
    }

    @Test
    public void restoreLeaderLine_putsTheLineBackUnderLeaderControl() {
        GridCoordinateConverter converter = new GridCoordinateConverter();
        SearchPartyAssignmentManager assignment =
                new SearchPartyAssignmentManager(null);
        assignment.setTeamCreated(true);
        SearchLineManager manager = new SearchLineManager(converter,
                assignment);
        SearchGridCell cell = new SearchGridCell("agg", CELL_ID, 5, 7, ZONE,
                502700, 6961500, 502800, 6961600,
                SearchGridStatus.IN_PROGRESS);

        assertTrue(manager.restoreLeaderLine(cell, SearchLineState.PAUSED,
                6961550, 1000L, 2000L, SearchLineColorOption.values()[1],
                25.0));

        assertEquals(SearchLineState.PAUSED, manager.getState());
        assertTrue(manager.isStarted());
        assertTrue(manager.isPaused());
        assertFalse(manager.isRemoteControlled());
        assertEquals(CELL_ID, manager.getActiveCell().getId());
        assertEquals(6961550, manager.getLineNorthing(), 0.001);
        assertEquals(1000L, manager.getLineStartedAt());
        assertEquals(2000L, manager.getLinePausedAt());
        assertEquals(SearchLineColorOption.values()[1],
                manager.getColorOption());
        assertEquals(25.0, manager.getReturnMarkToleranceMeters(), 0.001);
    }

    @Test
    public void restoreLeaderLine_clampsTheNorthingIntoTheCell() {
        SearchLineManager manager = new SearchLineManager(
                new GridCoordinateConverter(),
                new SearchPartyAssignmentManager(null));
        SearchGridCell cell = new SearchGridCell("agg", CELL_ID, 5, 7, ZONE,
                502700, 6961500, 502800, 6961600,
                SearchGridStatus.IN_PROGRESS);

        manager.restoreLeaderLine(cell, SearchLineState.ACTIVE, 9999999, 1L,
                0L, null, 10.0);

        assertEquals(6961600, manager.getLineNorthing(), 0.001);
    }

    @Test
    public void restoreLeaderLine_rejectsMissingCellOrEndedLine() {
        SearchLineManager manager = new SearchLineManager(
                new GridCoordinateConverter(),
                new SearchPartyAssignmentManager(null));
        SearchGridCell cell = new SearchGridCell("agg", CELL_ID, 5, 7, ZONE,
                502700, 6961500, 502800, 6961600,
                SearchGridStatus.IN_PROGRESS);

        assertFalse(manager.restoreLeaderLine(null, SearchLineState.ACTIVE,
                6961550, 1L, 0L, null, 10.0));
        assertFalse(manager.restoreLeaderLine(cell,
                SearchLineState.NOT_STARTED, 6961550, 1L, 0L, null, 10.0));
        assertFalse(manager.isStarted());
    }

    @Test
    public void overlayVisibility_survivesARestart() {
        OverlayVisibilityStore defaults = new OverlayVisibilityStore(context);
        assertFalse(defaults.isGridVisible());
        assertFalse(defaults.isGridLabelsVisible());
        assertTrue(defaults.isRouteVisible());
        assertFalse(defaults.isTeamAreasVisible());

        new OverlayVisibilityStore(context).saveIfChanged(true, true, false,
                true);

        OverlayVisibilityStore restarted = new OverlayVisibilityStore(context);
        assertTrue(restarted.isGridVisible());
        assertTrue(restarted.isGridLabelsVisible());
        assertFalse(restarted.isRouteVisible());
        assertTrue(restarted.isTeamAreasVisible());
    }

    private static SearchLineStateStore.Record record(SearchLineState state,
            double northing, long pausedAt) {
        return new SearchLineStateStore.Record("OP-1", "TEAM-1", state,
                CELL_ID, northing, 1000L, pausedAt,
                SearchLineColorOption.values()[1], 25.0);
    }
}
