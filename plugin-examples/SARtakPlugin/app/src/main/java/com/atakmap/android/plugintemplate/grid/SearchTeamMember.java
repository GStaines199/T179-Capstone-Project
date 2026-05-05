package com.atakmap.android.plugintemplate.grid;

public class SearchTeamMember {

    public enum TeamRole {
        TEAM_LEADER,
        SEARCHER
    }

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
    private final TeamRole role;
    private final String colorName;
    private final int displayColor;
    private double latitude;
    private double longitude;
    private double headingDegrees;
    private MembershipStatus membershipStatus;
    private ConnectionStatus connectionStatus;
    private int laneNumber;
    private String gpsCoordinates;
    private String altitude;
    private String currentGridCell;
    private String lastPing;
    private String distanceFromYou;
    private String distanceFromSearchLine;

    public SearchTeamMember(String uniqueId, String callsign, TeamRole role,
            String colorName, int displayColor,
            MembershipStatus membershipStatus,
            ConnectionStatus connectionStatus, int laneNumber, double latitude,
            double longitude, double headingDegrees, String gpsCoordinates,
            String altitude, String currentGridCell, String lastPing,
            String distanceFromYou, String distanceFromSearchLine) {
        this.uniqueId = uniqueId;
        this.callsign = callsign;
        this.role = role;
        this.colorName = colorName;
        this.displayColor = displayColor;
        this.latitude = latitude;
        this.longitude = longitude;
        this.headingDegrees = headingDegrees;
        this.membershipStatus = membershipStatus;
        this.connectionStatus = connectionStatus;
        this.laneNumber = laneNumber;
        this.gpsCoordinates = gpsCoordinates;
        this.altitude = altitude;
        this.currentGridCell = currentGridCell;
        this.lastPing = lastPing;
        this.distanceFromYou = distanceFromYou;
        this.distanceFromSearchLine = distanceFromSearchLine;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public String getCallsign() {
        return callsign;
    }

    public TeamRole getRole() {
        return role;
    }

    public boolean isTeamLeader() {
        return role == TeamRole.TEAM_LEADER;
    }

    public String getColorName() {
        return colorName;
    }

    public int getDisplayColor() {
        return displayColor;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getHeadingDegrees() {
        return headingDegrees;
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

    public int getLaneNumber() {
        return laneNumber;
    }

    public void setLaneNumber(int laneNumber) {
        this.laneNumber = laneNumber;
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

    public String getDistanceFromYou() {
        return distanceFromYou;
    }

    public String getDistanceFromSearchLine() {
        return distanceFromSearchLine;
    }

    public void updatePosition(double latitude, double longitude,
            double headingDegrees,
            String gpsCoordinates, String currentGridCell,
            String distanceFromYou, String distanceFromSearchLine) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.headingDegrees = headingDegrees;
        this.gpsCoordinates = gpsCoordinates;
        this.currentGridCell = currentGridCell;
        this.distanceFromYou = distanceFromYou;
        this.distanceFromSearchLine = distanceFromSearchLine;
    }

    public String getRoleLabel() {
        return isTeamLeader() ? "Team Leader" : "Searcher";
    }

    public String getLaneLabel() {
        if (!contributesLane())
            return "Not assigned";
        return isTeamLeader() ? "Lane " + laneNumber + " (Leader)"
                : "Lane " + laneNumber;
    }

    public boolean contributesLane() {
        return membershipStatus == MembershipStatus.ACTIVE_MEMBER;
    }

    public boolean needsConnectionAlert() {
        return membershipStatus == MembershipStatus.ACTIVE_MEMBER
                && connectionStatus != ConnectionStatus.CONNECTED;
    }
}
