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
        persist(new ArrayList<>(byId.values()), profile.getId());
    }

    public void removeProfile(String profileId) {
        String target = safe(profileId);
        List<DittoCredentialProfile> remaining = new ArrayList<>();
        for (DittoCredentialProfile profile : getProfiles()) {
            if (!profile.getId().equals(target))
                remaining.add(profile);
        }
        String nextSelected = remaining.isEmpty() ? "" : remaining.get(0)
                .getId();
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
