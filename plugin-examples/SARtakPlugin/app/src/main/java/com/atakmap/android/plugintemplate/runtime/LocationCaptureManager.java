package com.atakmap.android.plugintemplate.runtime;

import android.os.Handler;
import android.os.Looper;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.plugintemplate.grid.SearchTrackManager;
import com.atakmap.coremap.maps.coords.GeoPoint;

public class LocationCaptureManager {

    public interface Listener {
        void onLocationCaptured();
    }

    public static final long UPDATE_INTERVAL_MS = 10000L;

    /**
     * A GPS fix, or the reason there isn't one. Free of ATAK types so the rule
     * that no position is written without a fix can be unit tested; the ATAK
     * plumbing lives in {@link #readFix()}.
     */
    static final class Fix {
        private final boolean available;
        private final String message;
        private final double latitude;
        private final double longitude;
        private final double altitude;
        private final double accuracy;
        private final double bearing;
        private final double speed;
        private final long timestamp;
        private final String source;

        private Fix(boolean available, String message, double latitude,
                double longitude, double altitude, double accuracy,
                double bearing, double speed, long timestamp, String source) {
            this.available = available;
            this.message = message;
            this.latitude = latitude;
            this.longitude = longitude;
            this.altitude = altitude;
            this.accuracy = accuracy;
            this.bearing = bearing;
            this.speed = speed;
            this.timestamp = timestamp;
            this.source = source;
        }

        static Fix unavailable(String message) {
            return new Fix(false, message, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0L,
                    "");
        }

        static Fix available(double latitude, double longitude, double altitude,
                double accuracy, double bearing, double speed, long timestamp,
                String source) {
            return new Fix(true, "", latitude, longitude, altitude, accuracy,
                    bearing, speed, timestamp, source);
        }

        boolean isAvailable() {
            return available;
        }

        String getMessage() {
            return message;
        }

        double getLatitude() {
            return latitude;
        }

        double getLongitude() {
            return longitude;
        }

        double getAltitude() {
            return altitude;
        }

        double getAccuracy() {
            return accuracy;
        }

        double getBearing() {
            return bearing;
        }

        double getSpeed() {
            return speed;
        }

        long getTimestamp() {
            return timestamp;
        }

        String getSource() {
            return source;
        }
    }

    private final MapView mapView;
    private final IdentityManager identityManager;
    private final SearchTrackManager trackManager;
    private final PluginHealthManager healthManager;
    private final Listener listener;
    private Handler handler;
    private boolean running;

    private final Runnable captureRunnable = new Runnable() {
        @Override
        public void run() {
            captureNow();
            if (running)
                handler.postDelayed(this, UPDATE_INTERVAL_MS);
        }
    };

    public LocationCaptureManager(MapView mapView,
            IdentityManager identityManager, SearchTrackManager trackManager,
            PluginHealthManager healthManager, Listener listener) {
        this.mapView = mapView;
        this.identityManager = identityManager;
        this.trackManager = trackManager;
        this.healthManager = healthManager;
        this.listener = listener;
    }

    public void start() {
        if (running)
            return;
        running = true;
        if (handler == null)
            handler = new Handler(Looper.getMainLooper());
        captureNow();
        handler.postDelayed(captureRunnable, UPDATE_INTERVAL_MS);
    }

    public void stop() {
        running = false;
        if (handler != null)
            handler.removeCallbacks(captureRunnable);
    }

    public void captureNow() {
        IdentityManager.Identity identity = identityManager.resolveIdentity();
        applyCapture(identity.isResolved(), identity.getMessage(),
                identity.getUid(), identity.getCallsign(),
                identity.isResolved() ? readFix() : null);
    }

    /**
     * Applies one capture cycle. A position is written only when the identity
     * resolved <i>and</i> ATAK supplied a usable fix; every other path records
     * the failure and writes nothing, so a lost signal can never be filled in
     * with a stale or inferred position.
     */
    void applyCapture(boolean identityResolved, String identityMessage,
            String uid, String callsign, Fix fix) {
        healthManager.setIdentityResolved(identityResolved, identityMessage);
        if (!identityResolved) {
            healthManager.recordLocationFailure(
                    "Identity unavailable; tracking degraded");
            notifyListener();
            return;
        }

        if (fix == null || !fix.isAvailable()) {
            healthManager.recordLocationFailure(
                    fix == null ? "No GPS Signal" : fix.getMessage());
            notifyListener();
            return;
        }

        trackManager.recordLocation(uid, callsign, fix.getLatitude(),
                fix.getLongitude(), fix.getAltitude(), fix.getAccuracy(),
                fix.getBearing(), fix.getSpeed(), fix.getTimestamp());
        healthManager.setTrackingActive(trackManager.isRecording());
        healthManager.recordLocationSuccess(fix.getTimestamp(),
                fix.getAccuracy(), fix.getSource());
        notifyListener();
    }

    /** Reads ATAK's current fix. Returns an unavailable Fix rather than null. */
    private Fix readFix() {
        AtakLocationStatus.Snapshot snapshot = AtakLocationStatus.from(mapView);
        if (!snapshot.isAvailable())
            return Fix.unavailable(snapshot.getMessage());

        GeoPoint point = snapshot.getPoint();
        if (point == null || !point.isValid())
            return Fix.unavailable("No GPS Signal");

        Marker self = mapView.getSelfMarker();
        return Fix.available(point.getLatitude(), point.getLongitude(),
                point.getAltitude(), point.getCE(), self.getTrackHeading(),
                self.getTrackSpeed(), snapshot.getTimestamp(),
                snapshot.getSource());
    }

    private void notifyListener() {
        if (listener != null)
            listener.onLocationCaptured();
    }
}
