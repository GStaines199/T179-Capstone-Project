package com.atakmap.android.plugintemplate.grid;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Remembers the search line this device is leading, so a leader whose ATAK
 * is closed, restarted or killed by Android comes back to the same line
 * instead of silently dropping it while the team is still searching.
 */
public class SearchLineStateStore {

    private static final String PREFS_NAME = "sartak_search_line_state";
    private static final String KEY_OPERATION = "operationId";
    private static final String KEY_TEAM = "teamId";
    private static final String KEY_STATE = "state";
    private static final String KEY_CELL = "cellId";
    private static final String KEY_NORTHING = "lineNorthing";
    private static final String KEY_STARTED = "startedAt";
    private static final String KEY_PAUSED = "pausedAt";
    private static final String KEY_COLOR = "color";
    private static final String KEY_TOLERANCE = "toleranceMeters";

    /** A leader-owned search line as it was last saved. */
    public static final class Record {
        public final String operationId;
        public final String teamId;
        public final SearchLineState state;
        public final String cellId;
        public final double lineNorthing;
        public final long startedAt;
        public final long pausedAt;
        public final SearchLineColorOption color;
        public final double toleranceMeters;

        public Record(String operationId, String teamId,
                SearchLineState state, String cellId, double lineNorthing,
                long startedAt, long pausedAt, SearchLineColorOption color,
                double toleranceMeters) {
            this.operationId = operationId == null ? "" : operationId;
            this.teamId = teamId == null ? "" : teamId;
            this.state = state;
            this.cellId = cellId == null ? "" : cellId;
            this.lineNorthing = lineNorthing;
            this.startedAt = startedAt;
            this.pausedAt = pausedAt;
            this.color = color;
            this.toleranceMeters = toleranceMeters;
        }

        boolean sameLineAs(Record other) {
            return other != null && operationId.equals(other.operationId)
                    && teamId.equals(other.teamId) && state == other.state
                    && cellId.equals(other.cellId) && color == other.color
                    && toleranceMeters == other.toleranceMeters
                    && startedAt == other.startedAt
                    && pausedAt == other.pausedAt;
        }
    }

    private static final long NORTHING_SAVE_INTERVAL_MS = 5000L;

    private final SharedPreferences preferences;
    private Record lastSaved;
    private long lastSavedAt;

    public SearchLineStateStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME,
                Context.MODE_PRIVATE);
    }

    /**
     * Saves the line. State changes are written straight away; the leader's
     * moving northing only every few seconds, since it changes constantly.
     */
    public void save(Record record) {
        if (record == null || record.state == null
                || record.state == SearchLineState.NOT_STARTED) {
            clear();
            return;
        }
        long now = System.currentTimeMillis();
        if (record.sameLineAs(lastSaved)
                && (lastSaved.lineNorthing == record.lineNorthing
                        || now - lastSavedAt < NORTHING_SAVE_INTERVAL_MS))
            return;
        preferences.edit()
                .putString(KEY_OPERATION, record.operationId)
                .putString(KEY_TEAM, record.teamId)
                .putString(KEY_STATE, record.state.name())
                .putString(KEY_CELL, record.cellId)
                .putString(KEY_NORTHING, Double.toString(record.lineNorthing))
                .putLong(KEY_STARTED, record.startedAt)
                .putLong(KEY_PAUSED, record.pausedAt)
                .putString(KEY_COLOR, record.color == null ? ""
                        : record.color.name())
                .putString(KEY_TOLERANCE,
                        Double.toString(record.toleranceMeters))
                .apply();
        lastSaved = record;
        lastSavedAt = now;
    }

    public Record load() {
        String stateName = preferences.getString(KEY_STATE, "");
        String cellId = preferences.getString(KEY_CELL, "");
        if (stateName == null || stateName.length() == 0 || cellId == null
                || cellId.length() == 0)
            return null;
        try {
            SearchLineState state = SearchLineState.valueOf(stateName);
            if (state == SearchLineState.NOT_STARTED)
                return null;
            SearchLineColorOption color = null;
            String colorName = preferences.getString(KEY_COLOR, "");
            if (colorName != null && colorName.length() > 0)
                color = SearchLineColorOption.valueOf(colorName);
            return new Record(preferences.getString(KEY_OPERATION, ""),
                    preferences.getString(KEY_TEAM, ""), state, cellId,
                    Double.parseDouble(preferences.getString(KEY_NORTHING,
                            "0")),
                    preferences.getLong(KEY_STARTED, 0L),
                    preferences.getLong(KEY_PAUSED, 0L), color,
                    Double.parseDouble(preferences.getString(KEY_TOLERANCE,
                            "10")));
        } catch (IllegalArgumentException ignored) {
            // Covers unknown enum names and malformed numbers alike.
            clear();
            return null;
        }
    }

    public void clear() {
        if (lastSaved == null && !preferences.contains(KEY_STATE))
            return;
        preferences.edit().clear().apply();
        lastSaved = null;
        lastSavedAt = 0L;
    }
}
