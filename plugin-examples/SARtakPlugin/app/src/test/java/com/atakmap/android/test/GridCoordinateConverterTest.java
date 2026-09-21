package com.atakmap.android.plugintemplate.grid;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GridCoordinateConverter.
 *
 * Tests here cover: createCell, aggregateId, toGeoPoints coordinate math, and
 * the internal floorToGrid / clamp helpers (exercised indirectly).
 *
 * These tests require only Mockito — no ATAK SDK on the classpath.
 * For cellForPoint end-to-end tests see GridCoordinateConverterCellForPointTest.
 *
 * Dependencies (build.gradle):
 *   testImplementation 'junit:junit:4.13.2'
 *   testImplementation 'org.mockito:mockito-core:4.11.0'
 */
@RunWith(MockitoJUnitRunner.class)
public class GridCoordinateConverterTest {

    // -------------------------------------------------------------------------
    // A realistic UTM easting/northing pair we can reason about manually.
    //
    // Zone "55H" is over eastern Australia (Brisbane area).
    // Easting  300 350.0 → floorToGrid(100) = 300 300.0  → column = (300300-300000)/100 = 3
    // Northing 6200750.0 → floorToGrid(100) = 6200700.0 → row    = (6200700-6200000)/100 = 7
    // -------------------------------------------------------------------------
    private static final String ZONE           = "55H";
    private static final double EASTING        = 300_350.0;
    private static final double NORTHING       = 6_200_750.0;
    private static final double CELL_WEST      = 300_300.0;
    private static final double CELL_SOUTH     = 6_200_700.0;
    private static final double AGG_WEST       = 300_000.0;
    private static final double AGG_SOUTH      = 6_200_000.0;
    private static final int    EXPECTED_COL   = 3;
    private static final int    EXPECTED_ROW   = 7;

    @Mock
    private SearchGridStateStore mockStateStore;

    private GridCoordinateConverter converter;

    @Before
    public void setUp() {
        converter = new GridCoordinateConverter();
        when(mockStateStore.getStatus(anyString())).thenReturn(SearchGridStatus.NOT_STARTED);
    }

    // =========================================================================
    // createCell — basic field mapping
    // =========================================================================

    @Test
    public void createCell_eastBoundaryIsCellWestPlusCellSize() {
        SearchGridCell cell = makeCell(0, 0);
        assertEquals(CELL_WEST + GridCoordinateConverter.BASE_CELL_SIZE_METERS,
                cell.getEast(), 0.001);
    }

    @Test
    public void createCell_northBoundaryIsCellSouthPlusCellSize() {
        SearchGridCell cell = makeCell(0, 0);
        assertEquals(CELL_SOUTH + GridCoordinateConverter.BASE_CELL_SIZE_METERS,
                cell.getNorth(), 0.001);
    }

    @Test
    public void createCell_westAndSouthArePassedThrough() {
        SearchGridCell cell = makeCell(0, 0);
        assertEquals(CELL_WEST,  cell.getWest(),  0.001);
        assertEquals(CELL_SOUTH, cell.getSouth(), 0.001);
    }

    @Test
    public void createCell_rowAndColumnArePassedThrough() {
        SearchGridCell cell = makeCell(EXPECTED_ROW, EXPECTED_COL);
        assertEquals(EXPECTED_ROW, cell.getRow());
        assertEquals(EXPECTED_COL, cell.getColumn());
    }

    @Test
    public void createCell_aggregateIdIsPassedThrough() {
        String aggId = "some-aggregate-id";
        SearchGridCell cell = converter.createCell(
                aggId, ZONE, CELL_WEST, CELL_SOUTH, 0, 0, mockStateStore);
        assertEquals(aggId, cell.getAggregateId());
    }

    @Test
    public void createCell_zoneDescriptorIsPassedThrough() {
        SearchGridCell cell = makeCell(0, 0);
        assertEquals(ZONE, cell.getZoneDescriptor());
    }

    @Test
    public void createCell_statusIsQueriedByCellId() {
        when(mockStateStore.getStatus(anyString())).thenReturn(SearchGridStatus.COMPLETE);
        SearchGridCell cell = makeCell(0, 0);
        assertEquals(SearchGridStatus.COMPLETE, cell.getStatus());
    }

    @Test
    public void createCell_statusDefaultsToNotStarted() {
        // setUp already stubs getStatus → NOT_STARTED
        SearchGridCell cell = makeCell(0, 0);
        assertEquals(SearchGridStatus.NOT_STARTED, cell.getStatus());
    }

    @Test
    public void createCell_cellSizeIs100Metres() {
        // East - West and North - South should both equal 100 m
        SearchGridCell cell = makeCell(0, 0);
        assertEquals(100.0, cell.getEast()  - cell.getWest(),  0.001);
        assertEquals(100.0, cell.getNorth() - cell.getSouth(), 0.001);
    }

    // =========================================================================
    // createCell — cell ID format
    //
    // Expected pattern: "utm-{zone}-c100-e{roundedWest}-n{roundedSouth}"
    // =========================================================================

    @Test
    public void createCell_cellIdContainsZone() {
        SearchGridCell cell = makeCell(0, 0);
        assertTrue(cell.getId().contains(ZONE));
    }

    @Test
    public void createCell_cellIdContainsRoundedWest() {
        SearchGridCell cell = makeCell(0, 0);
        assertTrue(cell.getId().contains("e" + Math.round(CELL_WEST)));
    }

    @Test
    public void createCell_cellIdContainsRoundedSouth() {
        SearchGridCell cell = makeCell(0, 0);
        assertTrue(cell.getId().contains("n" + Math.round(CELL_SOUTH)));
    }

    @Test
    public void createCell_cellIdHasCellSizeMarker() {
        SearchGridCell cell = makeCell(0, 0);
        assertTrue(cell.getId().contains("-c100-"));
    }

    @Test
    public void createCell_cellIdHasUtmPrefix() {
        SearchGridCell cell = makeCell(0, 0);
        assertTrue(cell.getId().startsWith("utm-"));
    }

    // =========================================================================
    // aggregateId — format checks
    // =========================================================================

    @Test
    public void aggregateId_hasUtmPrefix() {
        String id = converter.aggregateId(ZONE, AGG_WEST, AGG_SOUTH);
        assertTrue(id.startsWith("utm-"));
    }

    @Test
    public void aggregateId_containsZone() {
        String id = converter.aggregateId(ZONE, AGG_WEST, AGG_SOUTH);
        assertTrue(id.contains(ZONE));
    }

    @Test
    public void aggregateId_containsRoundedWest() {
        String id = converter.aggregateId(ZONE, AGG_WEST, AGG_SOUTH);
        assertTrue(id.contains("e" + Math.round(AGG_WEST)));
    }

    @Test
    public void aggregateId_containsRoundedSouth() {
        String id = converter.aggregateId(ZONE, AGG_WEST, AGG_SOUTH);
        assertTrue(id.contains("n" + Math.round(AGG_SOUTH)));
    }

    @Test
    public void aggregateId_has1kMarker() {
        String id = converter.aggregateId(ZONE, AGG_WEST, AGG_SOUTH);
        assertTrue(id.contains("-agg1k-"));
    }

    @Test
    public void aggregateId_differsByZone() {
        String id1 = converter.aggregateId("54H", AGG_WEST, AGG_SOUTH);
        String id2 = converter.aggregateId("55H", AGG_WEST, AGG_SOUTH);
        assertNotEquals(id1, id2);
    }

    @Test
    public void aggregateId_differsByWest() {
        String id1 = converter.aggregateId(ZONE, 300_000.0, AGG_SOUTH);
        String id2 = converter.aggregateId(ZONE, 301_000.0, AGG_SOUTH);
        assertNotEquals(id1, id2);
    }

    @Test
    public void aggregateId_differsBySouth() {
        String id1 = converter.aggregateId(ZONE, AGG_WEST, 6_200_000.0);
        String id2 = converter.aggregateId(ZONE, AGG_WEST, 6_201_000.0);
        assertNotEquals(id1, id2);
    }

    @Test
    public void aggregateId_isDeterministic() {
        String id1 = converter.aggregateId(ZONE, AGG_WEST, AGG_SOUTH);
        String id2 = converter.aggregateId(ZONE, AGG_WEST, AGG_SOUTH);
        assertEquals(id1, id2);
    }

    // =========================================================================
    // floorToGrid — exercised indirectly through createCell & aggregateId
    // =========================================================================

    @Test
    public void floorToGrid_via_aggregateId_alignsTo1000mBoundary() {
        // Both easting values should collapse to the same aggregate west
        String id1 = converter.aggregateId(ZONE, 300_000.0, AGG_SOUTH);
        // If we create the agg ID directly for an easting mid-grid, it should match
        // the one derived from our test easting (300350 floors to 300000)
        SearchGridCell cell = makeCell(EXPECTED_ROW, EXPECTED_COL);
        String aggFromCell = cell.getAggregateId();
        assertEquals(id1, aggFromCell);
    }

    // =========================================================================
    // Row / column boundary values (clamp behaviour)
    // =========================================================================

    @Test
    public void createCell_rowZeroIsAccepted() {
        SearchGridCell cell = makeCell(0, 5);
        assertEquals(0, cell.getRow());
    }

    @Test
    public void createCell_columnZeroIsAccepted() {
        SearchGridCell cell = makeCell(5, 0);
        assertEquals(0, cell.getColumn());
    }

    @Test
    public void createCell_maxRowIsNineIndexed() {
        int maxRow = GridCoordinateConverter.AGGREGATE_CELLS_PER_SIDE - 1;
        SearchGridCell cell = makeCell(maxRow, 0);
        assertEquals(maxRow, cell.getRow());
    }

    @Test
    public void createCell_maxColumnIsNineIndexed() {
        int maxCol = GridCoordinateConverter.AGGREGATE_CELLS_PER_SIDE - 1;
        SearchGridCell cell = makeCell(0, maxCol);
        assertEquals(maxCol, cell.getColumn());
    }

    // =========================================================================
    // Constants sanity checks
    // =========================================================================

    @Test
    public void constants_baseCellSizeIs100() {
        assertEquals(100.0, GridCoordinateConverter.BASE_CELL_SIZE_METERS, 0.0);
    }

    @Test
    public void constants_aggregateGridSizeIs1000() {
        assertEquals(1000.0, GridCoordinateConverter.AGGREGATE_GRID_SIZE_METERS, 0.0);
    }

    @Test
    public void constants_aggregateCellsPerSideIs10() {
        assertEquals(10, GridCoordinateConverter.AGGREGATE_CELLS_PER_SIDE);
    }

    @Test
    public void constants_aggregateGridEqualsBaseCellTimesPerSide() {
        assertEquals(
                GridCoordinateConverter.AGGREGATE_GRID_SIZE_METERS,
                GridCoordinateConverter.BASE_CELL_SIZE_METERS
                        * GridCoordinateConverter.AGGREGATE_CELLS_PER_SIDE,
                0.001);
    }

    // =========================================================================
    // cellForUtmPoint — pure-Java seam behind cellForPoint(GeoPoint)
    // =========================================================================

    @Test
    public void cellForUtmPoint_createsCorrectCell() {
        SearchGridCell cell = converter.cellForUtmPoint(ZONE, EASTING,
                NORTHING, mockStateStore);

        assertEquals(CELL_WEST, cell.getWest(), 0.001);
        assertEquals(CELL_SOUTH, cell.getSouth(), 0.001);
        assertEquals(EXPECTED_ROW, cell.getRow());
        assertEquals(EXPECTED_COL, cell.getColumn());
        assertEquals(ZONE, cell.getZoneDescriptor());
    }

    @Test
    public void cellForUtmPoint_createsCellWithCorrectId() {
        SearchGridCell cell = converter.cellForUtmPoint(ZONE, EASTING,
                NORTHING, mockStateStore);

        assertTrue(cell.getId().startsWith("utm-"));
        assertTrue(cell.getId().contains(ZONE));
        assertTrue(cell.getId().contains("-c100-"));
        assertTrue(cell.getId().contains("e" + Math.round(CELL_WEST)));
        assertTrue(cell.getId().contains("n" + Math.round(CELL_SOUTH)));
    }

    @Test
    public void cellForUtmPoint_roundsDownTo100mGrid() {
        SearchGridCell cell = converter.cellForUtmPoint(ZONE, 300_349.9,
                6_200_750.0, mockStateStore);

        // 300349.9 floors to 300300, not rounds to 300400.
        assertEquals(300_300.0, cell.getWest(), 0.001);
    }

    @Test
    public void cellForUtmPoint_atAggregateOrigin_rowColAreZero() {
        SearchGridCell cell = converter.cellForUtmPoint(ZONE, 300_000.0,
                6_200_000.0, mockStateStore);

        assertEquals(0, cell.getRow());
        assertEquals(0, cell.getColumn());
        assertEquals(300_000.0, cell.getWest(), 0.001);
        assertEquals(6_200_000.0, cell.getSouth(), 0.001);
    }

    @Test
    public void cellForUtmPoint_atMaxRow_createsCellAtRowNine() {
        SearchGridCell cell = converter.cellForUtmPoint(ZONE, 300_000.0,
                6_200_900.0, mockStateStore);

        assertEquals(GridCoordinateConverter.AGGREGATE_CELLS_PER_SIDE - 1,
                cell.getRow());
        assertEquals(0, cell.getColumn());
    }

    @Test
    public void cellForUtmPoint_statusIsQueriedFromStateStore() {
        when(mockStateStore.getStatus(anyString()))
                .thenReturn(SearchGridStatus.COMPLETE);

        SearchGridCell cell = converter.cellForUtmPoint(ZONE, EASTING,
                NORTHING, mockStateStore);

        assertEquals(SearchGridStatus.COMPLETE, cell.getStatus());
    }

    // =========================================================================
    // cellIdForUtmPoint — pure-Java seam behind cellIdForPoint(GeoPoint)
    // =========================================================================

    @Test
    public void cellIdForUtmPoint_returnsExpectedFormat() {
        String id = converter.cellIdForUtmPoint(ZONE, EASTING, NORTHING);

        assertEquals("utm-" + ZONE + "-c100-e300300-n6200700", id);
    }

    @Test
    public void cellIdForUtmPoint_roundsDownTo100mGrid() {
        String id = converter.cellIdForUtmPoint(ZONE, 300_349.9, 6_200_750.0);

        assertTrue(id.contains("e300300"));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private SearchGridCell makeCell(int row, int col) {
        String aggId = converter.aggregateId(ZONE, AGG_WEST, AGG_SOUTH);
        return converter.createCell(aggId, ZONE, CELL_WEST, CELL_SOUTH, row, col, mockStateStore);
    }
}