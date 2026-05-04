package com.atakmap.android.plugintemplate.grid;

public enum TeamMarkerVisibilityMode {
    ME_ONLY("Me Only"),
    MY_TEAM("My Team"),
    LEADERS("Leaders"),
    ALL_VISIBLE("All Visible");

    private final String label;

    TeamMarkerVisibilityMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
