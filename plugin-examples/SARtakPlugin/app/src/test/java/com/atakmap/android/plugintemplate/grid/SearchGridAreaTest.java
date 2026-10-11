package com.atakmap.android.plugintemplate.grid;

import org.junit.Before;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Area-wide grid queries used by the map overlay. None of them may walk every
 * cell of the planned area, so they are checked against large areas here.
 */
public class SearchGridAreaTest {

    private static final String ZONE = "56J";
    private static final String OTHER_ZONE = "55J";

    private SearchGridStateStore store;
    private Map<String, SearchGridStatus> statuses;
    private int revision;
    private SearchGridManager manager;

    @Before
    public void setUp() {
        store = mock(SearchGridStateStore.class);
        statuses = new LinkedHashMap<>();
        when(store.getStatus(anyString()))
                .thenReturn(SearchGridStatus.NOT_STARTED);
        when(store.getKnownStatuses()).thenAnswer(invocation ->
                new LinkedHashMap<>(statuses));
        when(store.getRevision()).thenAnswer(invocation -> revision);
        manager = new SearchGridManager(new GridCoordinateConverter(), store);
    }

    @Test
    public void areaExtent_nothingPlannedOrSelected_isNull() {
        assertNull(manager.getAreaExtent());
        assertEquals(0, manager.getAreaCellCount());
    }

    @Test
    public void areaExtent_onlySelectedCell_isItsOneKilometreSquare() {
        manager.selectCell(new GridCoordinateConverter().cellForUtmPoint(
                ZONE, 502750, 6961550, store));

        SearchGridManager.GridExtent area = manager.getAreaExtent();

        assertEquals(502000, area.west, 0.001);
        assertEquals(6961000, area.south, 0.001);
        assertEquals(503000, area.east, 0.001);
        assertEquals(6962000, area.north, 0.001);
        assertEquals(100, manager.getAreaCellCount());
    }

    @Test
    public void areaCellCount_twentyKilometreBox_isExact() {
        planBox(492700, 6951500, 512800, 6971600);

        assertEquals(201 * 201, manager.getAreaCellCount());
        assertEquals(201, manager.getAreaExtent().getColumns());
        assertEquals(201, manager.getAreaExtent().getRows());
    }

    @Test
    public void areaCellCount_circle_matchesCellByCellCount() {
        double centerEasting = 502750;
        double centerNorthing = 6961550;
        double radius = 7300;
        manager.restorePlannedArea(new SearchGridStateStore.PlannedAreaRecord(
                "CIRCLE", ZONE, centerEasting, centerNorthing,
                Math.floor((centerEasting - radius) / 100) * 100,
                Math.floor((centerNorthing - radius) / 100) * 100,
                Math.ceil((centerEasting + radius) / 100) * 100,
                Math.ceil((centerNorthing + radius) / 100) * 100,
                radius, radius * 2, radius * 2));
        SearchGridManager.GridExtent area = manager.getAreaExtent();

        int expected = 0;
        for (double x = area.west; x < area.east; x += 100) {
            for (double y = area.south; y < area.north; y += 100) {
                double dx = x + 50 - centerEasting;
                double dy = y + 50 - centerNorthing;
                if (dx * dx + dy * dy <= radius * radius)
                    expected++;
            }
        }

        assertEquals(expected, manager.getAreaCellCount());
    }

    @Test
    public void markedCells_onlyIncludeSearchedCellsInsideTheArea() {
        planBox(492700, 6951500, 512800, 6971600);
        statuses.put(cellId(ZONE, 500000, 6960000), SearchGridStatus.COMPLETE);
        statuses.put(cellId(ZONE, 500100, 6960000), SearchGridStatus.PARTIAL);
        statuses.put(cellId(ZONE, 500200, 6960000),
                SearchGridStatus.IN_PROGRESS);
        statuses.put(cellId(ZONE, 400000, 6960000), SearchGridStatus.COMPLETE);
        statuses.put(cellId(OTHER_ZONE, 500000, 6960000),
                SearchGridStatus.COMPLETE);
        statuses.put("not-a-cell-id", SearchGridStatus.COMPLETE);

        assertEquals(2, manager.getMarkedCellsInArea().size());
        SearchGridManager.AreaProgress progress = manager.getAreaProgress();
        assertEquals(1, progress.complete);
        assertEquals(1, progress.partial);
        assertEquals(1, progress.inProgress);
        assertEquals(201 * 201, progress.totalCells);
        assertEquals(SearchGridStatus.IN_PROGRESS,
                progress.getAggregateStatus());
    }

    @Test
    public void markedCells_circleExcludesCellsInTheBoundingBoxCorners() {
        double radius = 1000;
        manager.restorePlannedArea(new SearchGridStateStore.PlannedAreaRecord(
                "CIRCLE", ZONE, 500050, 6960050, 499000, 6959000, 501100,
                6961100, radius, radius * 2, radius * 2));
        statuses.put(cellId(ZONE, 500000, 6960000), SearchGridStatus.COMPLETE);
        statuses.put(cellId(ZONE, 499000, 6959000), SearchGridStatus.COMPLETE);

        assertEquals(1, manager.getMarkedCellsInArea().size());
    }

    @Test
    public void markedCells_areCachedUntilAStatusChanges() {
        planBox(492700, 6951500, 512800, 6971600);
        statuses.put(cellId(ZONE, 500000, 6960000), SearchGridStatus.COMPLETE);
        clearInvocations(store);

        manager.getMarkedCellsInArea();
        manager.getMarkedCellsInArea();
        manager.getAreaProgress();
        verify(store, times(1)).getKnownStatuses();

        statuses.put(cellId(ZONE, 500100, 6960000), SearchGridStatus.COMPLETE);
        revision++;

        assertEquals(2, manager.getMarkedCellsInArea().size());
        verify(store, times(2)).getKnownStatuses();
    }

    @Test
    public void areaProgress_everyCellComplete_isComplete() {
        planBox(500000, 6960000, 500200, 6960100);
        statuses.put(cellId(ZONE, 500000, 6960000), SearchGridStatus.COMPLETE);
        statuses.put(cellId(ZONE, 500100, 6960000), SearchGridStatus.COMPLETE);

        assertEquals(SearchGridStatus.COMPLETE,
                manager.getAreaProgress().getAggregateStatus());
    }

    @Test
    public void areaProgress_noSearchedCells_isNotStarted() {
        planBox(492700, 6951500, 512800, 6971600);

        assertEquals(SearchGridStatus.NOT_STARTED,
                manager.getAreaProgress().getAggregateStatus());
    }

    @Test
    public void clipToUtmView_padsSnapsAndClipsToArea() {
        SearchGridManager.GridExtent area = new SearchGridManager.GridExtent(
                ZONE, 492700, 6951500, 512800, 6971600);

        SearchGridManager.GridExtent window = SearchGridManager
                .clipToUtmView(area, 500030, 6960030, 502030, 6961030, 0.5,
                        500);

        // 2 km x 1 km view, padded by 1 km and 500 m, snapped out to 500 m.
        assertEquals(499000, window.west, 0.001);
        assertEquals(6959500, window.south, 0.001);
        assertEquals(503500, window.east, 0.001);
        assertEquals(6962000, window.north, 0.001);
    }

    @Test
    public void clipToUtmView_neverExtendsPastTheArea() {
        SearchGridManager.GridExtent area = new SearchGridManager.GridExtent(
                ZONE, 492700, 6951500, 512800, 6971600);

        SearchGridManager.GridExtent window = SearchGridManager
                .clipToUtmView(area, 400000, 6900000, 600000, 7000000, 0.5,
                        500);

        assertEquals(area.key(), window.key());
    }

    @Test
    public void clipToUtmView_viewOutsideArea_isNull() {
        SearchGridManager.GridExtent area = new SearchGridManager.GridExtent(
                ZONE, 492700, 6951500, 512800, 6971600);

        assertNull(SearchGridManager.clipToUtmView(area, 600000, 6960000,
                601000, 6961000, 0.0, 100));
        assertNotNull(SearchGridManager.clipToUtmView(area, 512000, 6960000,
                513000, 6961000, 0.0, 100));
    }

    private void planBox(double west, double south, double east,
            double north) {
        manager.restorePlannedArea(new SearchGridStateStore.PlannedAreaRecord(
                "BOX", ZONE, (west + east) / 2, (south + north) / 2, west,
                south, east, north, 0, east - west, north - south));
    }

    private static String cellId(String zone, double west, double south) {
        return "utm-" + zone + "-c100-e" + Math.round(west) + "-n"
                + Math.round(south);
    }
}
