package com.atakmap.android.plugintemplate.grid;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Remembers which SARtak map overlays were switched on, so they come back
 * the same after ATAK is closed, restarted or killed by Android.
 */
public class OverlayVisibilityStore {

    private static final String PREFS_NAME = "sartak_overlay_visibility";
    private static final String KEY_GRID = "grid";
    private static final String KEY_GRID_LABELS = "gridLabels";
    private static final String KEY_ROUTE = "route";
    private static final String KEY_TEAM_AREAS = "teamAreas";

    private final SharedPreferences preferences;
    private String lastSaved;

    public OverlayVisibilityStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME,
                Context.MODE_PRIVATE);
    }

    public boolean isGridVisible() {
        return preferences.getBoolean(KEY_GRID, false);
    }

    public boolean isGridLabelsVisible() {
        return preferences.getBoolean(KEY_GRID_LABELS, false);
    }

    public boolean isRouteVisible() {
        return preferences.getBoolean(KEY_ROUTE, true);
    }

    public boolean isTeamAreasVisible() {
        return preferences.getBoolean(KEY_TEAM_AREAS, false);
    }

    /** Cheap to call on every render; only writes when something changed. */
    public void saveIfChanged(boolean grid, boolean gridLabels, boolean route,
            boolean teamAreas) {
        String state = grid + ":" + gridLabels + ":" + route + ":"
                + teamAreas;
        if (state.equals(lastSaved))
            return;
        lastSaved = state;
        preferences.edit()
                .putBoolean(KEY_GRID, grid)
                .putBoolean(KEY_GRID_LABELS, gridLabels)
                .putBoolean(KEY_ROUTE, route)
                .putBoolean(KEY_TEAM_AREAS, teamAreas)
                .apply();
    }
}
