package com.atakmap.android.plugintemplate.runtime;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shares real user-created ATAK point markers through SARtak's operation sync.
 *
 * <p>The manager deliberately ignores ATAK contact markers and SARtak overlay
 * items so it does not re-publish live position markers, search grids, team
 * overlays, or markers it imported from another device.
 */
public class SharedMapMarkerSyncManager {

    private static final String TAG = "SARtakSharedMarkers";
    private static final String GROUP_NAME = "SARtak Shared Markers";
    private static final long MIN_SCAN_INTERVAL_MS = 5000L;

    private final MapView mapView;
    private final IdentityManager identityManager;
    private final DittoSyncManager dittoSyncManager;
    private final Map<String, String> publishedSignatures = new HashMap<>();
    private final Map<String, Long> appliedRemoteUpdates = new HashMap<>();

    private String operationId = "";
    private long lastScanTime;

    public SharedMapMarkerSyncManager(MapView mapView,
            IdentityManager identityManager, DittoSyncManager dittoSyncManager) {
        this.mapView = mapView;
        this.identityManager = identityManager;
        this.dittoSyncManager = dittoSyncManager;
    }

    public void setOperationId(String operationId) {
        this.operationId = safe(operationId);
        publishedSignatures.clear();
        appliedRemoteUpdates.clear();
    }

    public void sync(String teamId) {
        applyRemoteMarkers();
        publishLocalMarkersIfDue(teamId);
    }

    private void publishLocalMarkersIfDue(String teamId) {
        if (operationId.length() == 0 || dittoSyncManager == null
                || !dittoSyncManager.isStarted())
            return;
        long now = System.currentTimeMillis();
        if (now - lastScanTime < MIN_SCAN_INTERVAL_MS)
            return;
        lastScanTime = now;

        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        if (identity == null || !identity.isResolved())
            return;
        MapGroup root = mapView == null ? null : mapView.getRootGroup();
        if (root == null)
            return;
        Collection<MapItem> items = root.getItemsRecursive();
        if (items == null)
            return;

        for (MapItem item : items) {
            if (!(item instanceof Marker) || !isShareableMarker(item))
                continue;
            Marker marker = (Marker) item;
            GeoPoint point = marker.getPoint();
            if (!isValid(point))
                continue;
            String uid = safe(marker.getUID());
            String signature = signatureFor(marker, point);
            if (signature.equals(publishedSignatures.get(uid)))
                continue;
            boolean published = dittoSyncManager.publishSharedMapMarker(
                    new SharedMapMarkerMessage(
                            "shared-marker-" + operationId + "-" + uid,
                            uid, safe(marker.getType()),
                            titleFor(marker, uid), point.getLatitude(),
                            point.getLongitude(), altitudeFor(point),
                            identity.getUid(), identity.getCallsign(),
                            safe(teamId), operationId, now));
            if (published)
                publishedSignatures.put(uid, signature);
        }
    }

    private void applyRemoteMarkers() {
        if (operationId.length() == 0 || dittoSyncManager == null)
            return;
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        String selfUid = identity == null ? "" : identity.getUid();
        List<SharedMapMarkerMessage> messages =
                dittoSyncManager.getSharedMapMarkers();
        for (SharedMapMarkerMessage message : messages) {
            if (message == null || !operationId.equals(message.getOperationId()))
                continue;
            if (selfUid.length() > 0
                    && selfUid.equals(message.getSenderUid()))
                continue;
            if (!isValid(message.getLatitude(), message.getLongitude()))
                continue;
            Long applied = appliedRemoteUpdates.get(message.getMarkerUid());
            if (applied != null && applied >= message.getUpdatedAt())
                continue;
            upsertRemoteMarker(message);
            appliedRemoteUpdates.put(message.getMarkerUid(),
                    message.getUpdatedAt());
        }
    }

    private void upsertRemoteMarker(SharedMapMarkerMessage message) {
        try {
            MapGroup group = ensureGroup();
            if (group == null)
                return;
            MapItem existing = mapView.getRootGroup()
                    .deepFindUID(message.getMarkerUid());
            Marker marker = existing instanceof Marker ? (Marker) existing
                    : null;
            if (marker == null) {
                marker = new Marker(new GeoPoint(message.getLatitude(),
                        message.getLongitude(), message.getAltitude()),
                        message.getMarkerUid());
                group.addItem(marker);
            }
            marker.setPoint(new GeoPoint(message.getLatitude(),
                    message.getLongitude(), message.getAltitude()));
            marker.setType(message.getMarkerType().length() == 0
                    ? "b-m-p-s-p-i" : message.getMarkerType());
            marker.setTitle(message.getTitle());
            marker.setMetaString("callsign", message.getTitle());
            marker.setMetaString("entry", "sartak-shared");
            marker.setMetaString("sartak.shared.marker", "true");
            marker.setMetaString("sartak.operation.id",
                    message.getOperationId());
            marker.setMetaString("sartak.sender.uid", message.getSenderUid());
            marker.setMetaString("sartak.sender.callsign",
                    message.getSenderCallsign());
            marker.setMetaString("sartak.team.id", message.getTeamId());
            marker.setMetaBoolean("archive", true);
            marker.setMetaBoolean("editable", true);
            marker.setMetaBoolean("movable", true);
            marker.setMetaBoolean("removable", true);
            marker.setVisible(true);
        } catch (Exception exception) {
            Log.w(TAG, "Unable to apply shared map marker", exception);
        }
    }

    private MapGroup ensureGroup() {
        if (mapView == null || mapView.getRootGroup() == null)
            return null;
        MapGroup group = mapView.getRootGroup().findMapGroup(GROUP_NAME);
        if (group == null)
            group = mapView.getRootGroup().addGroup(GROUP_NAME);
        group.setMetaBoolean("addToObjList", true);
        return group;
    }

    private boolean isShareableMarker(MapItem item) {
        String uid = safe(item.getUID());
        if (uid.length() == 0 || uid.startsWith("sartak-")
                || uid.startsWith("ANDROID-")
                || uid.equals(MapView.getDeviceUid()))
            return false;
        if ("sartak".equals(safe(item.getMetaString("entry", "")))
                || "sartak-shared".equals(safe(item.getMetaString("entry",
                        "")))
                || "true".equals(safe(item.getMetaString(
                        "sartak.ditto.contact", "")))
                || "true".equals(safe(item.getMetaString(
                        "sartak.shared.marker", ""))))
            return false;
        String type = item instanceof Marker ? safe(((Marker) item).getType())
                : "";
        return !type.equals("a-f-G-U-C")
                && !type.startsWith("a-f-G-U-C-")
                && !type.equals("a-f-G-U-C-I")
                && !type.startsWith("a-f-G-U-C-I-");
    }

    private String signatureFor(Marker marker, GeoPoint point) {
        return safe(marker.getType()) + "|" + titleFor(marker,
                safe(marker.getUID())) + "|" + rounded(point.getLatitude())
                + "|" + rounded(point.getLongitude()) + "|"
                + rounded(altitudeFor(point));
    }

    private String titleFor(Marker marker, String uid) {
        String title = safe(marker.getTitle());
        if (title.length() == 0)
            title = safe(marker.getMetaString("callsign", ""));
        return title.length() == 0 ? uid : title;
    }

    private String rounded(double value) {
        return String.format(java.util.Locale.US, "%.7f", value);
    }

    private boolean isValid(GeoPoint point) {
        return point != null && point.isValid()
                && isValid(point.getLatitude(), point.getLongitude());
    }

    private boolean isValid(double latitude, double longitude) {
        return !Double.isNaN(latitude) && !Double.isInfinite(latitude)
                && !Double.isNaN(longitude) && !Double.isInfinite(longitude)
                && latitude >= -90.0 && latitude <= 90.0
                && longitude >= -180.0 && longitude <= 180.0;
    }

    private double altitudeFor(GeoPoint point) {
        if (point == null || !point.isAltitudeValid())
            return 0.0;
        return point.getAltitude();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
