package com.atakmap.android.plugintemplate.runtime;

import android.content.Context;
import android.content.SharedPreferences;

import com.atakmap.coremap.log.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DittoCredentialStore {

    private static final String TAG = "SARtakDittoCredStore";
    private static final String PREFS_NAME = "sartak_ditto_credentials";
    private static final String KEY_SELECTED_ID = "selected_id";
    private static final String KEY_PROFILES_JSON = "profiles_json";

    private final SharedPreferences preferences;

    public DittoCredentialStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME,
                Context.MODE_PRIVATE);
    }

    public List<DittoCredentialProfile> getProfiles() {
        List<DittoCredentialProfile> profiles = new ArrayList<>();
        String json = preferences.getString(KEY_PROFILES_JSON, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                DittoCredentialProfile profile =
                        DittoCredentialProfile.fromJson(array.optJSONObject(i));
                if (profile != null && profile.isComplete())
                    profiles.add(profile);
            }
        } catch (JSONException exception) {
            Log.w(TAG, "Ignoring invalid Ditto credential profile store",
                    exception);
        }
        return profiles;
    }

    public DittoCredentialProfile getSelectedProfile() {
        String selectedId = getSelectedProfileId();
        for (DittoCredentialProfile profile : getProfiles()) {
            if (profile.getId().equals(selectedId))
                return profile;
        }
        List<DittoCredentialProfile> profiles = getProfiles();
        return profiles.isEmpty() ? null : profiles.get(0);
    }

    public String getSelectedProfileId() {
        return preferences.getString(KEY_SELECTED_ID, "");
    }

    public void selectProfile(String profileId) {
        preferences.edit().putString(KEY_SELECTED_ID, safe(profileId)).apply();
    }

    public void saveProfile(DittoCredentialProfile profile) {
        if (profile == null || !profile.isComplete())
            return;
        Map<String, DittoCredentialProfile> byId = new LinkedHashMap<>();
        for (DittoCredentialProfile existing : getProfiles())
            byId.put(existing.getId(), existing);
        byId.put(profile.getId(), profile);
        // Saving a profile no longer selects it (finding T1-6): HQ's next
        // Create Operation would otherwise quietly switch to whichever
        // profile was added or edited last. Only the first profile, or one
        // saved while the selection points at nothing, becomes selected.
        String selectedId = getSelectedProfileId();
        if (!byId.containsKey(selectedId))
            selectedId = profile.getId();
        persist(new ArrayList<>(byId.values()), selectedId);
    }

    /** What {@link #importFromOperation} did, for the toast. */
    public enum ImportResult {
        UNCHANGED, CREATED, TOKEN_UPDATED
    }

    /**
     * Records the credentials carried by a joined or switched-to operation
     * (finding T1-4). A new profile is created only when none has the same
     * database ID and auth URL. A matching profile keeps the label and type
     * the user gave it; only its token is replaced, and only when it differs.
     */
    public ImportResult importFromOperation(String label, String databaseId,
            String authUrl, String developmentToken) {
        String targetDatabaseId = safe(databaseId);
        String targetAuthUrl = safe(authUrl);
        for (DittoCredentialProfile existing : getProfiles()) {
            if (!existing.getDatabaseId().equals(targetDatabaseId)
                    || !existing.getAuthUrl().equals(targetAuthUrl))
                continue;
            if (existing.getDevelopmentToken().equals(safe(developmentToken)))
                return ImportResult.UNCHANGED;
            DittoCredentialProfile updated = existing.updated(
                    existing.getRawLabel(), existing.getConnectionType(),
                    targetDatabaseId, targetAuthUrl, developmentToken);
            if (!updated.isComplete())
                return ImportResult.UNCHANGED;
            saveProfile(updated);
            return ImportResult.TOKEN_UPDATED;
        }
        DittoCredentialProfile created = DittoCredentialProfile.create(label,
                DittoCredentialProfile.CONNECTION_SDK, targetDatabaseId,
                targetAuthUrl, developmentToken);
        if (!created.isComplete())
            return ImportResult.UNCHANGED;
        saveProfile(created);
        return ImportResult.CREATED;
    }

    public void removeProfile(String profileId) {
        String target = safe(profileId);
        List<DittoCredentialProfile> remaining = new ArrayList<>();
        for (DittoCredentialProfile profile : getProfiles()) {
            if (!profile.getId().equals(target))
                remaining.add(profile);
        }
        // Removing another profile keeps the current selection.
        String nextSelected = getSelectedProfileId();
        if (nextSelected.equals(target) || nextSelected.length() == 0)
            nextSelected = remaining.isEmpty() ? "" : remaining.get(0).getId();
        persist(remaining, nextSelected);
    }

    public void clear() {
        preferences.edit().remove(KEY_SELECTED_ID).remove(KEY_PROFILES_JSON)
                .apply();
    }

    private void persist(List<DittoCredentialProfile> profiles,
            String selectedId) {
        JSONArray array = new JSONArray();
        for (DittoCredentialProfile profile : profiles) {
            try {
                array.put(profile.toJson());
            } catch (JSONException exception) {
                Log.w(TAG, "Failed to save Ditto credential profile",
                        exception);
            }
        }
        preferences.edit().putString(KEY_PROFILES_JSON, array.toString())
                .putString(KEY_SELECTED_ID, safe(selectedId)).apply();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
