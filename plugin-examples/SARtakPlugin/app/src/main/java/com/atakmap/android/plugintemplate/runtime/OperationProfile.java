package com.atakmap.android.plugintemplate.runtime;

import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.Charset;
import java.util.Locale;

public class OperationProfile {

    public static final int SCHEMA_VERSION = 2;
    public static final String SYNC_DITTO_DEVELOPMENT =
            "DITTO_DEVELOPMENT";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    private final String operationId;
    private final String operationName;
    private final String syncMode;
    private final String dittoDatabaseId;
    private final String dittoAuthUrl;
    private final String dittoDevelopmentToken;
    private final String createdByUid;
    private final String createdByCallsign;
    private final long createdAt;
    private final long expiresAt;
    private final String status;
    private final String incidentNumber;
    private final String searchType;
    private final String stagingArea;
    private final String priority;
    private final String briefingNotes;
    private final long plannedStartAt;
    private final long plannedEndAt;
    private final long archivedAt;

    public OperationProfile(String operationId, String operationName,
            String syncMode, String dittoDatabaseId, String dittoAuthUrl,
            String dittoDevelopmentToken, String createdByUid,
            String createdByCallsign, long createdAt, long expiresAt) {
        this(operationId, operationName, syncMode, dittoDatabaseId,
                dittoAuthUrl, dittoDevelopmentToken, createdByUid,
                createdByCallsign, createdAt, expiresAt, STATUS_ACTIVE, "",
                "", "", "Normal", "", createdAt, 0L, 0L);
    }

    public OperationProfile(String operationId, String operationName,
            String syncMode, String dittoDatabaseId, String dittoAuthUrl,
            String dittoDevelopmentToken, String createdByUid,
            String createdByCallsign, long createdAt, long expiresAt,
            String status, String incidentNumber, String searchType,
            String stagingArea, String priority, String briefingNotes,
            long plannedStartAt, long plannedEndAt, long archivedAt) {
        this.operationId = safe(operationId);
        this.operationName = safe(operationName);
        this.syncMode = safe(syncMode);
        this.dittoDatabaseId = safe(dittoDatabaseId);
        this.dittoAuthUrl = safe(dittoAuthUrl);
        this.dittoDevelopmentToken = safe(dittoDevelopmentToken);
        this.createdByUid = safe(createdByUid);
        this.createdByCallsign = safe(createdByCallsign);
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = normalizeStatus(status);
        this.incidentNumber = safe(incidentNumber);
        this.searchType = safe(searchType);
        this.stagingArea = safe(stagingArea);
        this.priority = safe(priority).length() == 0 ? "Normal"
                : safe(priority);
        this.briefingNotes = safe(briefingNotes);
        this.plannedStartAt = plannedStartAt;
        this.plannedEndAt = plannedEndAt;
        this.archivedAt = archivedAt;
    }

    public static OperationProfile create(String operationName,
            IdentityManager.Identity identity, String dittoDatabaseId,
            String dittoAuthUrl, String dittoDevelopmentToken) {
        return create(operationName, "", "", "", "Normal", "", 0L, identity,
                dittoDatabaseId, dittoAuthUrl, dittoDevelopmentToken);
    }

    public static OperationProfile create(String operationName,
            String incidentNumber, String searchType, String stagingArea,
            String priority, String briefingNotes, long plannedEndAt,
            IdentityManager.Identity identity, String dittoDatabaseId,
            String dittoAuthUrl, String dittoDevelopmentToken) {
        long now = System.currentTimeMillis();
        String uid = identity == null ? "" : identity.getUid();
        String callsign = identity == null ? "" : identity.getCallsign();
        String idSeed = uid.length() > 0 ? uid : callsign;
        String suffix = idSeed.replaceAll("[^A-Za-z0-9]", "")
                .toUpperCase(Locale.US);
        if (suffix.length() > 10)
            suffix = suffix.substring(suffix.length() - 10);
        if (suffix.length() == 0)
            suffix = "DEVICE";
        return new OperationProfile("OP-" + suffix + "-" + now,
                operationName == null || operationName.trim().length() == 0
                        ? "SAR Operation" : operationName.trim(),
                SYNC_DITTO_DEVELOPMENT, dittoDatabaseId, dittoAuthUrl,
                dittoDevelopmentToken, uid, callsign, now,
                now + 14L * 24L * 60L * 60L * 1000L, STATUS_ACTIVE,
                incidentNumber, searchType, stagingArea, priority,
                briefingNotes, now, plannedEndAt, 0L);
    }

    public static OperationProfile fromJson(String json) throws JSONException {
        JSONObject object = new JSONObject(json);
        return new OperationProfile(
                object.optString("operationId", ""),
                object.optString("operationName", ""),
                object.optString("syncMode", SYNC_DITTO_DEVELOPMENT),
                object.optString("dittoDatabaseId", ""),
                object.optString("dittoAuthUrl", ""),
                object.optString("dittoDevelopmentToken", ""),
                object.optString("createdByUid", ""),
                object.optString("createdByCallsign", ""),
                object.optLong("createdAt", 0L),
                object.optLong("expiresAt", 0L),
                object.optString("status", STATUS_ACTIVE),
                object.optString("incidentNumber", ""),
                object.optString("searchType", ""),
                object.optString("stagingArea", ""),
                object.optString("priority", "Normal"),
                object.optString("briefingNotes", ""),
                object.optLong("plannedStartAt", object.optLong("createdAt",
                        0L)),
                object.optLong("plannedEndAt", 0L),
                object.optLong("archivedAt", 0L));
    }

    public static OperationProfile fromJoinCode(String joinCode)
            throws JSONException {
        String value = safe(joinCode);
        if (value.startsWith("SARTAK-OP1:"))
            value = value.substring("SARTAK-OP1:".length());
        String json = new String(Base64.decode(value, Base64.DEFAULT),
                Charset.forName("UTF-8"));
        OperationProfile profile = fromJson(json);
        if (profile.getOperationId().length() == 0
                || !profile.hasDittoCredentials())
            throw new JSONException("Operation join code is incomplete");
        return profile;
    }

    public String toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("schemaVersion", SCHEMA_VERSION);
        object.put("operationId", operationId);
        object.put("operationName", operationName);
        object.put("syncMode", syncMode);
        object.put("dittoDatabaseId", dittoDatabaseId);
        object.put("dittoAuthUrl", dittoAuthUrl);
        object.put("dittoDevelopmentToken", dittoDevelopmentToken);
        object.put("createdByUid", createdByUid);
        object.put("createdByCallsign", createdByCallsign);
        object.put("createdAt", createdAt);
        object.put("expiresAt", expiresAt);
        object.put("status", status);
        object.put("incidentNumber", incidentNumber);
        object.put("searchType", searchType);
        object.put("stagingArea", stagingArea);
        object.put("priority", priority);
        object.put("briefingNotes", briefingNotes);
        object.put("plannedStartAt", plannedStartAt);
        object.put("plannedEndAt", plannedEndAt);
        object.put("archivedAt", archivedAt);
        return object.toString();
    }

    public String toJoinCode() throws JSONException {
        return "SARTAK-OP1:" + Base64.encodeToString(toJson().getBytes(
                Charset.forName("UTF-8")), Base64.NO_WRAP);
    }

    public boolean hasDittoCredentials() {
        return operationId.length() > 0
                && dittoDatabaseId.length() > 0
                && dittoAuthUrl.length() > 0
                && dittoDevelopmentToken.length() > 0;
    }

    public String getSummary() {
        if (operationId.length() == 0)
            return "No active operation selected";
        StringBuilder builder = new StringBuilder();
        builder.append(operationName).append('\n');
        builder.append("Status: ").append(status).append('\n');
        if (incidentNumber.length() > 0)
            builder.append("Incident: ").append(incidentNumber).append('\n');
        if (searchType.length() > 0)
            builder.append("Type: ").append(searchType).append('\n');
        if (priority.length() > 0)
            builder.append("Priority: ").append(priority).append('\n');
        if (stagingArea.length() > 0)
            builder.append("Staging: ").append(stagingArea).append('\n');
        if (briefingNotes.length() > 0)
            builder.append("Notes: ").append(briefingNotes).append('\n');
        builder.append("ID: ").append(operationId).append('\n');
        builder.append("Sync: ").append(hasDittoCredentials()
                ? "Ditto operation profile ready"
                : "Ditto operation profile incomplete");
        return builder.toString();
    }

    public OperationProfile archivedCopy(long archivedAt) {
        return new OperationProfile(operationId, operationName, syncMode,
                dittoDatabaseId, dittoAuthUrl, dittoDevelopmentToken,
                createdByUid, createdByCallsign, createdAt, expiresAt,
                STATUS_ARCHIVED, incidentNumber, searchType, stagingArea,
                priority, briefingNotes, plannedStartAt, plannedEndAt,
                archivedAt);
    }

    public String getOperationId() {
        return operationId;
    }

    public String getOperationName() {
        return operationName;
    }

    public String getSyncMode() {
        return syncMode;
    }

    public String getDittoDatabaseId() {
        return dittoDatabaseId;
    }

    public String getDittoAuthUrl() {
        return dittoAuthUrl;
    }

    public String getDittoDevelopmentToken() {
        return dittoDevelopmentToken;
    }

    public String getCreatedByCallsign() {
        return createdByCallsign;
    }

    public String getCreatedByUid() {
        return createdByUid;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public String getStatus() {
        return status;
    }

    public boolean isArchived() {
        return STATUS_ARCHIVED.equals(status);
    }

    public String getIncidentNumber() {
        return incidentNumber;
    }

    public String getSearchType() {
        return searchType;
    }

    public String getStagingArea() {
        return stagingArea;
    }

    public String getPriority() {
        return priority;
    }

    public String getBriefingNotes() {
        return briefingNotes;
    }

    public long getPlannedStartAt() {
        return plannedStartAt;
    }

    public long getPlannedEndAt() {
        return plannedEndAt;
    }

    public long getArchivedAt() {
        return archivedAt;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeStatus(String value) {
        String normalized = safe(value).toUpperCase(Locale.US);
        return STATUS_ARCHIVED.equals(normalized) ? STATUS_ARCHIVED
                : STATUS_ACTIVE;
    }
}
