package com.atakmap.android.plugintemplate.grid;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SearchGridManager.
 *
 * Requires only Mockito — no ATAK SDK on the classpath. The ATAK-bound
 * {@link SearchGridManager#selectCellAt} entry point is exercised through the
 * ATAK-free {@link SearchGridManager#selectCell(SearchGridCell)} seam.
 *
 * Dependencies (build.gradle):
 *   testImplementation 'junit:junit:4.13.2'
 *   testImplementation 'org.mockito:mockito-core:4.11.0'
 */
@RunWith(MockitoJUnitRunner.class)
public class SearchGridManagerTest {

    private static final String AGG_ID = "utm-55H-agg1k-e300000-n6200000";
    private static final String CELL_ID = "utm-55H-c100-e300300-n6200700";
    private static final String ZONE = "55H";

    @Mock
    private GridCoordinateConverter mockConverter;

    @Mock
    private SearchGridStateStore mockStateStore;

    private SearchGridManager manager;

    @Before
    public void setUp() {
        manager = new SearchGridManager(mockConverter, mockStateStore);
    }

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Test
    public void getSelectedCell_whenNothingSelected_returnsNull() {
        assertEquals(null, manager.getSelectedCell());
    }

    @Test
    public void getSelectedAggregateId_whenNothingSelected_returnsNull() {
        assertEquals(null, manager.getSelectedAggregateId());
    }

    // -------------------------------------------------------------------------
    // selectCell — selection logic behind the ATAK-bound selectCellAt(GeoPoint)
    // -------------------------------------------------------------------------

    @Test
    public void selectCell_setsSelectedCellAndAggregateId() {
        SearchGridCell cell = cell(SearchGridStatus.NOT_STARTED);

        manager.selectCell(cell);

        assertEquals(cell, manager.getSelectedCell());
        assertEquals(AGG_ID, manager.getSelectedAggregateId());
    }

    @Test
    public void selectCell_whenCellNotStarted_marksItInProgressAndPersists() {
        SearchGridCell cell = cell(SearchGridStatus.NOT_STARTED);

        manager.selectCell(cell);

        assertEquals(SearchGridStatus.IN_PROGRESS, cell.getStatus());
        verify(mockStateStore).setStatus(CELL_ID,
                SearchGridStatus.IN_PROGRESS);
    }

    @Test
    public void selectCell_whenCellAlreadyInProgress_doesNotOverwriteStatus() {
        SearchGridCell cell = cell(SearchGridStatus.COMPLETE);

        manager.selectCell(cell);

        // A previously completed cell keeps its status; only NOT_STARTED
        // cells are advanced to IN_PROGRESS on selection.
        assertEquals(SearchGridStatus.COMPLETE, cell.getStatus());
        verify(mockStateStore, never()).setStatus(eq(CELL_ID), any());
    }

    // -------------------------------------------------------------------------
    // setSelectedStatus / setCellStatus
    // -------------------------------------------------------------------------

    @Test
    public void setSelectedStatus_whenNoCellSelected_noException() {
        manager.setSelectedStatus(SearchGridStatus.COMPLETE);
    }

    @Test
    public void setSelectedStatus_updatesSelectedCellAndPersists() {
        SearchGridCell cell = cell(SearchGridStatus.IN_PROGRESS);
        manager.selectCell(cell);

        manager.setSelectedStatus(SearchGridStatus.COMPLETE);

        assertEquals(SearchGridStatus.COMPLETE, cell.getStatus());
        verify(mockStateStore).setStatus(CELL_ID, SearchGridStatus.COMPLETE);
    }

    @Test
    public void setCellStatus_withComplete_persistsStatus() {
        manager.setCellStatus(CELL_ID, SearchGridStatus.COMPLETE);

        verify(mockStateStore).setStatus(CELL_ID, SearchGridStatus.COMPLETE);
    }

    @Test
    public void setCellStatus_withNotStarted_clearsStatus() {
        manager.setCellStatus(CELL_ID, SearchGridStatus.NOT_STARTED);

        verify(mockStateStore).clearStatus(CELL_ID);
    }

    @Test
    public void setCellStatus_updatesSelectedCellInPlaceWhenIdMatches() {
        SearchGridCell cell = cell(SearchGridStatus.IN_PROGRESS);
        manager.selectCell(cell);

        manager.setCellStatus(CELL_ID, SearchGridStatus.COMPLETE);

        assertEquals(SearchGridStatus.COMPLETE, cell.getStatus());
    }

    @Test
    public void setCellStatus_doesNotTouchSelectedCellWhenIdDiffers() {
        SearchGridCell cell = cell(SearchGridStatus.IN_PROGRESS);
        manager.selectCell(cell);

        manager.setCellStatus("utm-55H-c100-e300400-n6200700",
                SearchGridStatus.COMPLETE);

        assertEquals(SearchGridStatus.IN_PROGRESS, cell.getStatus());
    }

    @Test
    public void setCellStatus_withNullCellId_noException() {
        manager.setCellStatus(null, SearchGridStatus.COMPLETE);

        verify(mockStateStore, never()).setStatus(anyString(), any());
    }

    @Test
    public void setCellStatus_withEmptyCellId_noException() {
        manager.setCellStatus("", SearchGridStatus.COMPLETE);

        verify(mockStateStore, never()).setStatus(anyString(), any());
    }

    @Test
    public void setCellStatus_withNullStatus_noException() {
        manager.setCellStatus(CELL_ID, null);

        verify(mockStateStore, never()).setStatus(anyString(), any());
    }

    // -------------------------------------------------------------------------
    // getSelectedAggregateCells
    // -------------------------------------------------------------------------

    @Test
    public void getSelectedAggregateCells_whenNoCellSelected_returnsEmpty() {
        assertTrue(manager.getSelectedAggregateCells().isEmpty());
    }

    @Test
    public void getSelectedAggregateCells_returns100Cells() {
        SearchGridCell cell = cell(SearchGridStatus.IN_PROGRESS);
        manager.selectCell(cell);
        when(mockConverter.createCell(anyString(), anyString(), anyDouble(),
                anyDouble(), anyInt(), anyInt(), any()))
                .thenReturn(cell);

        List<SearchGridCell> cells = manager.getSelectedAggregateCells();

        // 10x10 aggregate bucket.
        assertEquals(GridCoordinateConverter.AGGREGATE_CELLS_PER_SIDE
                * GridCoordinateConverter.AGGREGATE_CELLS_PER_SIDE,
                cells.size());
    }

    @Test
    public void getSelectedAggregateCells_usesSelectedCellBounds() {
        SearchGridCell cell = cell(SearchGridStatus.IN_PROGRESS);
        manager.selectCell(cell);
        when(mockConverter.createCell(anyString(), anyString(), anyDouble(),
                anyDouble(), anyInt(), anyInt(), any()))
                .thenReturn(cell);

        manager.getSelectedAggregateCells();

        // First cell of the aggregate bucket starts at the 1km boundary.
        verify(mockConverter).createCell(eq(AGG_ID), eq(ZONE), eq(300_000.0),
                eq(6_200_000.0), eq(0), eq(0), eq(mockStateStore));
    }

    // -------------------------------------------------------------------------
    // getAggregateStatus
    // -------------------------------------------------------------------------

    @Test
    public void getAggregateStatus_emptyList_returnsNotStarted() {
        assertEquals(SearchGridStatus.NOT_STARTED,
                manager.getAggregateStatus(new ArrayList<>()));
    }

    @Test
    public void getAggregateStatus_allComplete_returnsComplete() {
        List<SearchGridCell> cells = new ArrayList<>();
        cells.add(cell(SearchGridStatus.COMPLETE));
        cells.add(cell(SearchGridStatus.COMPLETE));

        assertEquals(SearchGridStatus.COMPLETE,
                manager.getAggregateStatus(cells));
    }

    @Test
    public void getAggregateStatus_someCompleteSomeNotStarted_returnsInProgress() {
        List<SearchGridCell> cells = new ArrayList<>();
        cells.add(cell(SearchGridStatus.COMPLETE));
        cells.add(cell(SearchGridStatus.NOT_STARTED));

        assertEquals(SearchGridStatus.IN_PROGRESS,
                manager.getAggregateStatus(cells));
    }

    @Test
    public void getAggregateStatus_onlyPartialCells_returnsInProgress() {
        List<SearchGridCell> cells = new ArrayList<>();
        cells.add(cell(SearchGridStatus.PARTIAL));
        cells.add(cell(SearchGridStatus.PARTIAL));

        assertEquals(SearchGridStatus.IN_PROGRESS,
                manager.getAggregateStatus(cells));
    }

    @Test
    public void getAggregateStatus_onlyInProgressCells_returnsNotStarted() {
        List<SearchGridCell> cells = new ArrayList<>();
        cells.add(cell(SearchGridStatus.IN_PROGRESS));
        cells.add(cell(SearchGridStatus.IN_PROGRESS));

        // IN_PROGRESS cells do not count toward zoomed-out progress
        // aggregation; they render unfilled at low zoom.
        assertEquals(SearchGridStatus.NOT_STARTED,
                manager.getAggregateStatus(cells));
    }

    @Test
    public void getAggregateStatus_completeAndPartial_returnsInProgress() {
        List<SearchGridCell> cells = new ArrayList<>();
        cells.add(cell(SearchGridStatus.COMPLETE));
        cells.add(cell(SearchGridStatus.COMPLETE));
        cells.add(cell(SearchGridStatus.PARTIAL));

        // COMPLETE requires every cell complete; a PARTIAL cell drops the
        // aggregate to IN_PROGRESS.
        assertEquals(SearchGridStatus.IN_PROGRESS,
                manager.getAggregateStatus(cells));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private SearchGridCell cell(SearchGridStatus status) {
        return new SearchGridCell(AGG_ID, CELL_ID, 7, 3, ZONE, 300_300.0,
                6_200_700.0, 300_400.0, 6_200_800.0, status);
    }
}
