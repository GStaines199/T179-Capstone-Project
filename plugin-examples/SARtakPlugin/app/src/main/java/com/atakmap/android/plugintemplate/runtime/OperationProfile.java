package com.atakmap.android.plugintemplate.runtime;

import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.Charset;
import java.util.Locale;

public class OperationProfile {

    public static final int SCHEMA_VERSION = 1;
    public static final String SYNC_DITTO_DEVELOPMENT =
            "DITTO_DEVELOPMENT";

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

    public OperationProfile(String operationId, String operationName,
            String syncMode, String dittoDatabaseId, String dittoAuthUrl,
            String dittoDevelopmentToken, String createdByUid,
            String createdByCallsign, long createdAt, long expiresAt) {
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
    }

    public static OperationProfile create(String operationName,
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
                now + 14L * 24L * 60L * 60L * 1000L);
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
                object.optLong("expiresAt", 0L));
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
        return operationName + "\nID: " + operationId
                + "\nSync: " + (hasDittoCredentials()
                        ? "Ditto operation profile ready"
                        : "Ditto operation profile incomplete");
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

    public long getExpiresAt() {
        return expiresAt;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
