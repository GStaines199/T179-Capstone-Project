package com.atakmap.android.plugintemplate.runtime;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;

import com.atakmap.android.plugintemplate.database.LocationRepository;
import com.atakmap.android.plugintemplate.grid.SearchTrackManager;

public class RawGnssCaptureManager {

    public interface Listener {
        void onRawGnssCaptured(RawGnssCapture capture);
    }

    public static final long MIN_UPDATE_INTERVAL_MS = 1000L;
    public static final float MIN_UPDATE_DISTANCE_M = 0f;

    private final Context context;
    private final LocationManager locationManager;
    private final IdentityManager identityManager;
    private final SearchTrackManager trackManager;
    private final LocationRepository locationRepository;
    private final Listener listener;
    private boolean running;

    private final LocationListener rawLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            handleLocation(location);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }
    };

    public RawGnssCaptureManager(Context context, IdentityManager identityManager,
            SearchTrackManager trackManager, LocationRepository locationRepository,
            Listener listener) {
        this.context = context;
        this.locationManager = (LocationManager)
                context.getSystemService(Context.LOCATION_SERVICE);
        this.identityManager = identityManager;
        this.trackManager = trackManager;
        this.locationRepository = locationRepository;
        this.listener = listener;
    }

    public void start() {
        if (running)
            return;
        if (!hasFineLocationPermission())
            return;
        if (locationManager == null
                || !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
            return;

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                MIN_UPDATE_INTERVAL_MS, MIN_UPDATE_DISTANCE_M, rawLocationListener,
                Looper.getMainLooper());
        running = true;
    }

    public void stop() {
        if (!running)
            return;
        locationManager.removeUpdates(rawLocationListener);
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    void handleLocation(Location location) {
        IdentityManager.Identity identity = identityManager.resolveIdentity();
        if (!identity.isResolved())
            return;
        if (!trackManager.isRecording())
            return;

        String sessionId = trackManager.getActiveSessionId();
        if (sessionId == null) {
            trackManager.startOrResume(identity.getUid(), identity.getCallsign());
            sessionId = trackManager.getActiveSessionId();
        }

        RawGnssCapture capture = RawGnssCapture.from(location);
        locationRepository.insertRaw(identity.getUid(), identity.getCallsign(),
                sessionId, capture);

        if (listener != null)
            listener.onRawGnssCaptured(capture);
    }

    private boolean hasFineLocationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return true;
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
}
