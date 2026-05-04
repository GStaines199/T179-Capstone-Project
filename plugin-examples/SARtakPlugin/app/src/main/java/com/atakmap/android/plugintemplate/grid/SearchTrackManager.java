package com.atakmap.android.plugintemplate.grid;

import java.util.Locale;

public class SearchTrackManager {

    private static final double BASE_DISTANCE_METERS = 840.0;
    private static final int BASE_TRACK_POINTS = 126;

    private boolean recording = true;
    private boolean visible = true;
    private long recordingStartedAt = System.currentTimeMillis();
    private long recordedMillis;

    public boolean toggleRecording() {
        long now = System.currentTimeMillis();
        if (recording) {
            recordedMillis += now - recordingStartedAt;
            recording = false;
        } else {
            recordingStartedAt = now;
            recording = true;
        }
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

    public String getStatusSummary() {
        return recording ? "Recording - local track active"
                : "Paused - local track retained";
    }

    public String getDetailsSummary() {
        long elapsedSeconds = getElapsedMillis() / 1000L;
        double distanceMeters = BASE_DISTANCE_METERS
                + (recording ? elapsedSeconds * 0.8 : elapsedSeconds * 0.2);
        int trackPoints = BASE_TRACK_POINTS + (int) (elapsedSeconds / 8L);
        return String.format(Locale.US,
                "Distance: %.2f km\nDuration: %s\nTrack points: %d\nVisibility: %s\nAverage spacing: 20 m target",
                distanceMeters / 1000.0, formatDuration(elapsedSeconds),
                trackPoints, visible ? "Shown on map" : "Hidden from map");
    }

    private long getElapsedMillis() {
        if (recording)
            return recordedMillis + System.currentTimeMillis()
                    - recordingStartedAt;
        return recordedMillis;
    }

    private String formatDuration(long elapsedSeconds) {
        long minutes = elapsedSeconds / 60L;
        long seconds = elapsedSeconds % 60L;
        return String.format(Locale.US, "%d min %02d sec", minutes, seconds);
    }
}
