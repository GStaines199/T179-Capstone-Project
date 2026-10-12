package com.atakmap.android.plugintemplate.grid;

import android.content.Context;

import com.atakmap.android.plugintemplate.runtime.SearchRoutePlan;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Status changes on a large searched area update the area counts, the Grid
 * tab lists and route progress from the changed cells only (Task 3 finding
 * T3-4). Every check compares with a from-scratch rebuild over the same
 * store, so the incremental path can never drift from it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, manifest = Config.NONE)
public class IncrementalGridProgressTest {

    private static final String ZONE = "56J";
    private static final SearchGridStatus[] STATUSES = {
            SearchGridStatus.NOT_STARTED, SearchGridStatus.IN_PROGRESS,
            SearchGridStatus.PARTIAL, SearchGridStatus.COMPLETE};

    private Context context;
    private SearchGridStateStore store;
    private GridCoordinateConverter converter;
    private SearchGridStateStore.PlannedAreaRecord area;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("sartak_search_grid_state",
                Context.MODE_PRIVATE).edit().clear().commit();
        store = new SearchGridStateStore(context);
        store.setOperationId("op-1");
        converter = new GridCoordinateConverter();
        // 3 km box.
        area = new SearchGridStateStore.PlannedAreaRecord("BOX", ZONE,
                500050, 6960050, 498500, 6958500, 501600, 6961600, 0, 3100,
                3100);
    }

    private SearchGridManager newManager() {
        SearchGridManager manager = new SearchGridManager(converter, store);
        manager.restorePlannedArea(area);
        return manager;
    }

    private static String id(int column, int row) {
        return "utm-" + ZONE + "-c100-e" + (498500 + column * 100) + "-n"
                + (6958500 + row * 100);
    }

    // ---- the store's change log ----------------------------------------

    @Test
    public void storeReportsTheCellsWrittenSinceARevision() {
        int before = store.getRevision();
        store.setStatus(id(1, 1), SearchGridStatus.COMPLETE);
        store.setStatus(id(2, 2), SearchGridStatus.PARTIAL);
        store.setStatus(id(1, 1), SearchGridStatus.PARTIAL);
        store.clearStatus(id(3, 3));

        assertEquals(Arrays.asList(id(1, 1), id(2, 2), id(3, 3)),
                store.changedSince(before));
        assertEquals(Arrays.asList(id(3, 3)),
                store.changedSince(store.getRevision() - 1));
        assertTrue(store.changedSince(store.getRevision()).isEmpty());
    }

    @Test
    public void storeCannotAnswerAcrossAnOperationChangeOrTooManyWrites() {
        int before = store.getRevision();
        store.setOperationId("op-2");
        assertNull(store.changedSince(before));

        int start = store.getRevision();
        for (int i = 0; i <= SearchGridStateStore.MAX_TRACKED_CHANGES; i++)
            store.setStatus(id(i % 30, i / 30), SearchGridStatus.PARTIAL);
        assertNull(store.changedSince(start));
        assertEquals(1, store.changedSince(store.getRevision() - 1).size());
    }

    @Test
    public void knownStatusesStayInStepWithWrites() {
        store.setStatus(id(1, 1), SearchGridStatus.COMPLETE);
        assertEquals(SearchGridStatus.COMPLETE,
                store.getKnownStatuses().get(id(1, 1)));

        store.setStatus(id(1, 1), SearchGridStatus.NOT_STARTED);
        assertNull(store.getKnownStatuses().get(id(1, 1)));
        store.setStatus(id(2, 2), SearchGridStatus.PARTIAL);
        store.clearStatus(id(2, 2));
        assertTrue(store.getKnownStatuses().isEmpty());

        // A new store reads the same statuses back from preferences.
        store.setStatus(id(4, 4), SearchGridStatus.COMPLETE);
        SearchGridStateStore reopened = new SearchGridStateStore(context);
        reopened.setOperationId("op-1");
        assertEquals(store.getKnownStatuses(), reopened.getKnownStatuses());
    }

    // ---- area counts, Grid tab lists and route progress ---------------

    @Test
    public void incrementalResultsMatchAFullRebuild() {
        Random random = new Random(42);
        for (int i = 0; i < 400; i++)
            store.setStatus(id(random.nextInt(40), random.nextInt(40)),
                    STATUSES[1 + random.nextInt(3)]);
        SearchGridManager manager = newManager();
        CellProgressList list = new CellProgressList();
        RouteProgressTracker tracker = new RouteProgressTracker();
        SearchRoutePlan route = routeOverFirstColumns(3);
        assertSameAsRebuild(manager, list, tracker, route);
        int generation = manager.getMarkedGeneration();

        for (int round = 0; round < 60; round++) {
            int changes = 1 + random.nextInt(3);
            for (int c = 0; c < changes; c++) {
                // Some outside the 31 x 31 cell area, to be ignored.
                String cellId = id(random.nextInt(40), random.nextInt(40));
                SearchGridStatus status = STATUSES[random.nextInt(4)];
                if (random.nextInt(5) == 0)
                    store.clearStatus(cellId);
                else
                    store.setStatus(cellId, status);
            }
            assertSameAsRebuild(manager, list, tracker, route);
        }
        assertEquals("Small changes never forced a rebuild", generation,
                manager.getMarkedGeneration());
    }

    @Test
    public void manyChangesAtOnceFallBackToARebuild() {
        SearchGridManager manager = newManager();
        CellProgressList list = new CellProgressList();
        RouteProgressTracker tracker = new RouteProgressTracker();
        SearchRoutePlan route = routeOverFirstColumns(2);
        assertSameAsRebuild(manager, list, tracker, route);

        for (int i = 0; i < SearchGridStateStore.MAX_TRACKED_CHANGES + 10;
                i++)
            store.setStatus(id(i % 31, (i / 31) % 31),
                    i % 2 == 0 ? SearchGridStatus.COMPLETE
                            : SearchGridStatus.PARTIAL);

        assertSameAsRebuild(manager, list, tracker, route);
    }

    @Test
    public void changingTheAreaRebuildsTheCounts() {
        store.setStatus(id(1, 1), SearchGridStatus.COMPLETE);
        store.setStatus(id(35, 35), SearchGridStatus.COMPLETE);
        SearchGridManager manager = newManager();
        assertEquals(1, manager.getAreaProgress().complete);

        // 4 km box covers the second cell too.
        manager.restorePlannedArea(new SearchGridStateStore.PlannedAreaRecord(
                "BOX", ZONE, 500550, 6960550, 498500, 6958500, 502600,
                6962600, 0, 4100, 4100));

        assertEquals(2, manager.getAreaProgress().complete);
    }

    private SearchRoutePlan routeOverFirstColumns(int columns) {
        List<String> cells = new ArrayList<>();
        for (int column = 0; column < columns; column++)
            for (int row = 0; row < 31; row++)
                cells.add(id(column, column % 2 == 0 ? row : 30 - row));
        return new SearchRoutePlan("route-1", "op-1", "team-1", "Bravo",
                "Green", 0, "uid", "Lead", 1L, cells);
    }

    private void assertSameAsRebuild(SearchGridManager manager,
            CellProgressList list, RouteProgressTracker tracker,
            SearchRoutePlan route) {
        SearchGridManager fresh = newManager();
        SearchGridManager.AreaProgress expected = fresh.getAreaProgress();
        SearchGridManager.AreaProgress actual = manager.getAreaProgress();
        assertEquals(expected.complete, actual.complete);
        assertEquals(expected.partial, actual.partial);
        assertEquals(expected.inProgress, actual.inProgress);
        assertEquals(expected.totalCells, actual.totalCells);
        assertEquals(ids(fresh.getMarkedCellsInArea()),
                ids(manager.getMarkedCellsInArea()));

        CellProgressList freshList = new CellProgressList();
        for (SearchGridStatus status : new SearchGridStatus[] {
                SearchGridStatus.PARTIAL, SearchGridStatus.COMPLETE}) {
            CellProgressList.Page want = freshList.page(fresh, status, 0);
            for (int page = 0; page < want.pageCount; page++) {
                assertEquals(orderedIds(freshList.page(fresh, status, page)),
                        orderedIds(list.page(manager, status, page)));
            }
        }

        RouteProgressTracker.Progress wantRoute = new RouteProgressTracker()
                .getProgress(route, store);
        RouteProgressTracker.Progress gotRoute = tracker.getProgress(route,
                store);
        assertEquals(wantRoute.completeCells, gotRoute.completeCells);
        assertEquals(wantRoute.nextCellId, gotRoute.nextCellId);
        assertEquals(wantRoute.nextPosition, gotRoute.nextPosition);
    }

    private static Set<String> ids(List<SearchGridCell> cells) {
        Set<String> ids = new HashSet<>();
        for (SearchGridCell cell : cells)
            ids.add(cell.getId() + "=" + cell.getStatus());
        return ids;
    }

    private static List<String> orderedIds(CellProgressList.Page page) {
        List<String> ids = new ArrayList<>();
        for (SearchGridCell cell : page.cells)
            ids.add(cell.getId());
        return ids;
    }
}
