package com.atakmap.android.plugintemplate.grid;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The lane or box shown after the second route-selection tap. It must draw
 * as a few rectangles at any size and report what Confirm would add.
 */
public class RouteSelectionPreviewTest {

    private static final String ZONE = "56J";
    private static final int MAX_CELLS = 1200;

    private final GridCoordinateConverter converter =
            new GridCoordinateConverter();
    private SearchGridStateStore store;
    private SearchGridManager manager;

    @Before
    public void setUp() {
        store = mock(SearchGridStateStore.class);
        when(store.getStatus(anyString()))
                .thenReturn(SearchGridStatus.NOT_STARTED);
        when(store.getKnownStatuses()).thenReturn(
                new LinkedHashMap<String, SearchGridStatus>());
        manager = new SearchGridManager(converter, store);
        // 20 km x 20 km planned area, as on the emulators.
        manager.restorePlannedArea(new SearchGridStateStore.PlannedAreaRecord(
                "BOX", ZONE, 502750, 6961550, 492700, 6951500, 512800,
                6971600, 0, 20100, 20100));
    }

    @Test
    public void box_isOneRectangleWithItsCellCount() {
        RouteSelectionPreview preview = preview(cell(500000, 6960000),
                cell(500300, 6960400), null);

        assertEquals(20, preview.getCellCount());
        assertEquals(20, preview.getNewCellCount());
        assertFalse(preview.isLane());
        assertEquals("Box", preview.getLabel());
        List<GridCellRuns.Rect> blocks = preview.blocks();
        assertEquals(1, blocks.size());
        GridCellRuns.Rect block = blocks.get(0);
        assertEquals(5000, block.getFirstColumn());
        assertEquals(69600, block.getFirstRow());
        assertEquals(5003, block.getLastColumn());
        assertEquals(69604, block.getLastRow());
        assertEquals(ZONE, block.getKey());
        assertEquals("Box preview: 20 cells", preview.describe());
    }

    @Test
    public void lane_isDetectedEitherWay() {
        assertTrue(preview(cell(500000, 6960000), cell(500000, 6960900), null)
                .isLane());
        RouteSelectionPreview row = preview(cell(500900, 6960000),
                cell(500000, 6960000), null);
        assertTrue(row.isLane());
        assertEquals(10, row.getCellCount());
        assertEquals(1, row.blocks().size());
    }

    @Test
    public void singleCell_isOneCell() {
        RouteSelectionPreview preview = preview(cell(500000, 6960000),
                cell(500000, 6960000), null);

        assertEquals(1, preview.getCellCount());
        assertEquals("Lane preview: 1 cell", preview.describe());
    }

    /** The biggest allowed selection is still only one or two map items. */
    @Test
    public void cappedBox_staysAFewRectanglesAndSaysItWasCut() {
        RouteSelectionPreview preview = preview(cell(495000, 6955000),
                cell(510000, 6970000), null);

        assertEquals(MAX_CELLS, preview.getCellCount());
        assertTrue(preview.isLimited());
        assertTrue(preview.blocks().size() <= 2);
        int drawn = 0;
        for (GridCellRuns.Rect block : preview.blocks())
            drawn += block.getCellCount();
        assertEquals(MAX_CELLS, drawn);
        assertTrue(preview.describe().contains("selection limit reached"));
    }

    @Test
    public void cellsAlreadyOnTheRoute_areNotCountedAsNew() {
        RouteSelectionPreview preview = preview(cell(500000, 6960000),
                cell(500000, 6960400), new HashSet<>(Arrays.asList(
                        id(500000, 6960000), id(500000, 6960100),
                        id(700000, 6960100))));

        assertEquals(5, preview.getCellCount());
        assertEquals(3, preview.getNewCellCount());
        assertEquals("Lane preview: 5 cells (2 already on route)",
                preview.describe());
    }

    @Test
    public void boxClippedByACircle_mergesIntoAtMostOneRectanglePerRow() {
        manager.restorePlannedArea(new SearchGridStateStore.PlannedAreaRecord(
                "CIRCLE", ZONE, 500000, 6960000, 499000, 6959000, 501000,
                6961000, 1000, 2000, 2000));

        RouteSelectionPreview preview = preview(cell(499000, 6959000),
                cell(500900, 6960900), null);

        assertTrue(preview.getCellCount() > 250);
        assertTrue(preview.getCellCount() < 400);
        assertTrue(preview.blocks().size() <= 20);
        int drawn = 0;
        for (GridCellRuns.Rect block : preview.blocks())
            drawn += block.getCellCount();
        assertEquals(preview.getCellCount(), drawn);
    }

    @Test
    public void emptySelection_isEmpty() {
        RouteSelectionPreview preview = new RouteSelectionPreview(null, null,
                null, MAX_CELLS, null);

        assertTrue(preview.isEmpty());
        assertTrue(preview.blocks().isEmpty());
        assertFalse(preview.isLimited());
    }

    private RouteSelectionPreview preview(SearchGridCell anchor,
            SearchGridCell end, java.util.Set<String> routeIds) {
        return new RouteSelectionPreview(anchor, end, manager.cellsBetween(
                anchor, end, MAX_CELLS), MAX_CELLS, routeIds);
    }

    private SearchGridCell cell(double west, double south) {
        return converter.parseCell(id(west, south),
                SearchGridStatus.NOT_STARTED);
    }

    private static String id(double west, double south) {
        return "utm-" + ZONE + "-c100-e" + Math.round(west) + "-n"
                + Math.round(south);
    }
}
