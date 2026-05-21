package com.atakmap.android.plugintemplate.grid;

import com.atakmap.android.plugintemplate.database.LocationRepository;
import com.atakmap.android.plugintemplate.database.TrackSessionRepository;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.List;
import java.util.Locale;
import java.util.UUID;


public class SearchTrackManager {

    private final TrackSessionRepository trackSessionRepository;
    private final LocationRepository locationRepository;
    private boolean recording = true;
    private boolean visible = true;
    private String activeSessionId;
    private String activeUid;
    private String activeCallsign;
    private long sessionStartedAt;

    public SearchTrackManager(TrackSessionRepository trackSessionRepository,
            LocationRepository locationRepository) {
        this.trackSessionRepository = trackSessionRepository;
        this.locationRepository = locationRepository;
    }

    public void startOrResume(String uid, String callsign) {
        activeUid = uid;
        activeCallsign = callsign;
        String existingSession = trackSessionRepository.getActiveSessionId(uid);
        if (existingSession == null) {
            existingSession = "track-" + uid + "-" + UUID.randomUUID();
            sessionStartedAt = System.currentTimeMillis();
            trackSessionRepository.insert(existingSession, uid, callsign,
                    sessionStartedAt);
        } else {
            sessionStartedAt = trackSessionRepository
                    .getSessionStartTimestamp(existingSession);
        }
        activeSessionId = existingSession;
        recording = true;
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
        recording = wasRecording;
        if (uid != null && callsign != null && wasRecording)
            startOrResume(uid, callsign);

        if (activeSessionId != null) {
            trackSessionRepository.closeSession(
                    activeSessionId,
                    System.currentTimeMillis()
                    /// completed push
            );
        }
    }

    public void recordLocation(String uid, String callsign, GeoPoint point,
            double accuracy, double bearing, double speed, long timestamp) {
        if (!recording || point == null || !point.isValid())
            return;
        recordLocation(uid, callsign, point.getLatitude(),
                point.getLongitude(), point.getAltitude(), accuracy, bearing,
                speed, timestamp);
    }

    public void recordLocation(String uid, String callsign, double latitude,
            double longitude, double altitude, double accuracy, double bearing,
            double speed, long timestamp) {
        if (!recording)
            return;
        if (activeSessionId == null || activeUid == null
                || !activeUid.equals(uid))
            startOrResume(uid, callsign);

        locationRepository.insert(uid, callsign, latitude, longitude, altitude,
                (float) accuracy, (float) bearing, (float) speed, timestamp,
                activeSessionId);
        trackSessionRepository.incrementPointCount(activeSessionId);
    }

    public List<double[]> getTrackPoints() {
        if (activeSessionId == null)
            return java.util.Collections.emptyList();
        return locationRepository.getPointsForSession(activeSessionId);
    }

    public String getStatusSummary() {
        if (activeSessionId == null)
            return "Waiting for first GPS fix";
        return recording ? "Recording - local track active"
                : "Paused - local track retained";
    }

    public String getDetailsSummary() {
        int trackPoints = activeSessionId == null ? 0
                : locationRepository.countPointsInSession(activeSessionId);
        long elapsedSeconds = sessionStartedAt <= 0L ? 0L
                : (System.currentTimeMillis() - sessionStartedAt) / 1000L;
        double distanceMeters = calculateDistance(getTrackPoints());
        return String.format(Locale.US,
                "Distance: %.2f km\nDuration: %s\nTrack points: %d\nVisibility: %s\nSession: %s",
                distanceMeters / 1000.0, formatDuration(elapsedSeconds),
                trackPoints, visible ? "Shown on map" : "Hidden from map",
                activeSessionId == null ? "pending" : activeSessionId);
    }

    private double calculateDistance(List<double[]> points) {
        double total = 0.0;
        GeoPoint previous = null;
        for (double[] point : points) {
            GeoPoint current = new GeoPoint(point[0], point[1]);
            if (previous != null)
                total += previous.distanceTo(current);
            previous = current;
        }
        return total;
    }

    private String formatDuration(long elapsedSeconds) {
        long minutes = elapsedSeconds / 60L;
        long seconds = elapsedSeconds % 60L;
        return String.format(Locale.US, "%d min %02d sec", minutes, seconds);
    }

}

