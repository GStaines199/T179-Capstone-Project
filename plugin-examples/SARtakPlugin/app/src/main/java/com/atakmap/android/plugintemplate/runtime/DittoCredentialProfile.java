package com.atakmap.android.plugintemplate.runtime;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class DittoCredentialProfile {

    public static final String CONNECTION_SDK = "SDK";
    public static final String CONNECTION_HTTP = "HTTP";

    private final String id;
    private final String label;
    private final String connectionType;
    private final String databaseId;
    private final String authUrl;
    private final String developmentToken;
    private final long createdAt;
    private final long updatedAt;

    public DittoCredentialProfile(String id, String label, String databaseId,
            String authUrl, String developmentToken, long createdAt,
            long updatedAt) {
        this(id, label, CONNECTION_SDK, databaseId, authUrl, developmentToken,
                createdAt, updatedAt);
    }

    public DittoCredentialProfile(String id, String label,
            String connectionType, String databaseId, String authUrl,
            String developmentToken, long createdAt, long updatedAt) {
        this.id = safe(id).length() == 0 ? UUID.randomUUID().toString()
                : safe(id);
        this.label = safe(label).length() == 0 ? "Ditto Profile"
                : safe(label);
        this.connectionType = normalizeConnectionType(connectionType);
        this.databaseId = safe(databaseId);
        this.authUrl = safe(authUrl);
        this.developmentToken = safe(developmentToken);
        this.createdAt = createdAt <= 0L ? System.currentTimeMillis()
                : createdAt;
        this.updatedAt = updatedAt <= 0L ? this.createdAt : updatedAt;
    }

    public static DittoCredentialProfile create(String label,
            String databaseId, String authUrl, String developmentToken) {
        return create(label, CONNECTION_SDK, databaseId, authUrl,
                developmentToken);
    }

    public static DittoCredentialProfile create(String label,
            String connectionType, String databaseId, String authUrl,
            String developmentToken) {
        long now = System.currentTimeMillis();
        return new DittoCredentialProfile(UUID.randomUUID().toString(), label,
                connectionType, databaseId, authUrl, developmentToken, now,
                now);
    }

    public DittoCredentialProfile updated(String label, String databaseId,
            String authUrl, String developmentToken) {
        return updated(label, connectionType, databaseId, authUrl,
                developmentToken);
    }

    public DittoCredentialProfile updated(String label, String connectionType,
            String databaseId, String authUrl, String developmentToken) {
        return new DittoCredentialProfile(id, label, connectionType,
                databaseId, authUrl, developmentToken, createdAt,
                System.currentTimeMillis());
    }

    public static DittoCredentialProfile fromJson(JSONObject object) {
        if (object == null)
            return null;
        return new DittoCredentialProfile(object.optString("id", ""),
                object.optString("label", ""),
                object.optString("connectionType", CONNECTION_SDK),
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
        object.put("connectionType", connectionType);
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

    public boolean isSdkProfile() {
        return CONNECTION_SDK.equals(connectionType);
    }

    public boolean isHttpProfile() {
        return CONNECTION_HTTP.equals(connectionType);
    }

    public String getSummary() {
        String endpointLabel = isHttpProfile() ? "HTTP URL" : "Auth URL";
        return label + "\nConnection: " + getConnectionTypeLabel()
                + "\nDatabase: " + shortValue(databaseId)
                + "\n" + endpointLabel + ": " + authUrl + "\nToken: "
                + (developmentToken.length() == 0 ? "missing" : "saved");
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label + " (" + getConnectionTypeLabel() + ")";
    }

    public String getRawLabel() {
        return label;
    }

    public String getConnectionType() {
        return connectionType;
    }

    public String getConnectionTypeLabel() {
        return isHttpProfile() ? "HTTP" : "SDK";
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

    private static String normalizeConnectionType(String value) {
        String safe = safe(value).toUpperCase();
        return CONNECTION_HTTP.equals(safe) ? CONNECTION_HTTP
                : CONNECTION_SDK;
    }
}
