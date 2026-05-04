package com.atakmap.android.plugintemplate.grid;

public class SearchTeamMember {

    public enum MembershipStatus {
        ACTIVE_MEMBER,
        EXCLUDED_FROM_LANES,
        REMOVED
    }

    public enum ConnectionStatus {
        CONNECTED,
        STALE,
        DISCONNECTED,
        RECONNECTING
    }

    private final String uniqueId;
    private final String callsign;
    private final boolean teamLeader;
    private final String colorName;
    private MembershipStatus membershipStatus;
    private ConnectionStatus connectionStatus;
    private String gpsCoordinates;
    private String altitude;
    private String currentGridCell;
    private String lastPing;

    public SearchTeamMember(String uniqueId, String callsign,
            boolean teamLeader, String colorName,
            MembershipStatus membershipStatus,
            ConnectionStatus connectionStatus, String gpsCoordinates,
            String altitude, String currentGridCell, String lastPing) {
        this.uniqueId = uniqueId;
        this.callsign = callsign;
        this.teamLeader = teamLeader;
        this.colorName = colorName;
        this.membershipStatus = membershipStatus;
        this.connectionStatus = connectionStatus;
        this.gpsCoordinates = gpsCoordinates;
        this.altitude = altitude;
        this.currentGridCell = currentGridCell;
        this.lastPing = lastPing;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public String getCallsign() {
        return callsign;
    }

    public boolean isTeamLeader() {
        return teamLeader;
    }

    public String getColorName() {
        return colorName;
    }

    public MembershipStatus getMembershipStatus() {
        return membershipStatus;
    }

    public void setMembershipStatus(MembershipStatus membershipStatus) {
        this.membershipStatus = membershipStatus;
    }

    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    public String getGpsCoordinates() {
        return gpsCoordinates;
    }

    public String getAltitude() {
        return altitude;
    }

    public String getCurrentGridCell() {
        return currentGridCell;
    }

    public String getLastPing() {
        return lastPing;
    }

    public boolean contributesLane() {
        return !teamLeader
                && membershipStatus == MembershipStatus.ACTIVE_MEMBER;
    }

    public boolean needsConnectionAlert() {
        return membershipStatus == MembershipStatus.ACTIVE_MEMBER
                && connectionStatus != ConnectionStatus.CONNECTED;
    }
}
