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
     * <p>
     * The measurement fields are primitive, so "not reported" travels as
     * {@code Double.NaN} rather than as null. Nothing downstream treats NaN as
     * a measurement: {@link SearchTrackManager#recordLocation} routes through
     * {@code recordFix}, which resolves NaN to a NULL column. Producers must
     * therefore pass NaN, never a stand-in zero -- 0 m accuracy and a
     * due-north bearing are both values a real fix can legitimately have, so a
     * substituted zero cannot be told apart from a reading afterwards.
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
            return new LocationFix(false, message, 0.0, 0.0, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN, 0L, "");
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

        /** {@code NaN} when the receiver reported no altitude. */
        public double getAltitude() {
            return altitude;
        }

        public boolean hasAltitude() {
            return !Double.isNaN(altitude);
        }

        public double getAccuracy() {
            return accuracy;
        }

        /** {@code NaN} when the receiver reported no bearing. */
        public double getBearing() {
            return bearing;
        }

        public boolean hasBearing() {
            return !Double.isNaN(bearing);
        }

        /** {@code NaN} when the receiver reported no speed. */
        public double getSpeed() {
            return speed;
        }

        public boolean hasSpeed() {
            return !Double.isNaN(speed);
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
            if (point == null || !point.isValid())
                return LocationFix.unavailable("No GPS Signal");

            // Altitude, track heading and track speed are all NaN when ATAK has
            // no reading for them, and NaN is what the rest of the pipeline
            // reads as "not reported". A missing self marker is the same
            // situation, so it yields NaN too rather than a zero heading and
            // speed, which would be indistinguishable from a stationary
            // north-facing searcher.
            Marker self = mapView.getSelfMarker();
            return LocationFix.available(point.getLatitude(),
                    point.getLongitude(), point.getAltitude(), point.getCE(),
                    snapshot.getTimestamp(), snapshot.getSource(),
                    self == null ? Double.NaN : self.getTrackHeading(),
                    self == null ? Double.NaN : self.getTrackSpeed());
        }
    }

    public static final long UPDATE_INTERVAL_MS = 10000L;
    private static final double MAX_REASONABLE_SEARCH_SPEED_METERS_PER_SECOND =
            12.0;

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
     * <p>
     * A position is written only when the identity resolved <i>and</i> ATAK
     * supplied a usable fix; every other path records the failure and writes
     * nothing, so a lost signal can never be filled in with a stale or
     * inferred position.
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

        if (fix == null || !fix.isAvailable()) {
            healthManager.recordLocationFailure(
                    fix == null ? "No GPS Signal" : fix.getMessage());
            notifyListener();
            return;
        }

        trackManager.recordLocation(identity.getUid(), identity.getCallsign(),
                fix.getLatitude(), fix.getLongitude(), fix.getAltitude(),
                fix.getAccuracy(), fix.getBearing(), fix.getSpeed(),
                fix.getTimestamp());
        healthManager.setTrackingActive(trackManager.isRecording());
        healthManager.recordLocationSuccess(fix.getTimestamp(),
                fix.getAccuracy(), fix.getSource());
        notifyListener();
    }

    private double sanitizeSpeed(double speedMetersPerSecond) {
        if (Double.isNaN(speedMetersPerSecond)
                || Double.isInfinite(speedMetersPerSecond)
                || speedMetersPerSecond < 0.0
                || speedMetersPerSecond
                        > MAX_REASONABLE_SEARCH_SPEED_METERS_PER_SECOND)
            return 0.0;
        return speedMetersPerSecond;
    }

    private void notifyListener() {
        if (listener != null)
            listener.onLocationCaptured();
    }
}
