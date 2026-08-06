package com.atakmap.android.plugintemplate.runtime;

import org.json.JSONException;
import org.json.JSONObject;

public class DittoDeviceSnapshot {

    private final String uid;
    private final String callsign;
    private final String teamId;
    private final String teamName;
    private final String leaderUid;
    private final String leaderCallsign;
    private final String role;
    private final String teamColorName;
    private final int teamColorArgb;
    private final String memberColorName;
    private final int memberColorArgb;
    private final boolean hasLocation;
    private final double latitude;
    private final double longitude;
    private final double altitude;
    private final double accuracy;
    private final double heading;
    private final double speed;
    private final boolean headingReliable;
    private final String gridCellId;
    private final long updatedAt;

    private DittoDeviceSnapshot(String uid, String callsign, String teamId,
            String teamName, String leaderUid, String leaderCallsign,
            String role, String teamColorName, int teamColorArgb,
            String memberColorName, int memberColorArgb, boolean hasLocation,
            double latitude, double longitude, double altitude,
            double accuracy, double heading, double speed,
            boolean headingReliable, String gridCellId, long updatedAt) {
        this.uid = uid;
        this.callsign = callsign;
        this.teamId = teamId;
        this.teamName = teamName;
        this.leaderUid = leaderUid;
        this.leaderCallsign = leaderCallsign;
        this.role = role;
        this.teamColorName = teamColorName;
        this.teamColorArgb = teamColorArgb;
        this.memberColorName = memberColorName;
        this.memberColorArgb = memberColorArgb;
        this.hasLocation = hasLocation;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.accuracy = accuracy;
        this.heading = heading;
        this.speed = speed;
        this.headingReliable = headingReliable;
        this.gridCellId = gridCellId;
        this.updatedAt = updatedAt;
    }

    public static DittoDeviceSnapshot fromJson(String json)
            throws JSONException {
        JSONObject object = new JSONObject(json);
        return new DittoDeviceSnapshot(
                object.optString("uid", ""),
                object.optString("callsign", ""),
                object.optString("teamId", ""),
                object.optString("teamName", ""),
                object.optString("leaderUid", ""),
                object.optString("leaderCallsign", ""),
                object.optString("role", ""),
                object.optString("teamColorName", ""),
                object.optInt("teamColorArgb", 0),
                object.optString("memberColorName", ""),
                object.optInt("memberColorArgb", 0),
                object.optBoolean("hasLocation", false),
                object.optDouble("latitude", 0.0),
                object.optDouble("longitude", 0.0),
                object.optDouble("altitude", 0.0),
                object.optDouble("accuracy", 0.0),
                object.optDouble("heading", 0.0),
                object.optDouble("speed", 0.0),
                object.optBoolean("headingReliable", false),
                object.optString("gridCellId", ""),
                object.optLong("updatedAt", 0L));
    }

    public String getUid() {
        return uid;
    }

    public String getCallsign() {
        return callsign;
    }

    public String getTeamId() {
        return teamId;
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

    public String getRole() {
        return role;
    }

    public String getTeamColorName() {
        return teamColorName;
    }

    public int getTeamColorArgb() {
        return teamColorArgb;
    }

    public String getMemberColorName() {
        return memberColorName;
    }

    public int getMemberColorArgb() {
        return memberColorArgb;
    }

    public boolean hasLocation() {
        return hasLocation;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getAltitude() {
        return altitude;
    }

    public double getAccuracy() {
        return accuracy;
    }

    public double getHeading() {
        return heading;
    }

    public double getSpeed() {
        return speed;
    }

    public boolean isHeadingReliable() {
        return headingReliable;
    }

    public String getGridCellId() {
        return gridCellId;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }
}
