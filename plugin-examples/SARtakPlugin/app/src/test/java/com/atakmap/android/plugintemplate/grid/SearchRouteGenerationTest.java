package com.atakmap.android.plugintemplate.grid;

import com.atakmap.android.plugintemplate.runtime.SearchRoutePlan;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Auto Route over a planned area: it must include every planned cell, in a
 * walkable order, and survive being drawn and synced at large-area sizes.
 * It used to stop after the column that crossed 600 cells.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, manifest = Config.NONE)
public class SearchRouteGenerationTest {

    private static final String ZONE = "56J";

    private SearchGridStateStore store;
    private SearchGridManager manager;

    @Before
    public void setUp() {
        store = mock(SearchGridStateStore.class);
        when(store.getStatus(anyString()))
                .thenReturn(SearchGridStatus.NOT_STARTED);
        when(store.getKnownStatuses()).thenReturn(
                new LinkedHashMap<String, SearchGridStatus>());
        manager = new SearchGridManager(new GridCoordinateConverter(), store);
    }

    /** The 3 km box that showed the bug on the emulator: 620 of 961 cells. */
    @Test
    public void serpentine_threeKilometreBox_coversAll961Cells() {
        planBox(498500, 6958500, 501600, 6961600);

        List<SearchGridCell> route = manager.getSerpentineRouteCells();

        assertEquals(961, manager.getAreaCellCount());
        assertCoversPlannedAreaOnce(route);
        assertEachStepIsToANeighbour(route);
    }

    @Test
    public void serpentine_twentyKilometreBox_coversAll40401Cells() {
        planBox(492700, 6951500, 512800, 6971600);

        List<SearchGridCell> route = manager.getSerpentineRouteCells();

        assertEquals(40401, route.size());
        assertCoversPlannedAreaOnce(route);
        assertEachStepIsToANeighbour(route);
    }

    @Test
    public void serpentine_alternatesDirectionColumnByColumn() {
        planBox(500000, 6960000, 500300, 6960300);

        List<SearchGridCell> route = manager.getSerpentineRouteCells();

        // 3 x 3 cells: up the first column, down the second, up the third.
        assertEquals(Arrays.asList(
                id(500000, 6960000), id(500000, 6960100), id(500000, 6960200),
                id(500100, 6960200), id(500100, 6960100), id(500100, 6960000),
                id(500200, 6960000), id(500200, 6960100), id(500200, 6960200)),
                ids(route));
    }

    @Test
    public void serpentine_largeCircle_coversEveryPlannedCellOnce() {
        manager.restorePlannedArea(new SearchGridStateStore.PlannedAreaRecord(
                "CIRCLE", ZONE, 502000, 6961000, 492000, 6951000, 512000,
                6971000, 10000, 20000, 20000));

        List<SearchGridCell> route = manager.getSerpentineRouteCells();

        assertTrue("a 10 km circle is far past the old 600-cell stop",
                route.size() > 30000);
        assertCoversPlannedAreaOnce(route);
    }

    // ------------------------------------------------------------------
    // Search direction and assigned areas
    // ------------------------------------------------------------------

    @Test
    public void serpentine_eastLine_snakesRowByRowFromTheSouthWest() {
        List<SearchGridCell> route = manager.getSerpentineRouteCells(
                box(500000, 6960000, 500300, 6960300),
                SearchLineDirection.EAST);

        // 3 x 3 cells: east along the south row, back west, east again.
        assertEquals(Arrays.asList(
                id(500000, 6960000), id(500100, 6960000), id(500200, 6960000),
                id(500200, 6960100), id(500100, 6960100), id(500000, 6960100),
                id(500000, 6960200), id(500100, 6960200), id(500200, 6960200)),
                ids(route));
    }

    @Test
    public void serpentine_westLine_startsSouthEastHeadingWest() {
        List<SearchGridCell> route = manager.getSerpentineRouteCells(
                box(500000, 6960000, 500300, 6960200),
                SearchLineDirection.WEST);

        assertEquals(Arrays.asList(
                id(500200, 6960000), id(500100, 6960000), id(500000, 6960000),
                id(500000, 6960100), id(500100, 6960100), id(500200, 6960100)),
                ids(route));
    }

    @Test
    public void serpentine_southLine_startsNorthWestHeadingSouth() {
        List<SearchGridCell> route = manager.getSerpentineRouteCells(
                box(500000, 6960000, 500200, 6960300),
                SearchLineDirection.SOUTH);

        assertEquals(Arrays.asList(
                id(500000, 6960200), id(500000, 6960100), id(500000, 6960000),
                id(500100, 6960000), id(500100, 6960100), id(500100, 6960200)),
                ids(route));
    }

    @Test
    public void serpentine_northLine_matchesThePlannedAreaRoute() {
        planBox(498500, 6958500, 501600, 6961600);

        assertEquals(ids(manager.getSerpentineRouteCells()),
                ids(manager.getSerpentineRouteCells(
                        manager.getPlannedAreaRecord(),
                        SearchLineDirection.NORTH)));
    }

    /** Every direction covers a non-square box fully, one step at a time. */
    @Test
    public void serpentine_everyDirection_coversTheBoxWithNeighbourSteps() {
        SearchGridStateStore.PlannedAreaRecord area = box(498500, 6958500,
                501600, 6960700);
        for (SearchLineDirection direction : SearchLineDirection.values()) {
            List<SearchGridCell> route = manager.getSerpentineRouteCells(area,
                    direction);

            assertCoversAreaOnce(area, route, 31 * 22);
            assertEachStepIsToANeighbour(route);
            assertFirstLegHeads(direction, route);
        }
    }

    @Test
    public void serpentine_twentyKilometreBoxEast_coversAllAndSyncsAsRuns()
            throws Exception {
        SearchGridStateStore.PlannedAreaRecord area = box(492700, 6951500,
                512800, 6971600);

        long started = System.nanoTime();
        List<SearchGridCell> route = manager.getSerpentineRouteCells(area,
                SearchLineDirection.EAST);
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;

        assertCoversAreaOnce(area, route, 40401);
        assertEachStepIsToANeighbour(route);
        assertTrue("generation took " + elapsedMs + " ms", elapsedMs < 3000);
        // Rows encode as one run each, just like columns.
        SearchRoutePlan plan = new SearchRoutePlan("plan-1", "op-1",
                "team-1", "Alpha", "Green", 0, "uid-1", "LEAD", 1L,
                ids(route));
        assertEquals(201, plan.getCellRuns().size());
        assertEquals(2 * 201, plan.getRouteVertexIds().size());
        String json = plan.toJsonObject().toString();
        assertTrue("document is " + json.length() + " chars",
                json.length() < 20_000);
        assertEquals(ids(route), SearchRoutePlan.fromJson(json).getCellIds());
    }

    @Test
    public void serpentine_circleEast_coversEveryCellOnce() {
        manager.restorePlannedArea(new SearchGridStateStore.PlannedAreaRecord(
                "CIRCLE", ZONE, 502000, 6961000, 492000, 6951000, 512000,
                6971000, 10000, 20000, 20000));

        List<SearchGridCell> route = manager.getSerpentineRouteCells(
                manager.getPlannedAreaRecord(), SearchLineDirection.EAST);

        assertCoversPlannedAreaOnce(route);
    }

    /**
     * Auto Route follows the HQ-assigned area, not the (larger) area this
     * device planned: nothing outside the assignment, nothing in it missed.
     */
    @Test
    public void serpentine_assignedArea_coversExactlyThatArea() {
        planBox(492700, 6951500, 512800, 6971600);
        SearchGridStateStore.PlannedAreaRecord assigned = box(500000, 6960000,
                501500, 6961000);

        List<SearchGridCell> route = manager.getSerpentineRouteCells(assigned,
                SearchLineDirection.EAST);

        assertCoversAreaOnce(assigned, route, 15 * 10);
        assertEquals(id(500000, 6960000), route.get(0).getId());
    }

    @Test
    public void serpentine_noArea_isEmpty() {
        assertTrue(manager.getSerpentineRouteCells(null,
                SearchLineDirection.EAST).isEmpty());
        assertTrue(manager.getSerpentineRouteCells(box(500000, 6960000,
                500000, 6960000), SearchLineDirection.NORTH).isEmpty());
        assertTrue(manager.getSerpentineRouteCells().isEmpty());
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    @Test
    public void routeVertices_serpentineKeepsOnlyTheEndsOfEachColumn() {
        planBox(492700, 6951500, 512800, 6971600);
        List<SearchGridCell> route = manager.getSerpentineRouteCells();

        List<SearchGridCell> vertices = SearchRouteOverlay.routeVertices(route);

        assertEquals(2 * 201, vertices.size());
        assertSame(route.get(0), vertices.get(0));
        assertSame(route.get(route.size() - 1),
                vertices.get(vertices.size() - 1));
    }

    @Test
    public void routeVertices_keepsEveryBendAndReversal() {
        List<SearchGridCell> cells = cells(
                500000, 6960000, 500000, 6960100, 500000, 6960200, // north
                500100, 6960200, 500200, 6960200,                  // east
                500100, 6960200,                                   // back west
                500100, 6960500);                                  // jump north

        List<SearchGridCell> vertices = SearchRouteOverlay.routeVertices(cells);

        assertEquals(Arrays.asList(id(500000, 6960000), id(500000, 6960200),
                id(500200, 6960200), id(500100, 6960200),
                id(500100, 6960500)), ids(vertices));
    }

    /**
     * The overlay now resolves only the ends of each run. That must give the
     * same corners as resolving every cell, for generated and synced plans.
     */
    @Test
    public void runEndpoints_giveTheSameLineAsEveryCell() throws Exception {
        planBox(492700, 6951500, 512800, 6971600);
        List<String> routeIds = ids(manager.getSerpentineRouteCells());
        SearchRoutePlan generated = new SearchRoutePlan("plan-1", "op-1",
                "team-1", "Alpha", "Green", 0xFF00FF00, "uid-1", "LEAD", 123L,
                routeIds);
        SearchRoutePlan synced = SearchRoutePlan.fromJson(
                generated.toJsonObject().toString());

        List<String> everyCell = ids(SearchRouteOverlay.routeVertices(
                manager.cellsForIds(routeIds)));
        for (SearchRoutePlan plan : Arrays.asList(generated, synced)) {
            List<String> fromRuns = ids(SearchRouteOverlay.routeVertices(
                    manager.cellsForIds(plan.getRouteVertexIds())));
            assertEquals(everyCell, fromRuns);
            assertEquals(2 * 201, plan.getRouteVertexIds().size());
        }
    }

    @Test
    public void runEndpoints_ofAHandBuiltRouteMatchEveryCell() {
        List<String> route = Arrays.asList(id(500000, 6960000),
                id(500100, 6960000), id(500200, 6960000), id(500900, 6960900),
                id(500300, 6960000), id(500200, 6960000), id(500200, 6960100),
                id(500200, 6960200), id(500100, 6960200));
        SearchRoutePlan plan = new SearchRoutePlan("plan-1", "op-1", "team-1",
                "Alpha", "Green", 0, "uid-1", "LEAD", 1L, route);

        assertEquals(ids(SearchRouteOverlay.routeVertices(
                manager.cellsForIds(plan.getCellIds()))),
                ids(SearchRouteOverlay.routeVertices(
                        manager.cellsForIds(plan.getRouteVertexIds()))));
    }

    /** Team areas are now built from run ends; same cells as before. */
    @Test
    public void teamAreaCells_matchResolvingEveryCell() {
        planBox(492700, 6951500, 512800, 6971600);
        List<SearchRoutePlan> plans = Arrays.asList(
                new SearchRoutePlan("plan-1", "op-1", "team-1", "Alpha",
                        "Green", 0, "uid-1", "LEAD", 1L,
                        ids(manager.getSerpentineRouteCells())),
                new SearchRoutePlan("plan-2", "op-1", "team-2", "Bravo",
                        "Red", 0, "uid-2", "LEAD", 1L, Arrays.asList(
                                id(500000, 6960000), id(500100, 6960000),
                                id(500200, 6960000), id(500900, 6960900),
                                id(500300, 6960000), id(500200, 6960100),
                                "utm-55J-c100-e700000-n6960000",
                                id(500200, 6960200), id(500100, 6960200))));

        for (SearchRoutePlan plan : plans) {
            List<String> expected = new ArrayList<>();
            for (SearchGridCell cell : manager.cellsForIds(plan.getCellIds()))
                expected.add(Math.round(cell.getWest() / 100) + ","
                        + Math.round(cell.getSouth() / 100) + ","
                        + cell.getZoneDescriptor());
            List<String> actual = new ArrayList<>();
            for (GridCellRuns.Cell cell : SearchTeamAssignmentOverlay
                    .planCells(plan, manager))
                actual.add(cell.column + "," + cell.row + "," + cell.key);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void routeVertices_straightLineIsJustItsEnds() {
        List<SearchGridCell> cells = cells(500000, 6960000, 500100, 6960000,
                500200, 6960000, 500300, 6960000);

        assertEquals(Arrays.asList(id(500000, 6960000), id(500300, 6960000)),
                ids(SearchRouteOverlay.routeVertices(cells)));
    }

    // ------------------------------------------------------------------
    // Sync
    // ------------------------------------------------------------------

    @Test
    public void largeAreaRoute_survivesTheSyncRoundTripInOrder() throws Exception {
        planBox(492700, 6951500, 512800, 6971600);
        List<String> routeIds = ids(manager.getSerpentineRouteCells());
        SearchRoutePlan plan = new SearchRoutePlan("plan-1", "op-1", "team-1",
                "Alpha", "Green", 0xFF00FF00, "uid-1", "LEAD", 123L, routeIds);

        long started = System.nanoTime();
        String json = plan.toJsonObject().toString();
        SearchRoutePlan received = SearchRoutePlan.fromJson(json);
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;

        assertEquals(routeIds, received.getCellIds());
        assertTrue("document is " + json.length() + " chars",
                json.length() < 20_000);
        assertEquals(0, new JSONObject(json).getJSONArray("cellIds").length());
        assertTrue("round trip took " + elapsedMs + " ms", elapsedMs < 2000);
    }

    // ------------------------------------------------------------------

    /**
     * No repeats, nothing outside the area's extent, and as many cells as
     * the area holds -- so every planned cell is on the route exactly once.
     */
    private void assertCoversPlannedAreaOnce(List<SearchGridCell> route) {
        SearchGridManager.GridExtent extent = manager.getAreaExtent();
        Set<String> seen = new HashSet<>();
        for (SearchGridCell cell : route) {
            assertTrue("repeated cell " + cell.getId(), seen.add(cell.getId()));
            assertTrue("cell outside the area " + cell.getId(),
                    cell.getWest() >= extent.west && cell.getEast() <= extent.east
                            && cell.getSouth() >= extent.south
                            && cell.getNorth() <= extent.north);
        }
        assertEquals(manager.getAreaCellCount(), route.size());
    }

    private static void assertCoversAreaOnce(
            SearchGridStateStore.PlannedAreaRecord area,
            List<SearchGridCell> route, int expectedCells) {
        Set<String> seen = new HashSet<>();
        for (SearchGridCell cell : route) {
            assertTrue("repeated cell " + cell.getId(), seen.add(cell.getId()));
            assertTrue("cell outside the area " + cell.getId(),
                    cell.getWest() >= area.west && cell.getEast() <= area.east
                            && cell.getSouth() >= area.south
                            && cell.getNorth() <= area.north);
        }
        assertEquals(expectedCells, route.size());
    }

    /** The first move goes the way the search line travels. */
    private static void assertFirstLegHeads(SearchLineDirection direction,
            List<SearchGridCell> route) {
        SearchGridCell first = route.get(0);
        SearchGridCell second = route.get(1);
        double east = second.getWest() - first.getWest();
        double north = second.getSouth() - first.getSouth();
        double expectedEast = direction == SearchLineDirection.EAST ? 100
                : direction == SearchLineDirection.WEST ? -100 : 0;
        double expectedNorth = direction == SearchLineDirection.NORTH ? 100
                : direction == SearchLineDirection.SOUTH ? -100 : 0;
        assertEquals(direction.name(), expectedEast, east, 0.001);
        assertEquals(direction.name(), expectedNorth, north, 0.001);
    }

    private static SearchGridStateStore.PlannedAreaRecord box(double west,
            double south, double east, double north) {
        return new SearchGridStateStore.PlannedAreaRecord("BOX", ZONE,
                (west + east) / 2, (south + north) / 2, west, south, east,
                north, 0, east - west, north - south);
    }

    private void assertEachStepIsToANeighbour(List<SearchGridCell> route) {
        for (int i = 1; i < route.size(); i++) {
            double step = Math.abs(route.get(i).getWest()
                    - route.get(i - 1).getWest())
                    + Math.abs(route.get(i).getSouth()
                            - route.get(i - 1).getSouth());
            assertEquals("step " + i, 100.0, step, 0.001);
        }
    }

    private void planBox(double west, double south, double east,
            double north) {
        manager.restorePlannedArea(new SearchGridStateStore.PlannedAreaRecord(
                "BOX", ZONE, (west + east) / 2, (south + north) / 2, west,
                south, east, north, 0, east - west, north - south));
    }

    private List<SearchGridCell> cells(double... westSouth) {
        GridCoordinateConverter converter = new GridCoordinateConverter();
        List<SearchGridCell> cells = new ArrayList<>();
        for (int i = 0; i < westSouth.length; i += 2)
            cells.add(converter.parseCell(id(westSouth[i], westSouth[i + 1]),
                    SearchGridStatus.NOT_STARTED));
        return cells;
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
