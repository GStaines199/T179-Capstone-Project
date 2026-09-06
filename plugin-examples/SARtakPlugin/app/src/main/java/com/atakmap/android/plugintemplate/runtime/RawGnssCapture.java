package com.atakmap.android.plugintemplate.runtime;

import android.location.Location;
import android.os.Build;

public class RawGnssCapture {

    private final double latitude;
    private final double longitude;
    private final double altitude;
    private final float accuracyMeters;
    private final float bearingDegrees;
    private final float speedMps;
    private final String provider;
    private final long timestamp;
    private final Double verticalAccuracyMeters;
    private final Double bearingAccuracyDegrees;
    private final Double speedAccuracyMps;

    private RawGnssCapture(double latitude, double longitude, double altitude,
            float accuracyMeters, float bearingDegrees, float speedMps,
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
                location.getAltitude(),
                location.hasAccuracy() ? location.getAccuracy() : 0f,
                location.hasBearing() ? location.getBearing() : 0f,
                location.hasSpeed() ? location.getSpeed() : 0f,
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

    public double getAltitude() {
        return altitude;
    }

    public float getAccuracyMeters() {
        return accuracyMeters;
    }

    public float getBearingDegrees() {
        return bearingDegrees;
    }

    public float getSpeedMps() {
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
