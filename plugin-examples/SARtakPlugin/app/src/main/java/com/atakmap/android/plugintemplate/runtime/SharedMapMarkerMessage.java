package com.atakmap.android.plugintemplate.runtime;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * One shared ATAK map marker as stored in the {@code sartak_shared_markers}
 * Ditto collection.
 *
 * <p>{@code senderUid}/{@code senderCallsign}/{@code updatedAt} describe the
 * last edit. {@code creatorUid}/{@code creatorCallsign}/{@code createdAt} keep
 * the original author and creation time from ATAK's marker metadata, so they
 * survive edits by other devices. A deleted marker is a tombstone: the
 * {@code deleted*} fields are only ever written by a deletion, never by an
 * ordinary update, so Ditto's field-level merge keeps the tombstone even when
 * an offline device later publishes an edit of the same marker.
 */
public class SharedMapMarkerMessage {

    public static final String DEFAULT_TYPE = "b-m-p-s-p-i";

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
    private final String remarks;
    private final boolean hasColor;
    private final int color;
    private final String iconsetPath;
    private final String creatorUid;
    private final String creatorCallsign;
    private final long createdAt;
    private final boolean deleted;
    private final long deletedAt;
    private final String deletedByUid;
    private final String deletedByCallsign;

    public SharedMapMarkerMessage(String uid, String markerUid,
            String markerType, String title, double latitude, double longitude,
            double altitude, String senderUid, String senderCallsign,
            String teamId, String operationId, long updatedAt) {
        this(new Builder().uid(uid).markerUid(markerUid)
                .markerType(markerType).title(title)
                .position(latitude, longitude, altitude)
                .sender(senderUid, senderCallsign).teamId(teamId)
                .operationId(operationId).updatedAt(updatedAt));
    }

    private SharedMapMarkerMessage(Builder builder) {
        this.uid = safe(builder.uid);
        this.markerUid = safe(builder.markerUid);
        this.markerType = safe(builder.markerType);
        this.title = safe(builder.title);
        this.latitude = builder.latitude;
        this.longitude = builder.longitude;
        this.altitude = builder.altitude;
        this.senderUid = safe(builder.senderUid);
        this.senderCallsign = safe(builder.senderCallsign);
        this.teamId = safe(builder.teamId);
        this.operationId = safe(builder.operationId);
        this.updatedAt = builder.updatedAt;
        this.remarks = builder.remarks == null ? "" : builder.remarks;
        this.hasColor = builder.hasColor;
        this.color = builder.color;
        this.iconsetPath = safe(builder.iconsetPath);
        this.creatorUid = safe(builder.creatorUid);
        this.creatorCallsign = safe(builder.creatorCallsign);
        this.createdAt = Math.max(0L, builder.createdAt);
        this.deleted = builder.deleted;
        this.deletedAt = Math.max(0L, builder.deletedAt);
        this.deletedByUid = safe(builder.deletedByUid);
        this.deletedByCallsign = safe(builder.deletedByCallsign);
    }

    public static String documentId(String operationId, String markerUid) {
        return "shared-marker-" + safe(operationId) + "-" + safe(markerUid);
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
    public String getRemarks() { return remarks; }
    public boolean hasColor() { return hasColor; }
    public int getColor() { return color; }
    public String getIconsetPath() { return iconsetPath; }
    public String getCreatorUid() { return creatorUid; }
    public String getCreatorCallsign() { return creatorCallsign; }
    public long getCreatedAt() { return createdAt; }
    public boolean isDeleted() { return deleted; }
    public long getDeletedAt() { return deletedAt; }
    public String getDeletedByUid() { return deletedByUid; }
    public String getDeletedByCallsign() { return deletedByCallsign; }

    public boolean hasValidPosition() {
        return isFinite(latitude) && isFinite(longitude)
                && latitude >= -90.0 && latitude <= 90.0
                && longitude >= -180.0 && longitude <= 180.0;
    }

    /**
     * What a user can see and edit on the marker. Altitude is left out on
     * purpose: ATAK re-derives it from terrain when a marker is restored, and
     * a 2D marker must not look "edited" just because ATAK restarted.
     */
    public String contentSignature() {
        return markerType + "|" + title + "|" + rounded(latitude) + "|"
                + rounded(longitude) + "|" + remarks.trim() + "|"
                + (hasColor ? Integer.toHexString(color) : "-") + "|"
                + iconsetPath;
    }

    /** Content plus the edit that produced it, to detect new documents. */
    public String versionSignature() {
        return contentSignature() + "|" + senderUid + "|" + updatedAt + "|"
                + deleted;
    }

    /**
     * Fields written by an ordinary create or edit. Never contains the
     * {@code deleted*} fields, so it cannot undo a tombstone.
     */
    public Map<String, Object> toDocument(String operationId) {
        Map<String, Object> document = new HashMap<>();
        document.put("_id", documentId(operationId, markerUid));
        document.put("operationId", safe(operationId));
        document.put("uid", uid);
        document.put("markerUid", markerUid);
        document.put("markerType", markerType);
        document.put("title", title);
        document.put("latitude", latitude);
        document.put("longitude", longitude);
        document.put("altitude", altitude);
        document.put("senderUid", senderUid);
        document.put("senderCallsign", senderCallsign);
        document.put("teamId", teamId);
        document.put("updatedAt", updatedAt);
        document.put("remarks", remarks);
        document.put("hasColor", hasColor);
        document.put("color", color);
        document.put("iconsetPath", iconsetPath);
        document.put("creatorUid", creatorUid);
        document.put("creatorCallsign", creatorCallsign);
        document.put("createdAt", createdAt);
        return document;
    }

    /** Fields written by a deletion: the tombstone and nothing else. */
    public Map<String, Object> toDeletionDocument(String operationId) {
        Map<String, Object> document = new HashMap<>();
        document.put("_id", documentId(operationId, markerUid));
        document.put("operationId", safe(operationId));
        document.put("markerUid", markerUid);
        document.put("deleted", true);
        document.put("deletedAt", deletedAt);
        document.put("deletedByUid", deletedByUid);
        document.put("deletedByCallsign", deletedByCallsign);
        return document;
    }

    public static SharedMapMarkerMessage fromJson(JSONObject object) {
        return new Builder()
                .uid(object.optString("uid", ""))
                .markerUid(object.optString("markerUid", ""))
                .markerType(object.optString("markerType", ""))
                .title(object.optString("title", ""))
                .position(object.optDouble("latitude", Double.NaN),
                        object.optDouble("longitude", Double.NaN),
                        object.optDouble("altitude", 0.0))
                .sender(object.optString("senderUid", ""),
                        object.optString("senderCallsign", ""))
                .teamId(object.optString("teamId", ""))
                .operationId(object.optString("operationId", ""))
                .updatedAt(object.optLong("updatedAt", 0L))
                .remarks(object.optString("remarks", ""))
                .color(object.optBoolean("hasColor", false),
                        object.optInt("color", 0))
                .iconsetPath(object.optString("iconsetPath", ""))
                .creator(object.optString("creatorUid", ""),
                        object.optString("creatorCallsign", ""),
                        object.optLong("createdAt", 0L))
                .deleted(object.optBoolean("deleted", false),
                        object.optLong("deletedAt", 0L),
                        object.optString("deletedByUid", ""),
                        object.optString("deletedByCallsign", ""))
                .build();
    }

    public Builder toBuilder() {
        return new Builder().uid(uid).markerUid(markerUid)
                .markerType(markerType).title(title)
                .position(latitude, longitude, altitude)
                .sender(senderUid, senderCallsign).teamId(teamId)
                .operationId(operationId).updatedAt(updatedAt)
                .remarks(remarks).color(hasColor, color)
                .iconsetPath(iconsetPath)
                .creator(creatorUid, creatorCallsign, createdAt)
                .deleted(deleted, deletedAt, deletedByUid, deletedByCallsign);
    }

    private static String rounded(double value) {
        return String.format(Locale.US, "%.6f", value);
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static class Builder {
        private String uid;
        private String markerUid;
        private String markerType;
        private String title;
        private double latitude = Double.NaN;
        private double longitude = Double.NaN;
        private double altitude;
        private String senderUid;
        private String senderCallsign;
        private String teamId;
        private String operationId;
        private long updatedAt;
        private String remarks;
        private boolean hasColor;
        private int color;
        private String iconsetPath;
        private String creatorUid;
        private String creatorCallsign;
        private long createdAt;
        private boolean deleted;
        private long deletedAt;
        private String deletedByUid;
        private String deletedByCallsign;

        public Builder uid(String value) { uid = value; return this; }
        public Builder markerUid(String value) { markerUid = value; return this; }
        public Builder markerType(String value) { markerType = value; return this; }
        public Builder title(String value) { title = value; return this; }
        public Builder teamId(String value) { teamId = value; return this; }
        public Builder operationId(String value) { operationId = value; return this; }
        public Builder updatedAt(long value) { updatedAt = value; return this; }
        public Builder remarks(String value) { remarks = value; return this; }
        public Builder iconsetPath(String value) { iconsetPath = value; return this; }

        public Builder position(double lat, double lon, double alt) {
            latitude = lat;
            longitude = lon;
            altitude = Double.isNaN(alt) || Double.isInfinite(alt) ? 0.0
                    : alt;
            return this;
        }

        public Builder sender(String senderUid, String senderCallsign) {
            this.senderUid = senderUid;
            this.senderCallsign = senderCallsign;
            return this;
        }

        public Builder color(boolean hasColor, int color) {
            this.hasColor = hasColor;
            this.color = hasColor ? color : 0;
            return this;
        }

        public Builder creator(String uid, String callsign, long createdAt) {
            this.creatorUid = uid;
            this.creatorCallsign = callsign;
            this.createdAt = createdAt;
            return this;
        }

        public Builder deleted(boolean deleted, long deletedAt, String byUid,
                String byCallsign) {
            this.deleted = deleted;
            this.deletedAt = deletedAt;
            this.deletedByUid = byUid;
            this.deletedByCallsign = byCallsign;
            return this;
        }

        public SharedMapMarkerMessage build() {
            return new SharedMapMarkerMessage(this);
        }
    }
}
