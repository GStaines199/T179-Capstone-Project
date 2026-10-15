package com.atakmap.android.plugintemplate.runtime;

public class DeviceConnectivitySnapshot {

    private final String uid;
    private final String callsign;
    private final String connectionSummary;
    private final String lastUpdateSummary;
    private final String teamSummary;
    private final String role;
    private final String sartakRole;
    private final String atakGroupName;
    private final boolean self;

    public DeviceConnectivitySnapshot(String uid, String callsign,
            String connectionSummary, String lastUpdateSummary,
            String teamSummary, String role, String sartakRole,
            String atakGroupName, boolean self) {
        this.uid = safe(uid);
        this.callsign = safe(callsign);
        this.connectionSummary = safe(connectionSummary);
        this.lastUpdateSummary = safe(lastUpdateSummary);
        this.teamSummary = safe(teamSummary);
        this.role = safe(role);
        this.sartakRole = safe(sartakRole);
        this.atakGroupName = safe(atakGroupName);
        this.self = self;
    }

    private static final String UNASSIGNED = "Unassigned";

    /**
     * The map group to show for a device seen over both ATAK and Ditto.
     *
     * <p>A peer's ATAK contact often carries no group, which reads as
     * "Unassigned". That used to win over the SARtak team the peer reports
     * through Ditto, because only an empty ATAK value was replaced, so the
     * Devices tab called a teammate "Unassigned".
     */
    public static String preferredMapGroup(String atakGroup,
            String dittoGroup) {
        String atak = safe(atakGroup);
        String sartak = safe(dittoGroup);
        if (atak.length() == 0)
            return sartak;
        if (UNASSIGNED.equalsIgnoreCase(atak) && sartak.length() > 0
                && !UNASSIGNED.equalsIgnoreCase(sartak))
            return sartak;
        return atak;
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

    public String getSartakRole() {
        return sartakRole;
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
