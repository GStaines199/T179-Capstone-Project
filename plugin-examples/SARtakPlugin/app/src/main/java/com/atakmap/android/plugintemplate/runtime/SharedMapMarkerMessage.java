package com.atakmap.android.plugintemplate.runtime;

public class SharedMapMarkerMessage {

    private final String uid;
    private final String markerUid;
    private final String markerType;
    private final String title;
    private final double latitude;
    private final double longitude;
    private final double altitude;
    private final String senderUid;
    private final String senderCallsign;
    private final String teamId;
    private final String operationId;
    private final long updatedAt;

    public SharedMapMarkerMessage(String uid, String markerUid,
            String markerType, String title, double latitude, double longitude,
            double altitude, String senderUid, String senderCallsign,
            String teamId, String operationId, long updatedAt) {
        this.uid = safe(uid);
        this.markerUid = safe(markerUid);
        this.markerType = safe(markerType);
        this.title = safe(title);
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.senderUid = safe(senderUid);
        this.senderCallsign = safe(senderCallsign);
        this.teamId = safe(teamId);
        this.operationId = safe(operationId);
        this.updatedAt = updatedAt;
    }

    public String getUid() { return uid; }
    public String getMarkerUid() { return markerUid; }
    public String getMarkerType() { return markerType; }
    public String getTitle() { return title; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public double getAltitude() { return altitude; }
    public String getSenderUid() { return senderUid; }
    public String getSenderCallsign() { return senderCallsign; }
    public String getTeamId() { return teamId; }
    public String getOperationId() { return operationId; }
    public long getUpdatedAt() { return updatedAt; }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
