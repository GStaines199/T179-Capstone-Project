package com.atakmap.android.plugintemplate.grid;

import android.content.Context;
import android.content.SharedPreferences;

public class SearchGridStateStore {

    private static final String PREFS_NAME = "sartak_search_grid_state";
    private static final String CELL_PREFIX = "cell.";

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

    private String keyForCell(String cellId) {
        return CELL_PREFIX + operationId + "." + sanitize(cellId);
    }

    private boolean isOperationScoped() {
        return operationId.length() > 0;
    }

    private String sanitize(String value) {
        return value == null ? "" : value.trim();
    }
}
