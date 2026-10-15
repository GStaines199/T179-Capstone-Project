package com.atakmap.android.plugintemplate.grid;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Grid tab lists of partial and completed cells: area-scoped, sorted, paged,
 * and cheap to ask for again on every panel refresh.
 */
public class CellProgressListTest {

    private static final String ZONE = "56J";

    private SearchGridStateStore store;
    private Map<String, SearchGridStatus> statuses;
    private int revision;
    private SearchGridManager manager;
    private CellProgressList list;

    @Before
    public void setUp() {
        store = mock(SearchGridStateStore.class);
        statuses = new LinkedHashMap<>();
        when(store.getStatus(anyString()))
                .thenReturn(SearchGridStatus.NOT_STARTED);
        when(store.getKnownStatuses()).thenAnswer(invocation ->
                new LinkedHashMap<>(statuses));
        when(store.getRevision()).thenAnswer(invocation -> revision);
        // The mock keeps no change log, so every revision is a full rebuild.
        when(store.changedSince(anyInt())).thenReturn(null);
        manager = new SearchGridManager(new GridCoordinateConverter(), store);
        list = new CellProgressList();
        // 3 km box.
        manager.restorePlannedArea(new SearchGridStateStore.PlannedAreaRecord(
                "BOX", ZONE, 500050, 6960050, 498500, 6958500, 501600,
                6961600, 0, 3100, 3100));
    }

    @Test
    public void lists_onlyHoldTheirStatusInsideTheArea() {
        mark(SearchGridStatus.COMPLETE, 500000, 6960000);
        mark(SearchGridStatus.PARTIAL, 500100, 6960000);
        mark(SearchGridStatus.IN_PROGRESS, 500200, 6960000);
        mark(SearchGridStatus.COMPLETE, 600000, 6960000); // outside the area
        statuses.put("utm-55J-c100-e500000-n6960000",
                SearchGridStatus.COMPLETE); // other zone

        CellProgressList.Page complete = list.page(manager,
                SearchGridStatus.COMPLETE, 0);
        CellProgressList.Page partial = list.page(manager,
                SearchGridStatus.PARTIAL, 0);

        assertEquals(Arrays.asList(id(500000, 6960000)), ids(complete.cells));
        assertEquals(Arrays.asList(id(500100, 6960000)), ids(partial.cells));
        assertEquals(1, complete.partialTotal);
        assertEquals(1, complete.completeTotal);
    }

    @Test
    public void cells_areInMapReadingOrder() {
        mark(SearchGridStatus.COMPLETE, 500100, 6960000);
        mark(SearchGridStatus.COMPLETE, 500000, 6960100);
        mark(SearchGridStatus.COMPLETE, 500000, 6960000);
        mark(SearchGridStatus.COMPLETE, 500100, 6960100);

        CellProgressList.Page page = list.page(manager,
                SearchGridStatus.COMPLETE, 0);

        // North row first, then west to east.
        assertEquals(Arrays.asList(id(500000, 6960100), id(500100, 6960100),
                id(500000, 6960000), id(500100, 6960000)), ids(page.cells));
    }

    @Test
    public void pages_holdEightCellsAndShowTheTotal() {
        for (int i = 0; i < 20; i++)
            mark(SearchGridStatus.PARTIAL, 499000 + i * 100, 6960000);

        CellProgressList.Page first = list.page(manager,
                SearchGridStatus.PARTIAL, 0);
        CellProgressList.Page last = list.page(manager,
                SearchGridStatus.PARTIAL, 2);

        assertEquals(CellProgressList.PAGE_SIZE, first.cells.size());
        assertEquals(20, first.total);
        assertEquals(3, first.pageCount);
        assertEquals(1, first.firstNumber());
        assertEquals(8, first.lastNumber());
        assertFalse(first.hasPrevious());
        assertTrue(first.hasNext());
        assertEquals(4, last.cells.size());
        assertEquals(17, last.firstNumber());
        assertEquals(20, last.lastNumber());
        assertTrue(last.hasPrevious());
        assertFalse(last.hasNext());
        assertEquals(id(500600, 6960000), last.cells.get(0).getId());
    }

    /** Clearing cells can empty the page being shown; show the last one. */
    @Test
    public void pageIndex_isClampedWhenTheListShrinks() {
        for (int i = 0; i < 20; i++)
            mark(SearchGridStatus.PARTIAL, 499000 + i * 100, 6960000);
        assertEquals(2, list.page(manager, SearchGridStatus.PARTIAL, 2)
                .pageIndex);

        for (int i = 0; i < 12; i++)
            clear(499000 + i * 100, 6960000);
        CellProgressList.Page page = list.page(manager,
                SearchGridStatus.PARTIAL, 2);

        assertEquals(0, page.pageIndex);
        assertEquals(8, page.total);
        assertEquals(1, page.pageCount);
        assertEquals(0, list.page(manager, SearchGridStatus.PARTIAL, -3)
                .pageIndex);
    }

    @Test
    public void emptyList_isOneEmptyPage() {
        CellProgressList.Page page = list.page(manager,
                SearchGridStatus.COMPLETE, 5);

        assertTrue(page.cells.isEmpty());
        assertEquals(0, page.total);
        assertEquals(1, page.pageCount);
        assertEquals(0, page.firstNumber());
        assertEquals(0, page.lastNumber());
    }

    @Test
    public void markingACell_movesItBetweenLists() {
        mark(SearchGridStatus.PARTIAL, 500000, 6960000);
        assertEquals(1, list.page(manager, SearchGridStatus.PARTIAL, 0)
                .total);

        mark(SearchGridStatus.COMPLETE, 500000, 6960000);
        CellProgressList.Page page = list.page(manager,
                SearchGridStatus.PARTIAL, 0);

        assertEquals(0, page.total);
        assertEquals(1, page.completeTotal);
    }

    /**
     * A 20 km area with every cell complete: the list is built once from
     * the stored statuses, and later refreshes reuse it.
     */
    @Test
    public void twentyKilometreAreaAllComplete_buildsOnceAndPagesCheaply() {
        manager.restorePlannedArea(new SearchGridStateStore.PlannedAreaRecord(
                "BOX", ZONE, 502750, 6961550, 492700, 6951500, 512800,
                6971600, 0, 20100, 20100));
        for (int column = 0; column < 201; column++)
            for (int row = 0; row < 201; row++)
                statuses.put(id(492700 + column * 100, 6951500 + row * 100),
                        SearchGridStatus.COMPLETE);
        revision++;

        long started = System.nanoTime();
        CellProgressList.Page first = list.page(manager,
                SearchGridStatus.COMPLETE, 0);
        long buildMs = (System.nanoTime() - started) / 1_000_000L;
        started = System.nanoTime();
        CellProgressList.Page later = null;
        for (int i = 0; i < 100; i++)
            later = list.page(manager, SearchGridStatus.COMPLETE, 5000);
        long refreshMs = (System.nanoTime() - started) / 1_000_000L;

        assertEquals(40401, first.total);
        assertEquals(id(492700, 6971500), first.cells.get(0).getId());
        assertEquals((40401 + 7) / 8, later.pageCount);
        assertEquals(1, list.getRebuildCount());
        assertTrue("build took " + buildMs + " ms", buildMs < 3000);
        assertTrue("100 refreshes took " + refreshMs + " ms",
                refreshMs < 200);
    }

    private void mark(SearchGridStatus status, double west, double south) {
        statuses.put(id(west, south), status);
        revision++;
    }

    private void clear(double west, double south) {
        statuses.remove(id(west, south));
        revision++;
    }

    private static List<String> ids(List<SearchGridCell> cells) {
        List<String> ids = new ArrayList<>();
        for (SearchGridCell cell : cells)
            ids.add(cell.getId());
        return ids;
    }

    private static String id(double west, double south) {
        return "utm-" + ZONE + "-c100-e" + Math.round(west) + "-n"
                + Math.round(south);
    }
}
