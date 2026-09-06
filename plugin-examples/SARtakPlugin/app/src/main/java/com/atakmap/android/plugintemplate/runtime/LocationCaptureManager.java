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

    /**
     * A location fix reduced to plain-Java values. Kept free of ATAK types so
     * the capture decision logic can be unit tested on a plain JVM; the ATAK
     * plumbing that produces it lives in {@link MapViewLocationFixSource}.
     */
    public static class LocationFix {

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

        private LocationFix(boolean available, String message, double latitude,
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

        static LocationFix available(double latitude, double longitude,
                double altitude, double accuracy, long timestamp, String source,
                double bearing, double speed) {
            return new LocationFix(true, "", latitude, longitude, altitude,
                    accuracy, bearing, speed, timestamp, source);
        }

        static LocationFix unavailable(String message) {
            return new LocationFix(false, message, 0.0, 0.0, 0.0, 0.0, 0.0,
                    0.0, 0L, "");
        }

        public boolean isAvailable() {
            return available;
        }

        public String getMessage() {
            return message;
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public double getAltitude() {
            return altitude;
        }

        public double getAccuracy() {
            return accuracy;
        }

        public double getBearing() {
            return bearing;
        }

        public double getSpeed() {
            return speed;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getSource() {
            return source;
        }
    }

    /**
     * Supplies a {@link LocationFix}. The ATAK-bound implementation reads
     * {@link MapView}; tests provide a canned implementation instead.
     */
    interface LocationFixSource {
        LocationFix readFix();
    }

    /** Reads the self-marker fix and heading/speed from an ATAK MapView. */
    private static class MapViewLocationFixSource implements LocationFixSource {

        private final MapView mapView;

        MapViewLocationFixSource(MapView mapView) {
            this.mapView = mapView;
        }

        @Override
        public LocationFix readFix() {
            AtakLocationStatus.Snapshot snapshot =
                    AtakLocationStatus.from(mapView);
            if (!snapshot.isAvailable())
                return LocationFix.unavailable(snapshot.getMessage());

            GeoPoint point = snapshot.getPoint();
            Marker self = mapView.getSelfMarker();
            return LocationFix.available(point.getLatitude(),
                    point.getLongitude(), point.getAltitude(), point.getCE(),
                    snapshot.getTimestamp(), snapshot.getSource(),
                    self == null ? 0.0 : self.getTrackHeading(),
                    self == null ? 0.0 : self.getTrackSpeed());
        }
    }

    public static final long UPDATE_INTERVAL_MS = 10000L;

    private final LocationFixSource locationFixSource;
    private final IdentityManager identityManager;
    private final SearchTrackManager trackManager;
    private final PluginHealthManager healthManager;
    private final Listener listener;
    private final Handler handler;
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
        this(new MapViewLocationFixSource(mapView), identityManager,
                trackManager, healthManager, listener,
                new Handler(Looper.getMainLooper()));
    }

    LocationCaptureManager(LocationFixSource locationFixSource,
            IdentityManager identityManager, SearchTrackManager trackManager,
            PluginHealthManager healthManager, Listener listener,
            Handler handler) {
        this.locationFixSource = locationFixSource;
        this.identityManager = identityManager;
        this.trackManager = trackManager;
        this.healthManager = healthManager;
        this.listener = listener;
        this.handler = handler;
    }

    public void start() {
        if (running)
            return;
        running = true;
        captureNow();
        handler.postDelayed(captureRunnable, UPDATE_INTERVAL_MS);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(captureRunnable);
    }

    public void captureNow() {
        captureWith(identityManager.resolveIdentity(),
                locationFixSource.readFix());
    }

    /**
     * Capture decision logic, kept free of ATAK types so it can be unit tested
     * on a plain JVM. The ATAK plumbing lives in {@link #captureNow()}.
     */
    void captureWith(IdentityManager.Identity identity, LocationFix fix) {
        healthManager.setIdentityResolved(identity.isResolved(),
                identity.getMessage());
        if (!identity.isResolved()) {
            healthManager.recordLocationFailure(
                    "Identity unavailable; tracking degraded");
            notifyListener();
            return;
        }

        if (!fix.isAvailable()) {
            healthManager.recordLocationFailure(fix.getMessage());
            notifyListener();
            return;
        }

        // Track points are only ever logged by raw GNSS capture
        // (RawGnssCaptureManager) so the track never mixes ATAK's
        // internally-fused self-marker fix with unmodified raw device
        // readings - see Sprint 1 "preserve accuracy metadata without
        // modification". This self-marker snapshot is used purely to drive
        // identity/health reporting (Active/Degraded/GPS Lost) below, not
        // to log a track point.
        trackManager.recordLocation(identity.getUid(), identity.getCallsign(),
                fix.getLatitude(), fix.getLongitude(), fix.getAltitude(),
                fix.getAccuracy(), fix.getBearing(), fix.getSpeed(),
                fix.getTimestamp());
        healthManager.setTrackingActive(trackManager.isRecording());
        healthManager.recordLocationSuccess(fix.getTimestamp(),
                fix.getAccuracy(), fix.getSource());
        notifyListener();
    }

    private void notifyListener() {
        if (listener != null)
            listener.onLocationCaptured();
    }
}
