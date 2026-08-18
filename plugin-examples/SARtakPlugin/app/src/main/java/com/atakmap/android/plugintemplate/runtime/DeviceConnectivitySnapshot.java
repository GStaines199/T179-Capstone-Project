package com.atakmap.android.plugintemplate.runtime;

public class DeviceConnectivitySnapshot {

    private final String uid;
    private final String callsign;
    private final String connectionSummary;
    private final String lastUpdateSummary;
    private final String teamSummary;
    private final String role;
    private final String atakGroupName;
    private final boolean self;

    public DeviceConnectivitySnapshot(String uid, String callsign,
            String connectionSummary, String lastUpdateSummary,
            String teamSummary, String role, String atakGroupName,
            boolean self) {
        this.uid = safe(uid);
        this.callsign = safe(callsign);
        this.connectionSummary = safe(connectionSummary);
        this.lastUpdateSummary = safe(lastUpdateSummary);
        this.teamSummary = safe(teamSummary);
        this.role = safe(role);
        this.atakGroupName = safe(atakGroupName);
        this.self = self;
    }

    public String getUid() {
        return uid;
    }

    public String getCallsign() {
        return callsign;
    }

    public String getConnectionSummary() {
        return connectionSummary;
    }

    public String getLastUpdateSummary() {
        return lastUpdateSummary;
    }

    public String getTeamSummary() {
        return teamSummary;
    }

    public String getRole() {
        return role;
    }

    public String getAtakGroupName() {
        return atakGroupName;
    }

    public boolean isSelf() {
        return self;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
