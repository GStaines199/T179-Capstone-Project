package com.atakmap.android.plugintemplate.grid;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.LinkedHashMap;
import java.util.Map;

public class SearchGridStateStore {

    private static final String PREFS_NAME = "sartak_search_grid_state";
    private static final String CELL_PREFIX = "cell.";
    private static final String AREA_PREFIX = "area.";

    private final SharedPreferences preferences;
    private String operationId = "";

    public SearchGridStateStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME,
                Context.MODE_PRIVATE);
    }

    public void setOperationId(String operationId) {
        this.operationId = sanitize(operationId);
    }

    public SearchGridStatus getStatus(String cellId) {
        if (!isOperationScoped() || sanitize(cellId).length() == 0)
            return SearchGridStatus.NOT_STARTED;
        String value = preferences.getString(keyForCell(cellId),
                SearchGridStatus.NOT_STARTED.name());
        try {
            return SearchGridStatus.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return SearchGridStatus.NOT_STARTED;
        }
    }

    public void setStatus(String cellId, SearchGridStatus status) {
        if (!isOperationScoped() || sanitize(cellId).length() == 0
                || status == null)
            return;
        preferences.edit().putString(keyForCell(cellId), status.name())
                .apply();
    }

    public void clearStatus(String cellId) {
        if (!isOperationScoped() || sanitize(cellId).length() == 0)
            return;
        preferences.edit().remove(keyForCell(cellId)).apply();
    }

    public Map<String, SearchGridStatus> getKnownStatuses() {
        Map<String, SearchGridStatus> statuses = new LinkedHashMap<>();
        if (!isOperationScoped())
            return statuses;
        String prefix = CELL_PREFIX + operationId + ".";
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(prefix) || !(entry.getValue() instanceof String))
                continue;
            try {
                SearchGridStatus status = SearchGridStatus.valueOf(
                        (String) entry.getValue());
                if (status != SearchGridStatus.NOT_STARTED)
                    statuses.put(key.substring(prefix.length()), status);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return statuses;
    }

    public void savePlannedArea(PlannedAreaRecord record) {
        if (!isOperationScoped() || record == null
                || sanitize(record.shape).length() == 0)
            return;
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(keyForArea("shape"), sanitize(record.shape));
        editor.putString(keyForArea("zone"), sanitize(record.zone));
        putDouble(editor, "centerEasting", record.centerEasting);
        putDouble(editor, "centerNorthing", record.centerNorthing);
        putDouble(editor, "west", record.west);
        putDouble(editor, "south", record.south);
        putDouble(editor, "east", record.east);
        putDouble(editor, "north", record.north);
        putDouble(editor, "radiusMeters", record.radiusMeters);
        putDouble(editor, "widthMeters", record.widthMeters);
        putDouble(editor, "heightMeters", record.heightMeters);
        editor.apply();
    }

    public PlannedAreaRecord loadPlannedArea() {
        if (!isOperationScoped())
            return null;
        String shape = preferences.getString(keyForArea("shape"), "");
        if (sanitize(shape).length() == 0 || "NONE".equals(shape))
            return null;
        return new PlannedAreaRecord(shape, preferences.getString(keyForArea(
                "zone"), ""), getDouble("centerEasting"),
                getDouble("centerNorthing"), getDouble("west"),
                getDouble("south"), getDouble("east"), getDouble("north"),
                getDouble("radiusMeters"), getDouble("widthMeters"),
                getDouble("heightMeters"));
    }

    public void clearPlannedArea() {
        if (!isOperationScoped())
            return;
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(keyForArea("shape"));
        editor.remove(keyForArea("zone"));
        editor.remove(keyForArea("centerEasting"));
        editor.remove(keyForArea("centerNorthing"));
        editor.remove(keyForArea("west"));
        editor.remove(keyForArea("south"));
        editor.remove(keyForArea("east"));
        editor.remove(keyForArea("north"));
        editor.remove(keyForArea("radiusMeters"));
        editor.remove(keyForArea("widthMeters"));
        editor.remove(keyForArea("heightMeters"));
        editor.apply();
    }

    private String keyForCell(String cellId) {
        return CELL_PREFIX + operationId + "." + sanitize(cellId);
    }

    private String keyForArea(String field) {
        return AREA_PREFIX + operationId + "." + field;
    }

    private void putDouble(SharedPreferences.Editor editor, String field,
            double value) {
        editor.putString(keyForArea(field), Double.toString(value));
    }

    private double getDouble(String field) {
        String value = preferences.getString(keyForArea(field), "0");
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private boolean isOperationScoped() {
        return operationId.length() > 0;
    }

    private String sanitize(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class PlannedAreaRecord {
        public final String shape;
        public final String zone;
        public final double centerEasting;
        public final double centerNorthing;
        public final double west;
        public final double south;
        public final double east;
        public final double north;
        public final double radiusMeters;
        public final double widthMeters;
        public final double heightMeters;

        public PlannedAreaRecord(String shape, String zone,
                double centerEasting, double centerNorthing, double west,
                double south, double east, double north, double radiusMeters,
                double widthMeters, double heightMeters) {
            this.shape = shape;
            this.zone = zone;
            this.centerEasting = centerEasting;
            this.centerNorthing = centerNorthing;
            this.west = west;
            this.south = south;
            this.east = east;
            this.north = north;
            this.radiusMeters = radiusMeters;
            this.widthMeters = widthMeters;
            this.heightMeters = heightMeters;
        }
    }
}
