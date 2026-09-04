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

    /**
     * Whether the panel's GPS field may be shown as working.
     *
     * <p>Answered from the health model, never from
     * {@link #getLocationMessage()}. This used to prefix-match "GPS active" on
     * that message -- prose assembled for a human, which any rewording would
     * have silently pinned to red. The match was redundant as well as fragile:
     * only {@link #recordLocationSuccess} sets a fix timestamp and only
     * {@link #recordLocationFailure} clears it, so freshness already carried
     * the whole answer.
     *
     * <p><b>Deliberately not {@code getState() == ACTIVE}.</b> DEGRADED merges
     * two unrelated causes. One is an unresolved identity, where the capture
     * loop clears the fix and there is genuinely nothing to report. The other
     * is a recording the operator paused, where the receiver is working
     * normally and the message printed beside this indicator still reads "GPS
     * active" -- colouring that red would contradict the text next to it and
     * blame the receiver for a deliberate act. Ruling out INACTIVE is enough:
     * a storage failure stops {@code initialiseRuntime} before the capture loop
     * starts, so it can never leave a fresh fix behind.
     */
    public boolean isLocationActive() {
        return getState() != PluginHealthState.INACTIVE && hasFreshLocation();
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

    /**
     * Why storage is or is not usable. Set from the startup probe rather than
     * asserted, so this carries a real reason when the state is INACTIVE.
     */
    public String getStorageMessage() {
        return storageMessage;
    }

    private boolean hasFreshLocation() {
        return lastLocationTimestamp > 0L
                && clock.now() - lastLocationTimestamp <= LOCATION_STALE_MS;
    }
}
