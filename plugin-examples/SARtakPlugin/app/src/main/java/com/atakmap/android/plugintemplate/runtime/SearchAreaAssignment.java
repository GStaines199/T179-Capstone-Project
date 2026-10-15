package com.atakmap.android.plugintemplate.runtime;

import com.atakmap.android.plugintemplate.grid.SearchGridStateStore;

import org.json.JSONException;
import org.json.JSONObject;

public class SearchAreaAssignment {

    public static final String STATUS_ASSIGNED = "assigned";
    public static final String STATUS_ACKNOWLEDGED = "acknowledged";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_COMPLETE = "complete";

    private final String assignmentId;
    private final String operationId;
    private final String teamId;
    private final String teamName;
    private final String leaderUid;
    private final String leaderCallsign;
    private final String assignedByUid;
    private final String assignedByCallsign;
    private final String status;
    private final long updatedAt;
    private final SearchGridStateStore.PlannedAreaRecord area;

    public SearchAreaAssignment(String assignmentId, String operationId,
            String teamId, String teamName, String leaderUid,
            String leaderCallsign, String assignedByUid,
            String assignedByCallsign, String status, long updatedAt,
            SearchGridStateStore.PlannedAreaRecord area) {
        this.assignmentId = safe(assignmentId);
        this.operationId = safe(operationId);
        this.teamId = safe(teamId);
        this.teamName = safe(teamName);
        this.leaderUid = safe(leaderUid);
        this.leaderCallsign = safe(leaderCallsign);
        this.assignedByUid = safe(assignedByUid);
        this.assignedByCallsign = safe(assignedByCallsign);
        this.status = safe(status).length() == 0 ? STATUS_ASSIGNED
                : safe(status);
        this.updatedAt = updatedAt;
        this.area = area;
    }

    public static SearchAreaAssignment fromJson(String json)
            throws JSONException {
        JSONObject object = new JSONObject(json);
        SearchGridStateStore.PlannedAreaRecord area =
                new SearchGridStateStore.PlannedAreaRecord(
                        object.optString("shape", ""),
                        object.optString("zone", ""),
                        object.optDouble("centerEasting", 0.0),
                        object.optDouble("centerNorthing", 0.0),
                        object.optDouble("west", 0.0),
                        object.optDouble("south", 0.0),
                        object.optDouble("east", 0.0),
                        object.optDouble("north", 0.0),
                        object.optDouble("radiusMeters", 0.0),
                        object.optDouble("widthMeters", 0.0),
                        object.optDouble("heightMeters", 0.0));
        return new SearchAreaAssignment(object.optString("assignmentId",
                object.optString("_id", "")),
                object.optString("operationId", ""),
                object.optString("teamId", ""),
                object.optString("teamName", ""),
                object.optString("leaderUid", ""),
                object.optString("leaderCallsign", ""),
                object.optString("assignedByUid", ""),
                object.optString("assignedByCallsign", ""),
                object.optString("status", STATUS_ASSIGNED),
                object.optLong("updatedAt", 0L), area);
    }

    public String getAssignmentId() { return assignmentId; }
    public String getOperationId() { return operationId; }
    public String getTeamId() { return teamId; }
    public String getTeamName() { return teamName; }
    public String getLeaderUid() { return leaderUid; }
    public String getLeaderCallsign() { return leaderCallsign; }
    public String getAssignedByUid() { return assignedByUid; }
    public String getAssignedByCallsign() { return assignedByCallsign; }
    public String getStatus() { return status; }
    public long getUpdatedAt() { return updatedAt; }
    public SearchGridStateStore.PlannedAreaRecord getArea() { return area; }

    public String getDisplaySummary() {
        return teamName + " | Leader: " + leaderCallsign + "\n"
                + areaSummary() + "\nStatus: " + status;
    }

    public String areaSummary() {
        if (area == null || area.shape.length() == 0)
            return "No area geometry";
        if ("CIRCLE".equals(area.shape))
            return "Circle radius "
                    + Math.round(area.radiusMeters / 100.0) / 10.0 + " km";
        return "Box " + Math.round(area.widthMeters / 100.0) / 10.0
                + " km x " + Math.round(area.heightMeters / 100.0) / 10.0
                + " km";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
