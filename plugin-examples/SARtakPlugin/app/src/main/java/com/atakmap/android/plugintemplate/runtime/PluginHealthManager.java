package com.atakmap.android.plugintemplate.runtime;

public class PluginHealthManager {

    public static final long LOCATION_STALE_MS = 30000L;

    /**
     * Source of the current time. Exists so the staleness boundary can be
     * driven exactly in tests; production always reads the wall clock.
     */
    interface Clock {
        long now();
    }

    private static final Clock SYSTEM_CLOCK = new Clock() {
        @Override
        public long now() {
            return System.currentTimeMillis();
        }
    };

    private final Clock clock;

    private boolean started;
    private boolean storageReady;
    private boolean identityResolved;
    private boolean trackingActive;
    private long lastLocationTimestamp;
    private String identityMessage = "Identity pending";
    private String locationMessage = "Waiting for GPS";
    private String storageMessage = "Storage pending";

    public PluginHealthManager() {
        this(SYSTEM_CLOCK);
    }

    PluginHealthManager(Clock clock) {
        this.clock = clock;
    }

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

    /**
     * Current health, worst cause first. Setup problems outrank GPS loss: when
     * identity cannot be resolved the capture loop clears the last fix too, and
     * reporting that as GPS_LOST would blame the receiver for a problem that is
     * not the receiver's.
     */
    public PluginHealthState getState() {
        if (!started || !storageReady)
            return PluginHealthState.INACTIVE;
        if (!identityResolved || !trackingActive)
            return PluginHealthState.DEGRADED;
        if (!hasFreshLocation())
            return PluginHealthState.GPS_LOST;
        return PluginHealthState.ACTIVE;
    }

    public String getSummary() {
        return "SARtak " + getState().getLabel() + "\n"
                + identityMessage + "\n"
                + locationMessage + "\n"
                + storageMessage;
    }

    public String getLocationMessage() {
        return locationMessage;
    }

    private boolean hasFreshLocation() {
        return lastLocationTimestamp > 0L
                && clock.now() - lastLocationTimestamp <= LOCATION_STALE_MS;
    }
}
