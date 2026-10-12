package com.atakmap.android.plugintemplate.grid;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Remembers the search direction a lead chose for a team, whether or not a
 * line is running.
 *
 * <p>The direction used to be saved only as part of a running line, so a
 * lead who set East, didn't start a line and restarted ATAK came back to
 * North. Auto Route follows the direction, so the next route then ran in
 * columns instead of rows without anyone noticing.
 */
public class SearchDirectionStore {

    private static final String PREFS_NAME = "sartak_search_direction";

    private final SharedPreferences preferences;

    public SearchDirectionStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME,
                Context.MODE_PRIVATE);
    }

    public void save(String operationId, String teamId,
            SearchLineDirection direction) {
        String key = key(operationId, teamId);
        if (key == null || direction == null)
            return;
        if (direction.name().equals(preferences.getString(key, null)))
            return;
        preferences.edit().putString(key, direction.name()).apply();
    }

    /** The saved direction for this team, or null if none was chosen. */
    public SearchLineDirection load(String operationId, String teamId) {
        String key = key(operationId, teamId);
        if (key == null)
            return null;
        String name = preferences.getString(key, null);
        if (name == null || name.length() == 0)
            return null;
        try {
            return SearchLineDirection.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static String key(String operationId, String teamId) {
        if (operationId == null || operationId.trim().length() == 0
                || teamId == null || teamId.trim().length() == 0)
            return null;
        return operationId.trim() + "|" + teamId.trim();
    }
}
