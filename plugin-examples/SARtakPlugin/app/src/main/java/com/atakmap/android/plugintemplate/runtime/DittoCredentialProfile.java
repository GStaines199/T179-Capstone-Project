package com.atakmap.android.plugintemplate.runtime;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class DittoCredentialProfile {

    private final String id;
    private final String label;
    private final String databaseId;
    private final String authUrl;
    private final String developmentToken;
    private final long createdAt;
    private final long updatedAt;

    public DittoCredentialProfile(String id, String label, String databaseId,
            String authUrl, String developmentToken, long createdAt,
            long updatedAt) {
        this.id = safe(id).length() == 0 ? UUID.randomUUID().toString()
                : safe(id);
        this.label = safe(label).length() == 0 ? "Ditto Profile"
                : safe(label);
        this.databaseId = safe(databaseId);
        this.authUrl = safe(authUrl);
        this.developmentToken = safe(developmentToken);
        this.createdAt = createdAt <= 0L ? System.currentTimeMillis()
                : createdAt;
        this.updatedAt = updatedAt <= 0L ? this.createdAt : updatedAt;
    }

    public static DittoCredentialProfile create(String label,
            String databaseId, String authUrl, String developmentToken) {
        long now = System.currentTimeMillis();
        return new DittoCredentialProfile(UUID.randomUUID().toString(), label,
                databaseId, authUrl, developmentToken, now, now);
    }

    public DittoCredentialProfile updated(String label, String databaseId,
            String authUrl, String developmentToken) {
        return new DittoCredentialProfile(id, label, databaseId, authUrl,
                developmentToken, createdAt, System.currentTimeMillis());
    }

    public static DittoCredentialProfile fromJson(JSONObject object) {
        if (object == null)
            return null;
        return new DittoCredentialProfile(object.optString("id", ""),
                object.optString("label", ""),
                object.optString("databaseId", ""),
                object.optString("authUrl", ""),
                object.optString("developmentToken", ""),
                object.optLong("createdAt", 0L),
                object.optLong("updatedAt", 0L));
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("label", label);
        object.put("databaseId", databaseId);
        object.put("authUrl", authUrl);
        object.put("developmentToken", developmentToken);
        object.put("createdAt", createdAt);
        object.put("updatedAt", updatedAt);
        return object;
    }

    public boolean isComplete() {
        return databaseId.length() > 0 && authUrl.length() > 0
                && developmentToken.length() > 0;
    }

    public String getSummary() {
        return label + "\nDatabase: " + shortValue(databaseId)
                + "\nAuth URL: " + authUrl + "\nToken: "
                + (developmentToken.length() == 0 ? "missing" : "saved");
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getDatabaseId() {
        return databaseId;
    }

    public String getAuthUrl() {
        return authUrl;
    }

    public String getDevelopmentToken() {
        return developmentToken;
    }

    private String shortValue(String value) {
        String safe = safe(value);
        if (safe.length() <= 12)
            return safe.length() == 0 ? "missing" : safe;
        return safe.substring(0, 8) + "...";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
