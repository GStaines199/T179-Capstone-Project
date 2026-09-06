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
    private TeamRole role;
    private String colorName;
    private int displayColor;
    private String teamColorName = "White";
    private int teamColorArgb = 0xFFFFFFFF;
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
    private boolean liveAtakContact;
    private String atakGroupName = "Ungrouped ATAK";
    private boolean headingReliable;
    private double speedMetersPerSecond;
    private long lastPresenceTimestamp;

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

    public void setRole(TeamRole role) {
        this.role = role;
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

    public String getTeamColorName() {
        return teamColorName;
    }

    public int getTeamColorArgb() {
        return teamColorArgb;
    }

    public void setMarkerStyle(String teamColorName, int teamColorArgb,
            String memberColorName, int memberColorArgb) {
        if (teamColorArgb != 0)
            this.teamColorArgb = teamColorArgb;
        if (teamColorName != null && teamColorName.trim().length() > 0)
            this.teamColorName = teamColorName.trim();
        if (memberColorArgb != 0)
            this.displayColor = memberColorArgb;
        if (memberColorName != null && memberColorName.trim().length() > 0)
            this.colorName = memberColorName.trim();
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

    public boolean hasReliableHeading() {
        return headingReliable && speedMetersPerSecond > 0.4;
    }

    public double getSpeedMetersPerSecond() {
        return speedMetersPerSecond;
    }

    public long getLastPresenceTimestamp() {
        return lastPresenceTimestamp;
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

    public void setConnectionStatus(ConnectionStatus connectionStatus) {
        this.connectionStatus = connectionStatus;
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

    public boolean hasLiveAtakContact() {
        return liveAtakContact;
    }

    public String getAtakGroupName() {
        return atakGroupName;
    }

    public void setAtakGroupName(String atakGroupName) {
        this.atakGroupName = atakGroupName == null
                || atakGroupName.trim().length() == 0
                        ? "Ungrouped ATAK" : atakGroupName.trim();
    }

    public void setLiveAtakContact(boolean liveAtakContact) {
        this.liveAtakContact = liveAtakContact;
    }

    public void updatePosition(double latitude, double longitude,
            double headingDegrees,
            String gpsCoordinates, String currentGridCell,
            String distanceFromYou, String distanceFromSearchLine) {
        updatePosition(latitude, longitude, headingDegrees, gpsCoordinates,
                this.altitude, currentGridCell, this.lastPing,
                distanceFromYou, distanceFromSearchLine);
    }

    public void updatePosition(double latitude, double longitude,
            double headingDegrees, String gpsCoordinates, String altitude,
            String currentGridCell, String lastPing, String distanceFromYou,
            String distanceFromSearchLine) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.headingDegrees = headingDegrees;
        this.gpsCoordinates = gpsCoordinates;
        this.altitude = altitude;
        this.currentGridCell = currentGridCell;
        this.lastPing = lastPing;
        this.distanceFromYou = distanceFromYou;
        this.distanceFromSearchLine = distanceFromSearchLine;
        this.liveAtakContact = true;
    }

    public void updateMovement(double headingDegrees,
            boolean headingReliable, double speedMetersPerSecond) {
        this.headingDegrees = headingDegrees;
        this.headingReliable = headingReliable;
        this.speedMetersPerSecond = speedMetersPerSecond;
    }

    public void updateMapPosition(double latitude, double longitude,
            double headingDegrees, String currentGridCell,
            String distanceFromYou, String distanceFromSearchLine) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.headingDegrees = headingDegrees;
        this.currentGridCell = currentGridCell;
        this.distanceFromYou = distanceFromYou;
        this.distanceFromSearchLine = distanceFromSearchLine;
    }

    public void markLocationUnavailable(String message) {
        gpsCoordinates = message;
        altitude = message;
        currentGridCell = message;
        lastPing = message;
        distanceFromYou = message;
        distanceFromSearchLine = message;
        liveAtakContact = false;
        headingReliable = false;
        speedMetersPerSecond = 0.0;
    }

    public void markPresenceSeen(String lastPingMessage) {
        connectionStatus = ConnectionStatus.CONNECTED;
        lastPing = lastPingMessage;
        lastPresenceTimestamp = System.currentTimeMillis();
        if (!liveAtakContact) {
            gpsCoordinates = "ATAK contact pending";
            altitude = "ATAK contact pending";
            currentGridCell = "ATAK contact pending";
            distanceFromYou = "ATAK contact pending";
            distanceFromSearchLine = "ATAK contact pending";
        }
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

    /**
     * Whether this member is rostered onto a lane of the search line.
     *
     * <p>Deliberately a membership question only. It stays true while a
     * member's GPS is down: they are still assigned to walk their lane, we
     * simply do not know where they are. Do not add a position check here --
     * lane count divides the cell into lanes, so a member dropping out on a
     * lost fix would re-shape the search line for everybody else. Ask
     * {@link MemberPositionPolicy#hasUsablePosition(SearchTeamMember)} instead
     * before doing anything with the coordinates.
     */
    public boolean contributesLane() {
        return membershipStatus == MembershipStatus.ACTIVE_MEMBER;
    }

    public boolean needsConnectionAlert() {
        return membershipStatus == MembershipStatus.ACTIVE_MEMBER
                && connectionStatus != ConnectionStatus.CONNECTED;
    }
}
