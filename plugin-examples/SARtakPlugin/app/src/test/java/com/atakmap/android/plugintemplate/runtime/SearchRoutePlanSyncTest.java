package com.atakmap.android.plugintemplate.runtime;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * How a route plan travels between devices: the compact cellRuns form, the
 * plain cellIds list kept for older builds, and which one a reader trusts.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, manifest = Config.NONE)
public class SearchRoutePlanSyncTest {

    // ------------------------------------------------------------------
    // RouteCellRuns
    // ------------------------------------------------------------------

    @Test
    public void runs_serpentineColumnsBecomeOneRunEach() {
        List<String> route = serpentine(500000, 6960000, 3, 4);

        List<String> runs = RouteCellRuns.encode(route);

        assertEquals(Arrays.asList(
                id(500000, 6960000) + "~" + id(500000, 6960300),
                id(500100, 6960300) + "~" + id(500100, 6960000),
                id(500200, 6960000) + "~" + id(500200, 6960300)), runs);
        assertEquals(route, RouteCellRuns.decode(runs));
    }

    @Test
    public void runs_roundTripRowsGapsAndLoneCellsExactly() {
        List<String> route = Arrays.asList(
                id(500000, 6960000), id(500100, 6960000), id(500200, 6960000),
                id(500900, 6960900),                         // lone cell
                id(500300, 6960000), id(500200, 6960000),    // westward
                id(500200, 6960100),                         // turn north
                "not-a-cell-id",                             // kept verbatim
                id(501000, 6961000));
        assertEquals(route, RouteCellRuns.decode(RouteCellRuns.encode(route)));
    }

    @Test
    public void runs_neverJoinCellsFromDifferentZones() {
        List<String> route = Arrays.asList("utm-56J-c100-e500000-n6960000",
                "utm-55J-c100-e500100-n6960000");

        assertEquals(route, RouteCellRuns.decode(RouteCellRuns.encode(route)));
        assertEquals(2, RouteCellRuns.encode(route).size());
    }

    @Test
    public void runs_ignoreDamagedRuns() {
        List<String> decoded = RouteCellRuns.decode(Arrays.asList(
                id(500000, 6960000) + "~" + id(500100, 6960100), // diagonal
                id(500000, 6960000) + "~" + "rubbish",
                "utm-56J-c100-e500000-n6960000~utm-55J-c100-e500200-n6960000",
                id(500000, 6960050) + "~" + id(500000, 6960000), // off grid
                id(501000, 6961000)));

        assertEquals(Collections.singletonList(id(501000, 6961000)), decoded);
    }

    @Test
    public void runs_refuseToExpandBeyondTheSafetyLimit() {
        List<String> decoded = RouteCellRuns.decode(Collections.singletonList(
                id(100000, 1000000) + "~" + id(100000, 9900000)));

        assertTrue(decoded.size() <= RouteCellRuns.MAX_DECODED_CELLS);
    }

    // ------------------------------------------------------------------
    // SearchRoutePlan
    // ------------------------------------------------------------------

    @Test
    public void plan_dropsRepeatsKeepingFirstPosition() {
        SearchRoutePlan plan = plan(Arrays.asList("a", "b", "a", "c", "b"), 1L);

        assertEquals(Arrays.asList("a", "b", "c"), plan.getCellIds());
    }

    @Test
    public void plan_buildsALargeAreaRouteQuickly() {
        List<String> route = serpentine(492700, 6951500, 201, 201);
        long started = System.nanoTime();

        SearchRoutePlan plan = plan(route, 1L);

        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        assertEquals(40401, plan.size());
        assertTrue("took " + elapsedMs + " ms", elapsedMs < 1000);
    }

    @Test
    public void plan_smallRouteStillCarriesCellIdsForOlderBuilds()
            throws Exception {
        List<String> route = serpentine(500000, 6960000, 10, 10);
        JSONObject json = plan(route, 7L).toJsonObject();

        assertEquals(route, strings(json.getJSONArray("cellIds")));
        assertEquals(7L, json.getLong("cellRunsUpdatedAt"));
        assertEquals(10, json.getJSONArray("cellRuns").length());
    }

    @Test
    public void plan_largeRouteSendsAnEmptyCellIdsList() throws Exception {
        List<String> route = serpentine(500000, 6960000, 50, 50);
        JSONObject json = plan(route, 7L).toJsonObject();

        assertEquals(0, json.getJSONArray("cellIds").length());
        assertEquals(route, SearchRoutePlan.fromJson(json.toString())
                .getCellIds());
    }

    /** A plan written before cellRuns existed. */
    @Test
    public void fromJson_readsDocumentsFromOlderBuilds() throws Exception {
        JSONObject json = baseDocument(5L);
        json.put("cellIds", new JSONArray(Arrays.asList("x", "y")));

        assertEquals(Arrays.asList("x", "y"),
                SearchRoutePlan.fromJson(json.toString()).getCellIds());
    }

    /**
     * An older build updating a plan rewrites cellIds and updatedAt but
     * leaves the cellRuns a newer build wrote earlier. Those runs are stale.
     */
    @Test
    public void fromJson_ignoresRunsLeftOverFromAnEarlierVersion()
            throws Exception {
        JSONObject json = baseDocument(9L);
        json.put("cellRuns", new JSONArray(RouteCellRuns.encode(
                serpentine(500000, 6960000, 3, 3))));
        json.put("cellRunsUpdatedAt", 5L);
        json.put("cellIds", new JSONArray(Collections.singletonList(
                id(510000, 6970000))));

        assertEquals(Collections.singletonList(id(510000, 6970000)),
                SearchRoutePlan.fromJson(json.toString()).getCellIds());
    }

    @Test
    public void fromJson_runsAlwaysDescribeTheCellsTheyCameWith()
            throws Exception {
        List<String> route = serpentine(500000, 6960000, 4, 5);
        SearchRoutePlan synced = SearchRoutePlan.fromJson(
                plan(route, 3L).toJsonObject().toString());

        assertEquals(route, synced.getCellIds());
        assertEquals(RouteCellRuns.encode(route), synced.getCellRuns());
    }

    /** A damaged run is skipped, so the received runs cannot be reused. */
    @Test
    public void fromJson_withADamagedRun_rebuildsRunsFromTheCells()
            throws Exception {
        JSONObject json = baseDocument(4L);
        json.put("cellRuns", new JSONArray(Arrays.asList(
                id(500000, 6960000) + "~" + id(500000, 6960200),
                id(500000, 6960000) + "~" + id(500100, 6960100), // diagonal
                id(500100, 6960200))));
        json.put("cellRunsUpdatedAt", 4L);

        SearchRoutePlan synced = SearchRoutePlan.fromJson(json.toString());

        List<String> expected = Arrays.asList(id(500000, 6960000),
                id(500000, 6960100), id(500000, 6960200), id(500100, 6960200));
        assertEquals(expected, synced.getCellIds());
        assertEquals(RouteCellRuns.encode(expected), synced.getCellRuns());
        assertEquals(Arrays.asList(id(500000, 6960000), id(500000, 6960200),
                id(500100, 6960200)), synced.getRouteVertexIds());
    }

    /** A repeated cell is dropped, so the received runs cannot be reused. */
    @Test
    public void fromJson_withOverlappingRuns_rebuildsRunsFromTheCells()
            throws Exception {
        JSONObject json = baseDocument(4L);
        json.put("cellRuns", new JSONArray(Arrays.asList(
                id(500000, 6960000) + "~" + id(500000, 6960200),
                id(500000, 6960100) + "~" + id(500000, 6960300))));
        json.put("cellRunsUpdatedAt", 4L);

        SearchRoutePlan synced = SearchRoutePlan.fromJson(json.toString());

        assertEquals(4, synced.size());
        assertEquals(RouteCellRuns.encode(synced.getCellIds()),
                synced.getCellRuns());
    }

    // ------------------------------------------------------------------

    private static SearchRoutePlan plan(List<String> cells, long updatedAt) {
        return new SearchRoutePlan("plan-1", "op-1", "team-1", "Alpha",
                "Green", 0xFF00FF00, "uid-1", "LEAD", updatedAt, cells);
    }

    private static JSONObject baseDocument(long updatedAt) throws Exception {
        JSONObject json = new JSONObject();
        json.put("planId", "plan-1");
        json.put("operationId", "op-1");
        json.put("teamId", "team-1");
        json.put("updatedAt", updatedAt);
        return json;
    }

    private static List<String> serpentine(long west, long south, int columns,
            int rows) {
        List<String> ids = new ArrayList<>();
        for (int c = 0; c < columns; c++) {
            for (int r = 0; r < rows; r++) {
                int row = c % 2 == 0 ? r : rows - 1 - r;
                ids.add(id(west + c * 100L, south + row * 100L));
            }
        }
        return ids;
    }

    private static List<String> strings(JSONArray array) throws Exception {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < array.length(); i++)
            values.add(array.getString(i));
        return values;
    }

    private static String id(long west, long south) {
        return "utm-56J-c100-e" + west + "-n" + south;
    }
}
