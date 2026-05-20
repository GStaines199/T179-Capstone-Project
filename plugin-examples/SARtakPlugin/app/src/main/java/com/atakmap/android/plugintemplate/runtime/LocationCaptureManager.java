package com.atakmap.android.plugintemplate.runtime;

import android.os.Handler;
import android.os.Looper;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.plugintemplate.grid.SearchTrackManager;

public class LocationCaptureManager {

    public interface Listener {
        void onLocationCaptured();
    }

    public static final long UPDATE_INTERVAL_MS = 10000L;

    private final MapView mapView;
    private final IdentityManager identityManager;
    private final SearchTrackManager trackManager;
    private final PluginHealthManager healthManager;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
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
        captureNow();
        handler.postDelayed(captureRunnable, UPDATE_INTERVAL_MS);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(captureRunnable);
    }

    public void captureNow() {
        IdentityManager.Identity identity = identityManager.resolveIdentity();
        healthManager.setIdentityResolved(identity.isResolved(),
                identity.getMessage());
        if (!identity.isResolved()) {
            healthManager.recordLocationFailure(
                    "Identity unavailable; tracking degraded");
            notifyListener();
            return;
        }

        AtakLocationStatus.Snapshot snapshot = AtakLocationStatus.from(mapView);
        if (!snapshot.isAvailable()) {
            healthManager.recordLocationFailure(snapshot.getMessage());
            notifyListener();
            return;
        }

        long timestamp = snapshot.getTimestamp();
        double accuracy = snapshot.getPoint().getCE();
        trackManager.recordLocation(identity.getUid(), identity.getCallsign(),
                snapshot.getPoint(), accuracy,
                mapView.getSelfMarker().getTrackHeading(),
                mapView.getSelfMarker().getTrackSpeed(), timestamp);
        healthManager.setTrackingActive(trackManager.isRecording());
        healthManager.recordLocationSuccess(timestamp, accuracy,
                snapshot.getSource());
        notifyListener();
    }

    private void notifyListener() {
        if (listener != null)
            listener.onLocationCaptured();
    }
}
