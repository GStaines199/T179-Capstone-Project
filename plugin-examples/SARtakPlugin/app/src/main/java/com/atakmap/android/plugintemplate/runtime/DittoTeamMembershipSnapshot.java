package com.atakmap.android.plugintemplate.runtime;

import org.json.JSONException;
import org.json.JSONObject;

public class DittoTeamMembershipSnapshot {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_LEFT = "LEFT";
    public static final String STATUS_REMOVED = "REMOVED";

    private final String teamId;
    private final String operationId;
    private final String teamName;
    private final String leaderUid;
    private final String leaderCallsign;
    private final String memberUid;
    private final String memberCallsign;
    private final String status;
    private final String role;
    private final String updatedByUid;
    private final String updatedByCallsign;
    private final long updatedAt;

    public DittoTeamMembershipSnapshot(String teamId, String teamName,
            String leaderUid, String leaderCallsign, String memberUid,
            String memberCallsign, String status, String role,
            String updatedByUid, String updatedByCallsign, long updatedAt) {
        this("", teamId, teamName, leaderUid, leaderCallsign, memberUid,
                memberCallsign, status, role, updatedByUid,
                updatedByCallsign, updatedAt);
    }

    public DittoTeamMembershipSnapshot(String operationId, String teamId,
            String teamName, String leaderUid, String leaderCallsign,
            String memberUid, String memberCallsign, String status,
            String role, String updatedByUid, String updatedByCallsign,
            long updatedAt) {
        this.operationId = safe(operationId);
        this.teamId = safe(teamId);
        this.teamName = safe(teamName);
        this.leaderUid = safe(leaderUid);
        this.leaderCallsign = safe(leaderCallsign);
        this.memberUid = safe(memberUid);
        this.memberCallsign = safe(memberCallsign);
        this.status = safe(status);
        this.role = safe(role);
        this.updatedByUid = safe(updatedByUid);
        this.updatedByCallsign = safe(updatedByCallsign);
        this.updatedAt = updatedAt;
    }

    public static DittoTeamMembershipSnapshot fromJson(String json)
            throws JSONException {
        JSONObject object = new JSONObject(json);
        return new DittoTeamMembershipSnapshot(
                object.optString("operationId", ""),
                object.optString("teamId", ""),
                object.optString("teamName", ""),
                object.optString("leaderUid", ""),
                object.optString("leaderCallsign", ""),
                object.optString("memberUid", ""),
                object.optString("memberCallsign", ""),
                object.optString("status", ""),
                object.optString("role", ""),
                object.optString("updatedByUid", ""),
                object.optString("updatedByCallsign", ""),
                object.optLong("updatedAt", 0L));
    }

    public String getTeamId() {
        return teamId;
    }

    public String getOperationId() {
        return operationId;
    }

    public String getTeamName() {
        return teamName;
    }

    public String getLeaderUid() {
        return leaderUid;
    }

    public String getLeaderCallsign() {
        return leaderCallsign;
    }

    public String getMemberUid() {
        return memberUid;
    }

    public String getMemberCallsign() {
        return memberCallsign;
    }

    public String getStatus() {
        return status;
    }

    public String getRole() {
        return role;
    }

    public String getUpdatedByUid() {
        return updatedByUid;
    }

    public String getUpdatedByCallsign() {
        return updatedByCallsign;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    public boolean isLeftOrRemoved() {
        return STATUS_LEFT.equals(status) || STATUS_REMOVED.equals(status);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
