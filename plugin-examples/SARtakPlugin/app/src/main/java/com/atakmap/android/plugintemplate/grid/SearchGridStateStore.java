package com.atakmap.android.plugintemplate.grid;

import android.content.Context;
import android.content.SharedPreferences;

public class SearchGridStateStore {

    private static final String PREFS_NAME = "sartak_search_grid_state";
    private static final String CELL_PREFIX = "cell.";

    private final SharedPreferences preferences;

    public SearchGridStateStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME,
                Context.MODE_PRIVATE);
    }

    public SearchGridStatus getStatus(String cellId) {
        String value = preferences.getString(CELL_PREFIX + cellId,
                SearchGridStatus.NOT_STARTED.name());
        try {
            return SearchGridStatus.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return SearchGridStatus.NOT_STARTED;
        }
    }

    public void setStatus(String cellId, SearchGridStatus status) {
        preferences.edit().putString(CELL_PREFIX + cellId, status.name())
                .apply();
    }

    public void clearStatus(String cellId) {
        preferences.edit().remove(CELL_PREFIX + cellId).apply();
    }
}
