package com.atakmap.android.plugintemplate.runtime;

/**
 * Health states the plugin reports to the operator. GPS loss is kept separate
 * from {@link #DEGRADED} so a searcher can tell "your position is no longer
 * being logged" apart from "the plugin is running but not fully set up".
 */
public enum PluginHealthState {

    /** Identity resolved, tracking on, and a fresh GPS fix is being logged. */
    ACTIVE("Active"),

    /** Running, but identity is unresolved or track recording is stopped. */
    DEGRADED("Degraded"),

    /**
     * Set up and tracking, but ATAK has supplied no usable fix inside the
     * staleness window. No position is recorded while in this state.
     */
    GPS_LOST("GPS Lost"),

    /** Not started, or local storage is unavailable. */
    INACTIVE("Inactive");

    private final String label;

    PluginHealthState(String label) {
        this.label = label;
    }

    /** Operator-facing name, used in the status panel. */
    public String getLabel() {
        return label;
    }
}
