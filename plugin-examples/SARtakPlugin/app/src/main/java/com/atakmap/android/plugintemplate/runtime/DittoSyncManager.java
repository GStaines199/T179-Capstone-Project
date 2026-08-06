package com.atakmap.android.plugintemplate.runtime;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.plugintemplate.grid.SearchGridCell;
import com.atakmap.android.plugintemplate.grid.SearchTeamMember;
import com.atakmap.android.plugintemplate.plugin.BuildConfig;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.ditto.kotlin.Ditto;
import com.ditto.kotlin.DittoStoreObserver;
import com.ditto.kotlin.DittoSyncSubscription;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DittoSyncManager {

    private static final String TAG = "SARtakDittoSync";
    private static final long PUBLISH_INTERVAL_MS = 5000L;
    private static final String DEVICE_COLLECTION = "sartak_devices";
    private static final String DEVICE_QUERY = "SELECT * FROM "
            + DEVICE_COLLECTION;

    private final MapView mapView;
    private final IdentityManager identityManager;
    private final Map<String, DittoDeviceSnapshot> devices =
            Collections.synchronizedMap(new LinkedHashMap<String,
                    DittoDeviceSnapshot>());

    private Ditto ditto;
    private DittoSyncSubscription deviceSubscription;
    private DittoStoreObserver deviceObserver;
    private boolean started;
    private boolean configured;
    private String status = "Ditto: not started";
    private long lastPublishTime;
    private long lastReceiveTime;

    public DittoSyncManager(MapView mapView,
            IdentityManager identityManager) {
        this.mapView = mapView;
        this.identityManager = identityManager;
    }

    public void start() {
        if (started)
            return;
        configured = hasDittoCredentials();
        if (!configured) {
            status = "Ditto: not configured";
            return;
        }

        try {
            ditto = DittoSdkBridge.createDitto(BuildConfig.DITTO_APP_ID,
                    BuildConfig.DITTO_AUTH_URL);
            DittoSdkBridge.setupAuth(ditto,
                    BuildConfig.DITTO_PLAYGROUND_TOKEN);
            deviceSubscription = DittoSdkBridge.registerSubscription(ditto,
                    DEVICE_QUERY);
            deviceObserver = DittoSdkBridge.registerJsonObserver(ditto,
                    DEVICE_QUERY, new java.util.function.Consumer<List<String>>() {
                        @Override
                        public void accept(List<String> jsonDocuments) {
                            updateSnapshots(jsonDocuments);
                        }
                    });
            DittoSdkBridge.startSync(ditto);
            started = true;
            status = "Ditto: active";
        } catch (Throwable throwable) {
            status = "Ditto: unavailable - " + throwable.getClass()
                    .getSimpleName();
            Log.w(TAG, "Ditto startup failed", throwable);
        }
    }

    public void stop() {
        if (ditto == null)
            return;
        try {
            DittoSdkBridge.stopSync(ditto);
        } catch (Throwable ignored) {
        }
        started = false;
        status = configured ? "Ditto: stopped" : "Ditto: not configured";
        deviceObserver = null;
        deviceSubscription = null;
        ditto = null;
    }

    public void publishDeviceStateIfDue(boolean teamCreated, String teamId,
            String teamName, String leaderUid, String leaderCallsign,
            String roleLabel, String teamColorName, int teamColorArgb,
            SearchTeamMember selfMember, SearchGridCell selectedCell,
            AtakLocationStatus.Snapshot location) {
        if (!started || ditto == null)
            return;
        long now = System.currentTimeMillis();
        if (now - lastPublishTime < PUBLISH_INTERVAL_MS)
            return;
        publishDeviceState(teamCreated, teamId, teamName, leaderUid,
                leaderCallsign, roleLabel, teamColorName, teamColorArgb,
                selfMember, selectedCell, location, now);
    }

    public List<DittoDeviceSnapshot> getDeviceSnapshots() {
        synchronized (devices) {
            return new ArrayList<>(devices.values());
        }
    }

    public String getSummary() {
        if (!configured)
            return "Ditto: not configured";
        String active = "unknown";
        if (ditto != null) {
            try {
                active = DittoSdkBridge.isSyncActive(ditto) ? "active"
                        : "inactive";
            } catch (Throwable ignored) {
                active = "unknown";
            }
        }
        int peers = Math.max(0, getDeviceSnapshots().size() - 1);
        return status + " (" + active + ") | peers " + peers
                + " | sent " + formatAge(lastPublishTime)
                + " | received " + formatAge(lastReceiveTime);
    }

    private void publishDeviceState(boolean teamCreated, String teamId,
            String teamName, String leaderUid, String leaderCallsign,
            String roleLabel, String teamColorName, int teamColorArgb,
            SearchTeamMember selfMember, SearchGridCell selectedCell,
            AtakLocationStatus.Snapshot location, long now) {
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        if (identity == null || !identity.isResolved()) {
            status = "Ditto: waiting for ATAK identity";
            return;
        }

        Map<String, Object> document = new HashMap<>();
        document.put("_id", "device-" + identity.getUid());
        document.put("uid", identity.getUid());
        document.put("callsign", identity.getCallsign());
        document.put("teamCreated", teamCreated);
        document.put("teamId", safe(teamId));
        document.put("teamName", safe(teamName));
        document.put("leaderUid", safe(leaderUid));
        document.put("leaderCallsign", safe(leaderCallsign));
        document.put("role", safe(roleLabel));
        document.put("teamColorName", safe(teamColorName));
        document.put("teamColorArgb", teamColorArgb);
        document.put("memberColorName", selfMember == null ? ""
                : safe(selfMember.getColorName()));
        document.put("memberColorArgb", selfMember == null ? 0
                : selfMember.getDisplayColor());
        document.put("updatedAt", now);

        boolean hasLocation = location != null && location.isAvailable();
        document.put("hasLocation", hasLocation);
        if (hasLocation) {
            GeoPoint point = location.getPoint();
            document.put("latitude", point.getLatitude());
            document.put("longitude", point.getLongitude());
            document.put("altitude", point.isAltitudeValid()
                    ? point.getAltitude() : 0.0);
            document.put("accuracy", point.getCE());
            document.put("source", location.getSource());
        }
        document.put("heading", selfMember == null ? 0.0
                : selfMember.getHeadingDegrees());
        document.put("speed", selfMember == null ? 0.0
                : selfMember.getSpeedMetersPerSecond());
        document.put("headingReliable", selfMember != null
                && selfMember.hasReliableHeading());
        document.put("gridCellId", selectedCell == null ? ""
                : selectedCell.getId());

        Map<String, Object> args = Collections.singletonMap("device",
                (Object) document);
        try {
            DittoSdkBridge.execute(ditto, "INSERT INTO " + DEVICE_COLLECTION
                    + " DOCUMENTS (:device) ON ID CONFLICT DO UPDATE", args);
            lastPublishTime = now;
            status = "Ditto: active";
        } catch (Throwable throwable) {
            status = "Ditto: publish failed - " + throwable.getClass()
                    .getSimpleName();
            Log.w(TAG, "Ditto publish failed", throwable);
        }
    }

    private void updateSnapshots(List<String> jsonDocuments) {
        if (jsonDocuments == null)
            return;
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        String selfUid = identity == null ? "" : identity.getUid();
        LinkedHashMap<String, DittoDeviceSnapshot> next =
                new LinkedHashMap<>();
        for (String json : jsonDocuments) {
            try {
                DittoDeviceSnapshot snapshot = DittoDeviceSnapshot
                        .fromJson(json);
                if (snapshot.getUid().length() == 0)
                    continue;
                next.put(snapshot.getUid(), snapshot);
            } catch (JSONException exception) {
                Log.w(TAG, "Ignoring invalid Ditto device document",
                        exception);
            }
        }
        synchronized (devices) {
            devices.clear();
            devices.putAll(next);
        }
        for (DittoDeviceSnapshot snapshot : next.values()) {
            if (!snapshot.getUid().equals(selfUid)) {
                lastReceiveTime = System.currentTimeMillis();
                break;
            }
        }
    }

    private boolean hasDittoCredentials() {
        return notEmpty(BuildConfig.DITTO_APP_ID)
                && notEmpty(BuildConfig.DITTO_PLAYGROUND_TOKEN)
                && notEmpty(BuildConfig.DITTO_AUTH_URL);
    }

    private boolean notEmpty(String value) {
        return value != null && value.trim().length() > 0;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String formatAge(long timestamp) {
        if (timestamp <= 0L)
            return "never";
        long seconds = Math.max(0L, (System.currentTimeMillis() - timestamp)
                / 1000L);
        return seconds <= 1L ? "now" : seconds + " sec ago";
    }
}
