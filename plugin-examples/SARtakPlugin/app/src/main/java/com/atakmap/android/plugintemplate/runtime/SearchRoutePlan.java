package com.atakmap.android.plugintemplate.runtime;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public class SearchRoutePlan {

    /**
     * Routes up to this size also carry the plain {@code cellIds} list, so a
     * device on a build that predates {@code cellRuns} still sees them. Larger
     * routes only send runs: that older code could not have handled them.
     */
    public static final int LEGACY_CELL_ID_LIMIT = 2000;

    private final String planId;
    private final String operationId;
    private final String teamId;
    private final String teamName;
    private final String teamColorName;
    private final int teamColorArgb;
    private final String createdByUid;
    private final String createdByCallsign;
    private final long updatedAt;
    private final List<String> cellIds;
    // cellIds as runs; computed on first use, or taken from the synced
    // document when it describes cellIds exactly.
    private List<String> cellRuns;

    public SearchRoutePlan(String planId, String operationId, String teamId,
            String teamName, String teamColorName, int teamColorArgb,
            String createdByUid, String createdByCallsign, long updatedAt,
            List<String> cellIds) {
        this.planId = safe(planId);
        this.operationId = safe(operationId);
        this.teamId = safe(teamId);
        this.teamName = safe(teamName);
        this.teamColorName = safe(teamColorName);
        this.teamColorArgb = teamColorArgb;
        this.createdByUid = safe(createdByUid);
        this.createdByCallsign = safe(createdByCallsign);
        this.updatedAt = updatedAt;
        // A set keeps first-seen order and drops repeats in one pass. A list
        // contains() check here is quadratic, which a large-area route
        // cannot afford.
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (cellIds != null) {
            for (String cellId : cellIds) {
                String value = safe(cellId);
                if (value.length() > 0)
                    unique.add(value);
            }
        }
        this.cellIds = new ArrayList<>(unique);
    }

    public static SearchRoutePlan empty(String operationId, String teamId,
            String teamName, String teamColorName, int teamColorArgb,
            String uid, String callsign) {
        return new SearchRoutePlan(routePlanId(operationId, teamId),
                operationId, teamId, teamName, teamColorName, teamColorArgb,
                uid, callsign,
                System.currentTimeMillis(), new ArrayList<String>());
    }

    public static SearchRoutePlan fromJson(String json) throws JSONException {
        JSONObject object = new JSONObject(json);
        // Runs are only trusted when written with this version of the plan.
        // A build that predates them updates cellIds and updatedAt but leaves
        // the previous cellRuns in place.
        JSONArray runs = object.optJSONArray("cellRuns");
        boolean runsCurrent = runs != null && object.has("cellRunsUpdatedAt")
                && object.optLong("cellRunsUpdatedAt")
                        == object.optLong("updatedAt", 0L);
        List<String> runList = runsCurrent ? strings(runs) : null;
        List<String> acceptedRuns = new ArrayList<>();
        List<String> cells = runsCurrent
                ? RouteCellRuns.decode(runList, acceptedRuns)
                : strings(object.optJSONArray("cellIds"));
        SearchRoutePlan plan = new SearchRoutePlan(object.optString("planId",
                object.optString("_id", "")),
                object.optString("operationId", ""),
                object.optString("teamId", ""),
                object.optString("teamName", ""),
                object.optString("teamColorName", ""),
                object.optInt("teamColorArgb", 0),
                object.optString("createdByUid", ""),
                object.optString("createdByCallsign", ""),
                object.optLong("updatedAt", 0L), cells);
        // Reuse the received runs only if nothing was skipped as damaged or
        // dropped as a repeat; otherwise they no longer describe the plan.
        if (runsCurrent && acceptedRuns.size() == runList.size()
                && cells.size() == plan.size())
            plan.cellRuns = acceptedRuns;
        return plan;
    }

    public JSONObject toJsonObject() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("_id", planId);
        object.put("planId", planId);
        object.put("operationId", operationId);
        object.put("teamId", teamId);
        object.put("teamName", teamName);
        object.put("teamColorName", teamColorName);
        object.put("teamColorArgb", teamColorArgb);
        object.put("createdByUid", createdByUid);
        object.put("createdByCallsign", createdByCallsign);
        object.put("updatedAt", updatedAt);
        object.put("cellRuns", getCellRunsJson());
        object.put("cellRunsUpdatedAt", updatedAt);
        object.put("cellIds", getLegacyCellIdsJson());
        return object;
    }

    public synchronized List<String> getCellRuns() {
        if (cellRuns == null)
            cellRuns = RouteCellRuns.encode(cellIds);
        return Collections.unmodifiableList(cellRuns);
    }

    /** The cells a drawn route line has to pass through, in order. */
    public List<String> getRouteVertexIds() {
        return RouteCellRuns.endpoints(getCellRuns());
    }

    public JSONArray getCellRunsJson() {
        JSONArray array = new JSONArray();
        for (String run : getCellRuns())
            array.put(run);
        return array;
    }

    /**
     * Every cell id for routes up to {@link #LEGACY_CELL_ID_LIMIT}, otherwise
     * empty, so an older build shows no route rather than a stale one.
     */
    public JSONArray getLegacyCellIdsJson() {
        JSONArray array = new JSONArray();
        if (cellIds.size() <= LEGACY_CELL_ID_LIMIT) {
            for (String cellId : cellIds)
                array.put(cellId);
        }
        return array;
    }

    private static List<String> strings(JSONArray array) {
        List<String> values = new ArrayList<>();
        if (array == null)
            return values;
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "");
            if (value.length() > 0)
                values.add(value);
        }
        return values;
    }

    public SearchRoutePlan withAddedCell(String cellId, String uid,
            String callsign) {
        List<String> next = new ArrayList<>(cellIds);
        String value = safe(cellId);
        if (value.length() > 0 && !next.contains(value))
            next.add(value);
        return new SearchRoutePlan(planId, operationId, teamId, teamName,
                teamColorName, teamColorArgb, uid, callsign,
                System.currentTimeMillis(), next);
    }

    public SearchRoutePlan withCells(List<String> cellIds, String uid,
            String callsign) {
        return new SearchRoutePlan(planId, operationId, teamId, teamName,
                teamColorName, teamColorArgb, uid, callsign,
                System.currentTimeMillis(), cellIds);
    }

    public String getPlanId() { return planId; }
    public String getOperationId() { return operationId; }
    public String getTeamId() { return teamId; }
    public String getTeamName() { return teamName; }
    public String getTeamColorName() { return teamColorName; }
    public int getTeamColorArgb() { return teamColorArgb; }
    public String getCreatedByUid() { return createdByUid; }
    public String getCreatedByCallsign() { return createdByCallsign; }
    public long getUpdatedAt() { return updatedAt; }
    public List<String> getCellIds() {
        return Collections.unmodifiableList(cellIds);
    }
    public int size() { return cellIds.size(); }
    public boolean isEmpty() { return cellIds.isEmpty(); }

    public static String routePlanId(String operationId, String teamId) {
        return "route-plan-" + safe(operationId) + "-" + safe(teamId);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
