package com.atakmap.android.plugintemplate.runtime;

import android.content.Context;
import android.content.SharedPreferences;

import com.atakmap.coremap.log.Log;

import org.json.JSONException;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OperationStateStore {

    private static final String TAG = "SARtakOperationStore";
    private static final String PREFS_NAME = "sartak_operation_state";
    private static final String KEY_ACTIVE_PROFILE = "active_profile_json";
    private static final String KEY_SAVED_PROFILES = "saved_profiles_json";

    private final SharedPreferences preferences;

    public OperationStateStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME,
                Context.MODE_PRIVATE);
    }

    public OperationProfile load() {
        String json = preferences.getString(KEY_ACTIVE_PROFILE, "");
        if (json == null || json.trim().length() == 0)
            return null;
        try {
            return OperationProfile.fromJson(json);
        } catch (JSONException exception) {
            Log.w(TAG, "Ignoring invalid stored operation profile", exception);
            return null;
        }
    }

    public void save(OperationProfile profile) {
        if (profile == null) {
            clear();
            return;
        }
        try {
            List<OperationProfile> profiles = loadSavedProfiles();
            Map<String, OperationProfile> byId = new LinkedHashMap<>();
            for (OperationProfile saved : profiles)
                byId.put(saved.getOperationId(), saved);
            byId.put(profile.getOperationId(), profile);
            preferences.edit()
                    .putString(KEY_ACTIVE_PROFILE, profile.toJson())
                    .putString(KEY_SAVED_PROFILES,
                            encodeProfiles(new ArrayList<>(byId.values())))
                    .apply();
        } catch (JSONException exception) {
            Log.w(TAG, "Failed to save operation profile", exception);
        }
    }

    public void clear() {
        preferences.edit().remove(KEY_ACTIVE_PROFILE).apply();
    }

    public List<OperationProfile> loadSavedProfiles() {
        String json = preferences.getString(KEY_SAVED_PROFILES, "");
        Map<String, OperationProfile> byId = new LinkedHashMap<>();
        if (json != null && json.trim().length() > 0) {
            try {
                JSONArray array = new JSONArray(json);
                for (int index = 0; index < array.length(); index++) {
                    OperationProfile profile = OperationProfile.fromJson(
                            array.getString(index));
                    if (profile.getOperationId().length() > 0)
                        byId.put(profile.getOperationId(), profile);
                }
            } catch (JSONException exception) {
                Log.w(TAG, "Ignoring invalid saved operation catalogue",
                        exception);
            }
        }
        OperationProfile active = load();
        if (active != null && active.getOperationId().length() > 0)
            byId.put(active.getOperationId(), active);
        List<OperationProfile> profiles = new ArrayList<>(byId.values());
        Collections.sort(profiles, new Comparator<OperationProfile>() {
            @Override
            public int compare(OperationProfile first,
                    OperationProfile second) {
                return Long.compare(second.getCreatedAt(), first.getCreatedAt());
            }
        });
        return profiles;
    }

    private String encodeProfiles(List<OperationProfile> profiles)
            throws JSONException {
        JSONArray array = new JSONArray();
        for (OperationProfile profile : profiles)
            array.put(profile.toJson());
        return array.toString();
    }
}
