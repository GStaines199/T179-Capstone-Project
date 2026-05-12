package com.atakmap.android.plugintemplate.runtime;

public class PluginHealthManager {

    public static final long LOCATION_STALE_MS = 30000L;

    private boolean started;
    private boolean storageReady;
    private boolean identityResolved;
    private boolean trackingActive;
    private long lastLocationTimestamp;
    private String identityMessage = "Identity pending";
    private String locationMessage = "Waiting for GPS";
    private String storageMessage = "Storage pending";

    public void start() {
        started = true;
    }

    public void stop() {
        started = false;
        trackingActive = false;
    }

    public void setStorageReady(boolean storageReady, String message) {
        this.storageReady = storageReady;
        this.storageMessage = message;
    }

    public void setIdentityResolved(boolean identityResolved, String message) {
        this.identityResolved = identityResolved;
        this.identityMessage = message;
    }

    public void setTrackingActive(boolean trackingActive) {
        this.trackingActive = trackingActive;
    }

    public void recordLocationSuccess(long timestamp, double accuracyMeters) {
        recordLocationSuccess(timestamp, accuracyMeters, "ATAK");
    }

    public void recordLocationSuccess(long timestamp, double accuracyMeters,
            String source) {
        lastLocationTimestamp = timestamp;
        locationMessage = "GPS active | Source " + source + " | Accuracy "
                + Math.round(accuracyMeters) + " m";
    }

    public void recordLocationFailure(String message) {
        lastLocationTimestamp = 0L;
        locationMessage = message;
    }

    public boolean isLocationActive() {
        return hasFreshLocation()
                && locationMessage != null
                && locationMessage.startsWith("GPS active");
    }

    public PluginHealthState getState() {
        if (!started || !storageReady)
            return PluginHealthState.INACTIVE;
        if (!identityResolved || !trackingActive || !hasFreshLocation())
            return PluginHealthState.DEGRADED;
        return PluginHealthState.ACTIVE;
    }

    public String getSummary() {
        return "SARtak " + getState().name() + "\n"
                + identityMessage + "\n"
                + locationMessage + "\n"
                + storageMessage;
    }

    public String getLocationMessage() {
        return locationMessage;
    }

    private boolean hasFreshLocation() {
        return lastLocationTimestamp > 0L
                && System.currentTimeMillis() - lastLocationTimestamp
                        <= LOCATION_STALE_MS;
    }
}
