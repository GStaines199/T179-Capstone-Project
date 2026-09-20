package com.atakmap.android.plugintemplate.runtime;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SearchRoutePlan {

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
        this.cellIds = new ArrayList<>();
        if (cellIds != null) {
            for (String cellId : cellIds) {
                String value = safe(cellId);
                if (value.length() > 0 && !this.cellIds.contains(value))
                    this.cellIds.add(value);
            }
        }
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
        JSONArray array = object.optJSONArray("cellIds");
        List<String> cells = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                String cellId = array.optString(i, "");
                if (cellId.length() > 0)
                    cells.add(cellId);
            }
        }
        return new SearchRoutePlan(object.optString("planId", object
                .optString("_id", "")),
                object.optString("operationId", ""),
                object.optString("teamId", ""),
                object.optString("teamName", ""),
                object.optString("teamColorName", ""),
                object.optInt("teamColorArgb", 0),
                object.optString("createdByUid", ""),
                object.optString("createdByCallsign", ""),
                object.optLong("updatedAt", 0L), cells);
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
        JSONArray array = new JSONArray();
        for (String cellId : cellIds)
            array.put(cellId);
        object.put("cellIds", array);
        return object;
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
