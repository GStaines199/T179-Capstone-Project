package com.atakmap.android.plugintemplate.runtime;

import android.os.Bundle;

import com.atakmap.android.importexport.ImporterManager;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.plugintemplate.grid.TeamMarkerVisibilityMode;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts Ditto device snapshots into normal ATAK CoT contact markers.
 *
 * <p>Ditto is only the transport/cache here. The map item is created through
 * ATAK's CoT importer using the remote device UID and standard contact detail
 * so the marker behaves like an ATAK contact instead of a SARtak-only overlay
 * icon. SARtak team/member colours are deliberately kept in SARtak metadata
 * keys so they do not fight ATAK's native team colour.
 */
public class DittoAtakContactBridge {

    private static final String TAG = "SARtakDittoAtakBridge";
    private static final long MIN_IMPORT_INTERVAL_MS = 2000L;
    private static final long STALE_AFTER_MS = 90 * 1000L;

    private final MapView mapView;
    private final Map<String, Long> importedSnapshotTimes = new HashMap<>();
    private final Set<String> managedUids = new HashSet<>();

    public DittoAtakContactBridge(MapView mapView) {
        this.mapView = mapView;
    }

    public void sync(List<DittoDeviceSnapshot> snapshots, String selfUid,
            String activeOperationId, String localTeamId,
            boolean leaderView, TeamMarkerVisibilityMode visibilityMode,
            boolean showCallsigns) {
        if (mapView == null || snapshots == null)
            return;

        Set<String> activeUids = new HashSet<>();
        long now = System.currentTimeMillis();
        for (DittoDeviceSnapshot snapshot : snapshots) {
            if (!canImport(snapshot, selfUid, activeOperationId))
                continue;
            activeUids.add(snapshot.getUid());
            boolean markerVisible = shouldShow(snapshot, localTeamId,
                    leaderView, visibilityMode);
            Long lastImported = importedSnapshotTimes.get(snapshot.getUid());
            if (lastImported != null
                    && snapshot.getUpdatedAt() <= lastImported
                    && now - lastImported < MIN_IMPORT_INTERVAL_MS) {
                configureExistingMarker(snapshot, markerVisible,
                        showCallsigns);
                continue;
            }
            importSnapshot(snapshot, markerVisible, showCallsigns);
            importedSnapshotTimes.put(snapshot.getUid(), Math.max(now,
                    snapshot.getUpdatedAt()));
            managedUids.add(snapshot.getUid());
        }
        staleMissingMarkers(activeUids);
    }

    public void clear() {
        importedSnapshotTimes.clear();
        managedUids.clear();
    }

    private boolean canImport(DittoDeviceSnapshot snapshot, String selfUid,
            String activeOperationId) {
        if (snapshot == null || snapshot.getUid() == null
                || snapshot.getUid().trim().length() == 0)
            return false;
        if (selfUid != null && selfUid.equals(snapshot.getUid()))
            return false;
        if (safe(activeOperationId).length() > 0
                && !safe(activeOperationId).equals(safe(snapshot
                        .getOperationId())))
            return false;
        if (!snapshot.hasLocation())
            return false;
        if (!isValidCoordinate(snapshot.getLatitude(),
                snapshot.getLongitude()))
            return false;
        return true;
    }

    private boolean shouldShow(DittoDeviceSnapshot snapshot,
            String localTeamId, boolean leaderView,
            TeamMarkerVisibilityMode visibilityMode) {
        String localTeam = safe(localTeamId);
        if (localTeam.length() == 0)
            return true;

        boolean sameTeam = localTeam.equals(safe(snapshot.getTeamId()));
        if (!leaderView)
            return sameTeam;

        TeamMarkerVisibilityMode mode = visibilityMode == null
                ? TeamMarkerVisibilityMode.MY_TEAM : visibilityMode;
        switch (mode) {
            case ME_ONLY:
                return false;
            case LEADERS:
                return sameTeam || isLeader(snapshot);
            case ALL_VISIBLE:
                return true;
            case MY_TEAM:
            default:
                return sameTeam;
        }
    }

    private void importSnapshot(DittoDeviceSnapshot snapshot,
            boolean markerVisible, boolean showCallsigns) {
        CotEvent event = createCotEvent(snapshot);
        if (event == null)
            return;
        try {
            Bundle extras = new Bundle();
            extras.putString("from", "SARtak Ditto");
            CommsMapComponent.ImportResult result = ImporterManager
                    .importData(event, extras);
            if (result == CommsMapComponent.ImportResult.FAILURE)
                upsertFallbackMarker(snapshot, markerVisible, showCallsigns);
            configureImportedMarker(snapshot, markerVisible, showCallsigns);
        } catch (IOException exception) {
            upsertFallbackMarker(snapshot, markerVisible, showCallsigns);
            Log.w(TAG, "ATAK CoT import failed for Ditto device", exception);
        } catch (Exception exception) {
            upsertFallbackMarker(snapshot, markerVisible, showCallsigns);
            Log.w(TAG, "ATAK marker update failed for Ditto device",
                    exception);
        }
    }

    private CotEvent createCotEvent(DittoDeviceSnapshot snapshot) {
        long updatedAt = snapshot.getUpdatedAt() > 0L
                ? snapshot.getUpdatedAt() : System.currentTimeMillis();
        CoordinatedTime eventTime = new CoordinatedTime(updatedAt);
        CotDetail root = new CotDetail();

        CotDetail contact = new CotDetail("contact");
        contact.setAttribute("callsign", displayCallsign(snapshot));
        root.addChild(contact);

        CotDetail uid = new CotDetail("uid");
        uid.setAttribute("Droid", displayCallsign(snapshot));
        root.addChild(uid);

        CotDetail track = new CotDetail("track");
        track.setAttribute("course", String.valueOf(snapshot.getHeading()));
        track.setAttribute("speed", String.valueOf(snapshot.getSpeed()));
        root.addChild(track);

        CotEvent event = new CotEvent();
        event.setUID(snapshot.getUid());
        event.setType(isLeader(snapshot) ? "a-f-G-U-C" : "a-f-G-U-C-I");
        event.setTime(eventTime);
        event.setStart(eventTime);
        event.setStale(new CoordinatedTime().addSeconds(90));
        event.setHow(CotEvent.HOW_MACHINE_GENERATED);
        event.setPoint(new CotPoint(snapshot.getLatitude(),
                snapshot.getLongitude(), safeAltitude(snapshot.getAltitude()),
                safeAccuracy(snapshot.getAccuracy()), CotPoint.UNKNOWN));
        event.setDetail(root);
        return event;
    }

    private void configureImportedMarker(DittoDeviceSnapshot snapshot,
            boolean markerVisible, boolean showCallsigns) {
        MapItem item = mapView.getRootGroup().deepFindUID(snapshot.getUid());
        if (!(item instanceof Marker))
            return;
        configureMarker((Marker) item, snapshot, markerVisible, showCallsigns);
    }

    private void upsertFallbackMarker(DittoDeviceSnapshot snapshot,
            boolean markerVisible, boolean showCallsigns) {
        MapGroup group = ensureFallbackGroup();
        MapItem item = mapView.getRootGroup().deepFindUID(snapshot.getUid());
        Marker marker = item instanceof Marker ? (Marker) item : null;
        if (marker == null) {
            marker = new Marker(new GeoPoint(snapshot.getLatitude(),
                    snapshot.getLongitude(), safeAltitude(snapshot
                            .getAltitude())), snapshot.getUid());
            group.addItem(marker);
        }
        configureMarker(marker, snapshot, markerVisible, showCallsigns);
    }

    private void configureMarker(Marker marker, DittoDeviceSnapshot snapshot,
            boolean markerVisible, boolean showCallsigns) {
        marker.setPoint(new GeoPoint(snapshot.getLatitude(),
                snapshot.getLongitude(), safeAltitude(snapshot.getAltitude())));
        marker.setTitle(showCallsigns ? displayCallsign(snapshot) : "");
        marker.setType(isLeader(snapshot) ? "a-f-G-U-C" : "a-f-G-U-C-I");
        marker.setAlwaysShowText(showCallsigns);
        marker.setMetaString("callsign",
                showCallsigns ? displayCallsign(snapshot) : "");
        marker.setMetaString("atakRoleType", roleName(snapshot));
        marker.setMetaString("sartak.ditto.contact", "true");
        marker.setMetaString("sartak.operation.id", snapshot.getOperationId());
        marker.setMetaString("sartak.team.id", safe(snapshot.getTeamId()));
        marker.setMetaString("sartak.team.name", safe(snapshot.getTeamName()));
        marker.setMetaString("sartak.team.color.name",
                displayTeamColorName(snapshot));
        marker.setMetaString("sartak.member.color.name",
                safe(snapshot.getMemberColorName()));
        marker.setMetaString("sartak.member.role", roleName(snapshot));
        marker.setMetaBoolean("archive", false);
        marker.setMetaBoolean("editable", false);
        marker.setMetaBoolean("movable", false);
        marker.setMetaBoolean("removable", false);
        marker.setMetaBoolean("adapt_marker_icon", true);
        marker.setVisible(markerVisible);
        if (snapshot.isHeadingReliable())
            marker.setTrack(snapshot.getHeading(), snapshot.getSpeed());
        else
            marker.setTrack(0.0, 0.0);
    }

    private void staleMissingMarkers(Set<String> activeUids) {
        for (String uid : new HashSet<>(managedUids)) {
            if (activeUids.contains(uid))
                continue;
            MapItem item = mapView.getRootGroup().deepFindUID(uid);
            if (item != null
                    && "true".equals(item.getMetaString(
                            "sartak.ditto.contact", ""))) {
                item.setMetaBoolean("stale", true);
                item.setVisible(false);
            }
        }
    }

    private void configureExistingMarker(DittoDeviceSnapshot snapshot,
            boolean visible, boolean showCallsigns) {
        if (snapshot == null || snapshot.getUid() == null
                || snapshot.getUid().length() == 0)
            return;
        MapItem item = mapView.getRootGroup().deepFindUID(snapshot.getUid());
        if (item != null
                && "true".equals(item.getMetaString(
                        "sartak.ditto.contact", ""))
                && item instanceof Marker)
            configureMarker((Marker) item, snapshot, visible, showCallsigns);
    }

    private MapGroup ensureFallbackGroup() {
        MapGroup cursor = mapView.getRootGroup().findMapGroup(
                "Cursor on Target");
        if (cursor == null)
            cursor = mapView.getRootGroup().addGroup("Cursor on Target");
        MapGroup friendly = cursor.findMapGroup("Friendly");
        if (friendly == null)
            friendly = cursor.addGroup("Friendly");
        String teamName = "SARtak Ditto Contacts";
        MapGroup team = friendly.findMapGroup(teamName);
        if (team == null)
            team = friendly.addGroup(teamName);
        return team;
    }

    private String displayCallsign(DittoDeviceSnapshot snapshot) {
        String callsign = safe(snapshot.getCallsign());
        return callsign.length() == 0 ? snapshot.getUid() : callsign;
    }

    private String displayTeamColorName(DittoDeviceSnapshot snapshot) {
        String team = safe(snapshot.getTeamColorName());
        return team.length() == 0 ? "Unassigned" : team;
    }

    private String roleName(DittoDeviceSnapshot snapshot) {
        return isLeader(snapshot) ? "Team Lead" : "Team Member";
    }

    private boolean isLeader(DittoDeviceSnapshot snapshot) {
        String role = safe(snapshot.getRole()).toLowerCase();
        return role.equals("team lead") || role.equals("team leader")
                || role.equals("leader")
                || matches(snapshot.getUid(), snapshot.getLeaderUid());
    }

    private boolean matches(String first, String second) {
        return safe(first).length() > 0 && safe(first).equals(safe(second));
    }

    private double safeAltitude(double altitude) {
        if (Double.isNaN(altitude) || Double.isInfinite(altitude))
            return CotPoint.UNKNOWN;
        return altitude;
    }

    private double safeAccuracy(double accuracy) {
        if (Double.isNaN(accuracy) || Double.isInfinite(accuracy)
                || accuracy <= 0.0)
            return CotPoint.UNKNOWN;
        return accuracy;
    }

    private boolean isValidCoordinate(double latitude, double longitude) {
        return !Double.isNaN(latitude) && !Double.isInfinite(latitude)
                && !Double.isNaN(longitude) && !Double.isInfinite(longitude)
                && latitude >= -90.0 && latitude <= 90.0
                && longitude >= -180.0 && longitude <= 180.0;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
