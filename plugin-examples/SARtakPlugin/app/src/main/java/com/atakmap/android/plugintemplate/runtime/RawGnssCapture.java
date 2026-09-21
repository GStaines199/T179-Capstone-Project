package com.atakmap.android.plugintemplate.runtime;

import android.location.Location;
import android.os.Build;

/**
 * One GNSS fix, carrying only what the receiver actually reported.
 * <p>
 * Every measurement field is boxed, and null means "not reported". That
 * distinction has to survive this class: {@link Location} returns 0 from
 * {@code getAltitude()}, {@code getAccuracy()}, {@code getBearing()} and
 * {@code getSpeed()} when the corresponding {@code has*()} flag is false, and
 * 0 m accuracy, 0 m altitude, a due-north bearing and a stationary speed are
 * all values a real fix can legitimately have. Substituting 0 here would make
 * an unreported measurement indistinguishable from a measured one for every
 * later reader, and the difference cannot be recovered once stored.
 * <p>
 * {@link com.atakmap.android.plugintemplate.database.LocationRepository#insertRaw}
 * writes these straight through, so a null lands as a NULL column.
 */
public class RawGnssCapture {

    private final double latitude;
    private final double longitude;
    private final Double altitude;
    private final Double accuracyMeters;
    private final Double bearingDegrees;
    private final Double speedMps;
    private final String provider;
    private final long timestamp;
    private final Double verticalAccuracyMeters;
    private final Double bearingAccuracyDegrees;
    private final Double speedAccuracyMps;

    private RawGnssCapture(double latitude, double longitude, Double altitude,
            Double accuracyMeters, Double bearingDegrees, Double speedMps,
            String provider, long timestamp, Double verticalAccuracyMeters,
            Double bearingAccuracyDegrees, Double speedAccuracyMps) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.accuracyMeters = accuracyMeters;
        this.bearingDegrees = bearingDegrees;
        this.speedMps = speedMps;
        this.provider = provider;
        this.timestamp = timestamp;
        this.verticalAccuracyMeters = verticalAccuracyMeters;
        this.bearingAccuracyDegrees = bearingAccuracyDegrees;
        this.speedAccuracyMps = speedAccuracyMps;
    }

    public static RawGnssCapture from(Location location) {
        Double verticalAccuracy = null;
        Double bearingAccuracy = null;
        Double speedAccuracy = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (location.hasVerticalAccuracy())
                verticalAccuracy = (double) location.getVerticalAccuracyMeters();
            if (location.hasBearingAccuracy())
                bearingAccuracy = (double) location.getBearingAccuracyDegrees();
            if (location.hasSpeedAccuracy())
                speedAccuracy = (double) location.getSpeedAccuracyMetersPerSecond();
        }

        return new RawGnssCapture(
                location.getLatitude(),
                location.getLongitude(),
                location.hasAltitude() ? location.getAltitude() : null,
                location.hasAccuracy() ? (double) location.getAccuracy() : null,
                location.hasBearing() ? (double) location.getBearing() : null,
                location.hasSpeed() ? (double) location.getSpeed() : null,
                location.getProvider(),
                location.getTime(),
                verticalAccuracy,
                bearingAccuracy,
                speedAccuracy);
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    /** Null when the receiver reported no altitude. */
    public Double getAltitude() {
        return altitude;
    }

    /** Null when the receiver reported no horizontal accuracy. */
    public Double getAccuracyMeters() {
        return accuracyMeters;
    }

    /** Null when the receiver reported no bearing. */
    public Double getBearingDegrees() {
        return bearingDegrees;
    }

    /** Null when the receiver reported no speed. */
    public Double getSpeedMps() {
        return speedMps;
    }

    public String getProvider() {
        return provider;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Double getVerticalAccuracyMeters() {
        return verticalAccuracyMeters;
    }

    public Double getBearingAccuracyDegrees() {
        return bearingAccuracyDegrees;
    }

    public Double getSpeedAccuracyMps() {
        return speedAccuracyMps;
    }
}
