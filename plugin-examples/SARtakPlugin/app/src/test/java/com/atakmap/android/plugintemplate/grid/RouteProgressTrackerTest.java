package com.atakmap.android.plugintemplate.grid;

import com.atakmap.android.plugintemplate.runtime.SearchRoutePlan;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Remaining route cells and the next route cell. These are shown on every
 * panel refresh, so besides the counts the tests pin that the stored
 * statuses are only re-read when a status or the route changes.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, manifest = Config.NONE)
public class RouteProgressTrackerTest {

    private static final String ZONE = "56J";

    private SearchGridStateStore store;
    private Map<String, SearchGridStatus> statuses;
    private int revision;
    private RouteProgressTracker tracker;

    @Before
    public void setUp() {
        store = mock(SearchGridStateStore.class);
        statuses = new LinkedHashMap<>();
        when(store.getKnownStatuses()).thenAnswer(invocation ->
                new LinkedHashMap<>(statuses));
        when(store.getRevision()).thenAnswer(invocation -> revision);
        tracker = new RouteProgressTracker();
    }

    @Test
    public void noRoute_hasNoProgress() {
        assertNull(tracker.getProgress(null, store));
        assertNull(tracker.getProgress(plan(1L), store));
    }

    @Test
    public void untouchedRoute_isAllLeft_andStartsAtTheFirstCell() {
        RouteProgressTracker.Progress progress = tracker.getProgress(
                plan(1L, "a", "b", "c", "d", "e"), store);

        assertEquals(5, progress.totalCells);
        assertEquals(5, progress.getRemaining());
        assertEquals("a", progress.nextCellId);
        assertEquals(1, progress.nextPosition);
        assertFalse(progress.isComplete());
    }

    @Test
    public void nextCell_isTheFirstRouteCellThatIsNotComplete() {
        mark("a", SearchGridStatus.COMPLETE);
        mark("b", SearchGridStatus.COMPLETE);
        mark("d", SearchGridStatus.COMPLETE);

        RouteProgressTracker.Progress progress = tracker.getProgress(
                plan(1L, "a", "b", "c", "d", "e"), store);

        assertEquals(2, progress.getRemaining());
        assertEquals("c", progress.nextCellId);
        assertEquals(3, progress.nextPosition);
    }

    @Test
    public void partialAndInProgressCells_areStillLeft() {
        mark("a", SearchGridStatus.PARTIAL);
        mark("b", SearchGridStatus.IN_PROGRESS);

        RouteProgressTracker.Progress progress = tracker.getProgress(
                plan(1L, "a", "b", "c"), store);

        assertEquals(3, progress.getRemaining());
        assertEquals("a", progress.nextCellId);
    }

    @Test
    public void completeCellsOffTheRoute_areIgnored() {
        mark("x", SearchGridStatus.COMPLETE);
        mark("y", SearchGridStatus.COMPLETE);
        mark("b", SearchGridStatus.COMPLETE);

        RouteProgressTracker.Progress progress = tracker.getProgress(
                plan(1L, "a", "b", "c"), store);

        assertEquals(1, progress.completeCells);
        assertEquals(2, progress.getRemaining());
        assertEquals("a", progress.nextCellId);
    }

    @Test
    public void everyCellComplete_isReportedAsComplete() {
        mark("a", SearchGridStatus.COMPLETE);
        mark("b", SearchGridStatus.COMPLETE);

        RouteProgressTracker.Progress progress = tracker.getProgress(
                plan(1L, "a", "b"), store);

        assertTrue(progress.isComplete());
        assertEquals(0, progress.getRemaining());
        assertNull(progress.nextCellId);
        assertEquals(0, progress.nextPosition);
    }

    @Test
    public void statusChange_movesTheNumbers_bothWays() {
        SearchRoutePlan plan = plan(1L, "a", "b", "c");
        assertEquals(3, tracker.getProgress(plan, store).getRemaining());

        mark("a", SearchGridStatus.COMPLETE);
        RouteProgressTracker.Progress progress = tracker.getProgress(plan,
                store);
        assertEquals(2, progress.getRemaining());
        assertEquals("b", progress.nextCellId);

        // Cleared back to not started: it is left again and next again.
        statuses.remove("a");
        revision++;
        progress = tracker.getProgress(plan, store);
        assertEquals(3, progress.getRemaining());
        assertEquals("a", progress.nextCellId);
    }

    @Test
    public void refreshWithoutChanges_doesNotReReadTheStatuses() {
        SearchRoutePlan plan = plan(1L, "a", "b", "c");
        for (int i = 0; i < 50; i++)
            tracker.getProgress(plan, store);

        verify(store, times(1)).getKnownStatuses();

        revision++;
        tracker.getProgress(plan, store);
        verify(store, times(2)).getKnownStatuses();
    }

    @Test
    public void newRouteVersion_isRecountedAtTheSameRevision() {
        mark("a", SearchGridStatus.COMPLETE);
        assertEquals(1, tracker.getProgress(plan(1L, "a", "b"), store)
                .getRemaining());

        RouteProgressTracker.Progress progress = tracker.getProgress(
                plan(2L, "c", "a", "d"), store);

        assertEquals(2, progress.getRemaining());
        assertEquals("c", progress.nextCellId);
    }

    /**
     * A 20 km Auto Route is 40,401 cells. With 5,000 complete the numbers
     * must be exact and repeated refreshes must not re-read the statuses.
     */
    @Test
    public void twentyKilometreRoute_countsExactly_andIsCached() {
        SearchGridManager manager = new SearchGridManager(
                new GridCoordinateConverter(), store);
        when(store.getStatus(anyString()))
                .thenReturn(SearchGridStatus.NOT_STARTED);
        manager.restorePlannedArea(new SearchGridStateStore.PlannedAreaRecord(
                "BOX", ZONE, 500050, 6960050, 490000, 6950000, 510100,
                6970100, 0, 20100, 20100));
        List<String> routeIds = new ArrayList<>();
        for (SearchGridCell cell : manager.getSerpentineRouteCells())
            routeIds.add(cell.getId());
        assertEquals(40401, routeIds.size());
        for (int i = 0; i < 5000; i++)
            statuses.put(routeIds.get(i), SearchGridStatus.COMPLETE);
        statuses.put(routeIds.get(5001), SearchGridStatus.COMPLETE);
        revision++;
        SearchRoutePlan plan = new SearchRoutePlan("route-1", "op-1", "t-1",
                "Bravo", "Cyan", 0, "uid", "Lead", 1L, routeIds);
        // The grid manager read the statuses while loading; count only ours.
        clearInvocations(store);

        long started = System.nanoTime();
        RouteProgressTracker.Progress progress = null;
        for (int i = 0; i < 100; i++)
            progress = tracker.getProgress(plan, store);
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;

        assertEquals(40401 - 5001, progress.getRemaining());
        assertEquals(routeIds.get(5000), progress.nextCellId);
        assertEquals(5001, progress.nextPosition);
        verify(store, times(1)).getKnownStatuses();
        assertTrue("100 refreshes took " + elapsedMs + " ms",
                elapsedMs < 2000);
    }

    private void mark(String cellId, SearchGridStatus status) {
        statuses.put(cellId, status);
        revision++;
    }

    private static SearchRoutePlan plan(long updatedAt, String... cellIds) {
        return new SearchRoutePlan("route-1", "op-1", "t-1", "Bravo", "Cyan",
                0, "uid", "Lead", updatedAt, Arrays.asList(cellIds));
    }
}
