package com.atakmap.android.plugintemplate.grid;

import com.atakmap.android.plugintemplate.database.LocationRepository;
import com.atakmap.android.plugintemplate.database.ReportedMeasurement;
import com.atakmap.android.plugintemplate.database.TrackSessionRepository;
import com.atakmap.android.plugintemplate.runtime.RawGnssCapture;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.UUID;


public class SearchTrackManager {

    private static final int MAX_DISPLAY_TRACK_POINTS = 1500;
    private static final int MAX_SESSION_POINTS = 10000;
    private static final long MAX_SESSION_AGE_MS = 12L * 60L * 60L * 1000L;

    private final TrackSessionRepository trackSessionRepository;
    private final LocationRepository locationRepository;
    private boolean recording = true;
    private boolean visible = true;
    private String activeSessionId;
    private String activeUid;
    private String activeCallsign;
    private String operationId = "";
    private long sessionStartedAt;
    private List<double[]> cachedTrackPoints = Collections.emptyList();
    private boolean trackCacheDirty = true;
    // The display cache above keeps only the newest points, so the distance
    // is tracked separately over the whole session.
    private double sessionDistanceMeters;
    private double lastLatitude = Double.NaN;
    private double lastLongitude = Double.NaN;
    private long lastTimestamp;
    private boolean distanceDirty = true;

    /**
     * Faster than anyone searching moves, on foot or in a vehicle or aircraft.
     * A leg implying more than this is a position jump, not travel.
     */
    static final double MAX_PLAUSIBLE_SPEED_MPS = 150.0;

    public SearchTrackManager(TrackSessionRepository trackSessionRepository,
            LocationRepository locationRepository) {
        this.trackSessionRepository = trackSessionRepository;
        this.locationRepository = locationRepository;
    }

    public void startOrResume(String uid, String callsign) {
        activeUid = uid;
        activeCallsign = callsign;
        if (!hasActiveOperation()) {
            activeSessionId = null;
            sessionStartedAt = 0L;
            recording = true;
            return;
        }
        String existingSession = trackSessionRepository.getActiveSessionId(uid,
                sessionPrefix(uid));
        if (existingSession != null) {
            long startedAt = trackSessionRepository
                    .getSessionStartTimestamp(existingSession);
            boolean expired = startedAt <= 0L
                    || System.currentTimeMillis() - startedAt
                            > MAX_SESSION_AGE_MS;
            boolean oversized = locationRepository.countPointsInSession(
                    existingSession) >= MAX_SESSION_POINTS;
            if (expired || oversized) {
                trackSessionRepository.closeSession(existingSession,
                        System.currentTimeMillis());
                existingSession = null;
            }
        }
        if (existingSession == null) {
            existingSession = sessionPrefix(uid) + UUID.randomUUID();
            sessionStartedAt = System.currentTimeMillis();
            trackSessionRepository.insert(existingSession, uid, callsign,
                    sessionStartedAt);
        } else {
            sessionStartedAt = trackSessionRepository
                    .getSessionStartTimestamp(existingSession);
        }
        activeSessionId = existingSession;
        recording = true;
        invalidateSessionCaches();
    }

    public void setOperationId(String operationId) {
        String nextOperationId = sanitize(operationId);
        if (nextOperationId.equals(this.operationId))
            return;
        this.operationId = nextOperationId;
        activeSessionId = null;
        sessionStartedAt = 0L;
        cachedTrackPoints = Collections.emptyList();
        invalidateSessionCaches();
        if (activeUid != null && activeCallsign != null && recording)
            startOrResume(activeUid, activeCallsign);
    }

    public boolean toggleRecording() {
        recording = !recording;
        return recording;
    }

    public boolean toggleVisible() {
        visible = !visible;
        return visible;
    }

    public boolean isRecording() {
        return recording;
    }

    public boolean isVisible() {
        return visible;
    }

    public String getActiveSessionId() {
        return activeSessionId;
    }

    public void clearCurrentTrack() {
        String uid = activeUid;
        String callsign = activeCallsign;
        boolean wasRecording = recording;

        if (activeSessionId != null) {
            locationRepository.deleteSession(activeSessionId);
            trackSessionRepository.deleteSession(activeSessionId);
        }

        activeSessionId = null;
        sessionStartedAt = 0L;
        invalidateSessionCaches();
        recording = wasRecording;
        if (uid != null && callsign != null && wasRecording)
            startOrResume(uid, callsign);
    }

    public void recordLocation(String uid, String callsign, GeoPoint point,
            double accuracy, double bearing, double speed, long timestamp) {
        if (!recording || point == null || !point.isValid())
            return;
        recordLocation(uid, callsign, point.getLatitude(),
                point.getLongitude(), point.getAltitude(), accuracy, bearing,
                speed, timestamp);
    }

    /**
     * Records a fix whose measurements are all known to have been reported.
     *
     * <p>Delegates to {@link #recordFix} rather than to the primitive
     * {@code insert}, so a NaN arriving through this path is resolved to "not
     * reported" explicitly rather than relying on SQLite to coerce it.
     */
    public void recordLocation(String uid, String callsign, double latitude,
            double longitude, double altitude, double accuracy, double bearing,
            double speed, long timestamp) {
        recordFix(uid, callsign, latitude, longitude, altitude, accuracy,
                bearing, speed, timestamp);
    }

    /**
     * Records a fix, preserving which measurements the receiver actually
     * reported.
     *
     * <p>Null means "not reported" and is stored as NULL. That distinction
     * cannot be recovered once written, because 0 m accuracy and a due-north
     * bearing are both values a real fix can legitimately have.
     */
    public void recordFix(String uid, String callsign, double latitude,
            double longitude, Double altitude, Double accuracy, Double bearing,
            Double speed, long timestamp) {
        if (!recording || !hasActiveOperation())
            return;
        if (activeSessionId == null || activeUid == null
                || !activeUid.equals(uid))
            startOrResume(uid, callsign);

        locationRepository.insertFix(uid, callsign, latitude, longitude,
                ReportedMeasurement.of(altitude),
                ReportedMeasurement.toFloat(accuracy),
                ReportedMeasurement.toFloat(bearing),
                ReportedMeasurement.toFloat(speed), timestamp,
                activeSessionId);
        trackSessionRepository.incrementPointCount(activeSessionId);
        onPointStored(latitude, longitude, timestamp,
                accuracy == null ? Double.NaN : accuracy);
    }

    /**
     * Records a raw GNSS fix into the active session.
     *
     * <p>Raw capture used to write straight to {@link LocationRepository},
     * which left the point cache and the distance behind: the Track tab kept
     * showing the distance from the last time the session was loaded. Writing
     * through here keeps both current. Without an active operation there is no
     * session to write to, so nothing is stored.
     */
    public void recordRawCapture(String uid, String callsign,
            RawGnssCapture capture) {
        if (!recording || !hasActiveOperation() || capture == null)
            return;
        if (activeSessionId == null || activeUid == null
                || !activeUid.equals(uid))
            startOrResume(uid, callsign);

        locationRepository.insertRaw(uid, callsign, activeSessionId, capture);
        trackSessionRepository.incrementPointCount(activeSessionId);
        Double accuracy = capture.getAccuracyMeters();
        onPointStored(capture.getLatitude(), capture.getLongitude(),
                capture.getTimestamp(),
                accuracy == null ? Double.NaN : accuracy);
    }

    private void onPointStored(double latitude, double longitude,
            long timestamp, double accuracy) {
        // A dirty cache is reloaded from the database on the next read.
        if (!trackCacheDirty) {
            List<double[]> updated = new ArrayList<>(cachedTrackPoints);
            updated.add(new double[] {latitude, longitude, timestamp,
                    accuracy});
            if (updated.size() > MAX_DISPLAY_TRACK_POINTS)
                updated = new ArrayList<>(updated.subList(updated.size()
                        - MAX_DISPLAY_TRACK_POINTS, updated.size()));
            cachedTrackPoints = updated;
        }
        if (!distanceDirty) {
            if (!Double.isNaN(lastLatitude))
                sessionDistanceMeters += legDistance(lastLatitude,
                        lastLongitude, lastTimestamp, latitude, longitude,
                        timestamp);
            lastLatitude = latitude;
            lastLongitude = longitude;
            lastTimestamp = timestamp;
        }
    }

    private void invalidateSessionCaches() {
        trackCacheDirty = true;
        distanceDirty = true;
    }

    /** Distance walked in the active session, over every stored point. */
    public double getDistanceMeters() {
        if (distanceDirty) {
            sessionDistanceMeters = 0.0;
            lastLatitude = Double.NaN;
            lastLongitude = Double.NaN;
            lastTimestamp = 0L;
            if (activeSessionId != null) {
                for (double[] point : locationRepository
                        .getAllPointsForSession(activeSessionId)) {
                    long timestamp = point.length > 2 ? (long) point[2] : 0L;
                    if (!Double.isNaN(lastLatitude))
                        sessionDistanceMeters += legDistance(lastLatitude,
                                lastLongitude, lastTimestamp, point[0],
                                point[1], timestamp);
                    lastLatitude = point[0];
                    lastLongitude = point[1];
                    lastTimestamp = timestamp;
                }
            }
            distanceDirty = false;
        }
        return sessionDistanceMeters;
    }

    public List<double[]> getTrackPoints() {
        if (activeSessionId == null)
            return Collections.emptyList();
        if (trackCacheDirty) {
            cachedTrackPoints = locationRepository.getPointsForSession(
                    activeSessionId);
            trackCacheDirty = false;
        }
        return new ArrayList<>(cachedTrackPoints);
    }

    public String getStatusSummary() {
        if (!hasActiveOperation())
            return "Waiting for active operation";
        if (activeSessionId == null)
            return "Waiting for first GPS fix";
        return recording ? "Recording - local track active"
                : "Paused - local track retained";
    }

    public String getDetailsSummary() {
        if (!hasActiveOperation())
            return "Create or join an operation to start operation-scoped track logging.";
        int trackPoints = activeSessionId == null ? 0
                : locationRepository.countPointsInSession(activeSessionId);
        long elapsedSeconds = sessionStartedAt <= 0L ? 0L
                : (System.currentTimeMillis() - sessionStartedAt) / 1000L;
        double distanceMeters = getDistanceMeters();
        return String.format(Locale.US,
                "Distance: %.2f km\nDuration: %s\nTrack points: %d\nVisibility: %s\nSession: %s",
                distanceMeters / 1000.0, formatDuration(elapsedSeconds),
                trackPoints, visible ? "Shown on map" : "Hidden from map",
                activeSessionId == null ? "pending" : activeSessionId);
    }

    /**
     * Metres walked between two consecutive fixes, or 0 when getting from one
     * to the other would take an impossible speed. A receiver that jumps to a
     * stale or default position and back (the emulator's GPS swaps between
     * California and Brisbane on boot) otherwise added thousands of km. The
     * next leg is measured from the jumped-to fix, so a real move after a gap
     * still counts. Timestamps in ms; a missing or out-of-order one counts as
     * one second apart.
     */
    static double legDistance(double lat1, double lng1, long time1,
            double lat2, double lng2, long time2) {
        double meters = haversineDistance(lat1, lng1, lat2, lng2);
        double seconds = time1 > 0L && time2 > time1
                ? (time2 - time1) / 1000.0 : 1.0;
        return meters > MAX_PLAUSIBLE_SPEED_MPS * Math.max(seconds, 1.0)
                ? 0.0 : meters;
    }

    /**
     * Great-circle distance between two coordinates in metres. Kept free of
     * ATAK types so the track-summary logic can be unit tested on a plain JVM;
     * the ATAK GeoPoint#distanceTo path is not used here.
     */
    public static double haversineDistance(double lat1, double lng1,
            double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371000.0 * c;
    }

    private String formatDuration(long elapsedSeconds) {
        long minutes = elapsedSeconds / 60L;
        long seconds = elapsedSeconds % 60L;
        return String.format(Locale.US, "%d min %02d sec", minutes, seconds);
    }

    private String sessionPrefix(String uid) {
        return "track-" + operationId + "-" + sanitize(uid) + "-";
    }

    private boolean hasActiveOperation() {
        return operationId.length() > 0;
    }

    private String sanitize(String value) {
        return value == null ? "" : value.trim();
    }

    public void stopSession() {
        if (activeSessionId != null) {
            trackSessionRepository.closeSession(
                    activeSessionId,
                    System.currentTimeMillis()
                    /// completed push
            );
            activeSessionId = null;
            sessionStartedAt = 0L;
            cachedTrackPoints = Collections.emptyList();
            invalidateSessionCaches();
        }
    }
}

