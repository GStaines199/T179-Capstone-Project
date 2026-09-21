package com.atakmap.android.plugintemplate.runtime;

import android.content.Context;
import android.content.SharedPreferences;

import com.atakmap.coremap.log.Log;

import org.json.JSONException;

public class OperationStateStore {

    private static final String TAG = "SARtakOperationStore";
    private static final String PREFS_NAME = "sartak_operation_state";
    private static final String KEY_ACTIVE_PROFILE = "active_profile_json";

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
            preferences.edit().putString(KEY_ACTIVE_PROFILE,
                    profile.toJson()).apply();
        } catch (JSONException exception) {
            Log.w(TAG, "Failed to save operation profile", exception);
        }
    }

    public void clear() {
        preferences.edit().remove(KEY_ACTIVE_PROFILE).apply();
    }
}
