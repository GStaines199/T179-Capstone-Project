package com.atakmap.android.plugintemplate.runtime;

import android.os.SystemClock;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.plugintemplate.plugin.BuildConfig;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TimeZone;

/**
 * Shares real user-created ATAK point markers through SARtak's operation sync.
 *
 * <p>The manager deliberately ignores ATAK contact markers and SARtak overlay
 * items so it does not re-publish live position markers, search grids or team
 * overlays. Markers imported from another device stay shareable so that
 * moves, renames and deletions on any device reach everyone; the rules that
 * prevent echoes, duplicates and resurrection live in
 * {@link SharedMapMarkerReconciler}.
 */
public class SharedMapMarkerSyncManager {

    private static final String TAG = "SARtakSharedMarkers";
    private static final String PERF_TAG = "SARtakPerf";
    private static final String GROUP_NAME = "SARtak Shared Markers";
    private static final long MIN_SCAN_INTERVAL_MS = 5000L;

    // ATAK's own metadata keys (CreatorDetailHandler, ColorDetailHandler,
    // UserIconHandler). Using them means ATAK writes them into its CoT and
    // statesaver like any marker it received over the network.
    static final String META_PARENT_UID = "parent_uid";
    static final String META_PARENT_CALLSIGN = "parent_callsign";
    static final String META_PARENT_TYPE = "parent_type";
    static final String META_PRODUCTION_TIME = "production_time";
    static final String META_COLOR = "color";
    static final String META_ICONSET_PATH = "IconsetPath";

    private final MapView mapView;
    private final IdentityManager identityManager;
    private final DittoSyncManager dittoSyncManager;
    private final SharedMapMarkerReconciler reconciler =
            new SharedMapMarkerReconciler();
    private final AtakLocalMap localMap = new AtakLocalMap();
    private final SharedMarkerImageSync imageSync;

    private String operationId = "";
    private long lastScanTime;

    public SharedMapMarkerSyncManager(MapView mapView,
            IdentityManager identityManager, DittoSyncManager dittoSyncManager) {
        this.mapView = mapView;
        this.identityManager = identityManager;
        this.dittoSyncManager = dittoSyncManager;
        this.imageSync = dittoSyncManager == null ? null
                : new SharedMarkerImageSync(dittoSyncManager);
    }

    public void setOperationId(String operationId) {
        String next = safe(operationId);
        if (next.equals(this.operationId)
                && next.equals(reconciler.getOperationId()))
            return;
        this.operationId = next;
        reconciler.reset(next, System.currentTimeMillis());
        if (imageSync != null)
            imageSync.setOperationId(next);
        lastScanTime = 0L;
    }

    public void sync(String teamId) {
        if (operationId.length() == 0 || dittoSyncManager == null
                || mapView == null || mapView.getRootGroup() == null)
            return;
        // Until the observer has delivered the local store once, "no
        // document" could just mean "not loaded yet"; publishing then would
        // overwrite edits other devices made while this one was off.
        if (!dittoSyncManager.hasSharedMapMarkerSnapshot())
            return;
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        String selfUid = identity == null ? "" : identity.getUid();
        List<SharedMapMarkerMessage> documents =
                dittoSyncManager.getSharedMapMarkers();
        long now = System.currentTimeMillis();
        boolean scanDue = now - lastScanTime >= MIN_SCAN_INTERVAL_MS
                && dittoSyncManager.isStarted() && identity != null
                && identity.isResolved();
        long started = SystemClock.elapsedRealtime();
        SharedMapMarkerReconciler.Result result;
        if (scanDue) {
            lastScanTime = now;
            result = reconciler.sync(documents, localMap, publisher,
                    selfUid, identity.getCallsign(), MapView.getDeviceUid(),
                    teamId, now);
        } else {
            result = reconciler.applyRemote(documents, localMap, selfUid);
        }
        if (scanDue && imageSync != null)
            imageSync.sync(localLiveMarkerUids(), selfUid, now);
        long elapsed = SystemClock.elapsedRealtime() - started;
        if (result.changedAnything())
            Log.i(TAG, "shared markers " + result + " docs="
                    + documents.size());
        if (BuildConfig.DEBUG && (scanDue || elapsed >= 16L))
            Log.d(PERF_TAG, "shared markers ms=" + elapsed + " scan="
                    + scanDue + " docs=" + documents.size() + " " + result);
    }

    private List<String> localLiveMarkerUids() {
        List<String> uids = new ArrayList<>();
        for (SharedMapMarkerMessage marker : getLiveMarkers()) {
            if (findItem(marker.getMarkerUid()) != null)
                uids.add(marker.getMarkerUid());
        }
        return uids;
    }

    /** Live shared markers of this operation, newest edit first. */
    public List<SharedMapMarkerMessage> getLiveMarkers() {
        if (dittoSyncManager == null)
            return new ArrayList<>();
        return SharedMapMarkerReconciler.liveMarkers(
                dittoSyncManager.getSharedMapMarkers(), operationId);
    }

    /** "Title - by Alpha 14:05 11 Oct (edited by Bravo 14:20 11 Oct)". */
    public List<String> describeLiveMarkers(int limit) {
        List<String> lines = new ArrayList<>();
        for (SharedMapMarkerMessage marker : getLiveMarkers()) {
            if (lines.size() >= limit)
                break;
            lines.add(SharedMapMarkerReconciler.describe(marker,
                    TimeZone.getDefault()));
        }
        return lines;
    }

    private final SharedMapMarkerReconciler.Publisher publisher =
            new SharedMapMarkerReconciler.Publisher() {
                @Override
                public boolean publish(SharedMapMarkerMessage message) {
                    debug("publish", message);
                    return dittoSyncManager.publishSharedMapMarker(message);
                }

                @Override
                public boolean publishDeletion(
                        SharedMapMarkerMessage tombstone) {
                    debug("publish-delete", tombstone);
                    return dittoSyncManager
                            .publishSharedMapMarkerDeletion(tombstone);
                }
            };

    private class AtakLocalMap implements SharedMapMarkerReconciler.LocalMap {

        @Override
        public List<SharedMapMarkerMessage> scanShareable() {
            List<SharedMapMarkerMessage> snapshots = new ArrayList<>();
            MapGroup root = mapView.getRootGroup();
            Collection<MapItem> items = root == null ? null
                    : root.getItemsRecursive();
            if (items == null)
                return snapshots;
            for (MapItem item : items) {
                if (!(item instanceof Marker) || !isShareableMarker(item))
                    continue;
                SharedMapMarkerMessage snapshot = snapshot((Marker) item);
                if (snapshot != null)
                    snapshots.add(snapshot);
            }
            return snapshots;
        }

        @Override
        public boolean exists(String markerUid) {
            return findItem(markerUid) != null;
        }

        @Override
        public SharedMapMarkerMessage find(String markerUid) {
            MapItem item = findItem(markerUid);
            return item instanceof Marker ? snapshot((Marker) item) : null;
        }

        @Override
        public void remove(String markerUid) {
            MapItem item = findItem(markerUid);
            if (item == null)
                return;
            if (BuildConfig.DEBUG)
                Log.d(TAG, "remove deleted marker uid=" + markerUid);
            try {
                item.removeFromGroup();
            } catch (Exception exception) {
                Log.w(TAG, "Unable to remove deleted shared marker",
                        exception);
            }
        }

        @Override
        public SharedMapMarkerMessage apply(SharedMapMarkerMessage message) {
            debug("apply", message);
            Marker marker = upsertRemoteMarker(message);
            return marker == null ? null : snapshot(marker);
        }
    }

    private static void debug(String action, SharedMapMarkerMessage message) {
        if (!BuildConfig.DEBUG)
            return;
        Log.d(TAG, action + " uid=" + message.getMarkerUid() + " title="
                + message.getTitle() + " type=" + message.getMarkerType()
                + " pos=" + message.getLatitude() + "," + message.getLongitude()
                + " creator=" + message.getCreatorCallsign() + "/"
                + message.getCreatorUid() + " created="
                + message.getCreatedAt() + " editor="
                + message.getSenderCallsign() + " remarks="
                + message.getRemarks().length() + "ch color="
                + (message.hasColor() ? Integer.toHexString(message.getColor())
                        : "-") + " icon=" + message.getIconsetPath()
                + (message.isDeleted() ? " DELETED by "
                        + message.getDeletedByCallsign() : ""));
    }

    private MapItem findItem(String markerUid) {
        if (mapView == null || mapView.getRootGroup() == null
                || safe(markerUid).length() == 0)
            return null;
        return mapView.getRootGroup().deepFindUID(markerUid);
    }

    private SharedMapMarkerMessage snapshot(Marker marker) {
        GeoPoint point = marker.getPoint();
        if (!isValid(point))
            return null;
        String uid = safe(marker.getUID());
        boolean hasColor = marker.hasMetaValue(META_COLOR);
        return new SharedMapMarkerMessage.Builder()
                .markerUid(uid)
                .markerType(safe(marker.getType()))
                .title(titleFor(marker, uid))
                .position(point.getLatitude(), point.getLongitude(),
                        altitudeFor(point))
                .remarks(safe(marker.getRemarks()))
                .color(hasColor, hasColor
                        ? marker.getMetaInteger(META_COLOR, 0) : 0)
                .iconsetPath(marker.getMetaString(META_ICONSET_PATH, ""))
                .creator(marker.getMetaString(META_PARENT_UID, ""),
                        marker.getMetaString(META_PARENT_CALLSIGN, ""),
                        parseProductionTime(marker.getMetaString(
                                META_PRODUCTION_TIME, "")))
                .operationId(operationId)
                .build();
    }

    private Marker upsertRemoteMarker(SharedMapMarkerMessage message) {
        try {
            MapGroup group = ensureGroup();
            if (group == null)
                return null;
            MapItem existing = findItem(message.getMarkerUid());
            if (existing != null && !(existing instanceof Marker))
                return null;
            GeoPoint point = new GeoPoint(message.getLatitude(),
                    message.getLongitude(), message.getAltitude());
            Marker marker = (Marker) existing;
            boolean created = marker == null;
            if (created) {
                marker = new Marker(point, message.getMarkerUid());
                tagImportedMarker(marker, message);
                group.addItem(marker);
            }
            marker.setPoint(point);
            marker.setType(message.getMarkerType().length() == 0
                    ? SharedMapMarkerMessage.DEFAULT_TYPE
                    : message.getMarkerType());
            marker.setTitle(message.getTitle());
            marker.setMetaString("callsign", message.getTitle());
            marker.setRemarks(message.getRemarks());
            if (message.hasColor()) {
                marker.setMetaInteger(META_COLOR, message.getColor());
                marker.setColor(message.getColor());
            } else if (marker.hasMetaValue(META_COLOR)) {
                marker.removeMetaData(META_COLOR);
            }
            if (message.getIconsetPath().length() > 0)
                marker.setMetaString(META_ICONSET_PATH,
                        message.getIconsetPath());
            else if (marker.hasMetaValue(META_ICONSET_PATH))
                marker.removeMetaData(META_ICONSET_PATH);
            applyCreator(marker, message);
            marker.setMetaString("sartak.last.editor.uid",
                    message.getSenderUid());
            marker.setMetaString("sartak.last.editor.callsign",
                    message.getSenderCallsign());
            marker.setMetaLong("sartak.last.edit.time",
                    message.getUpdatedAt());
            marker.setVisible(true);
            // Let ATAK re-resolve the icon/colour and, for a marker ATAK
            // already saves (e.g. our own original), save the edit too.
            marker.refresh(mapView.getMapEventDispatcher(), null,
                    SharedMapMarkerSyncManager.class);
            if (!created && marker.getMetaBoolean("archive", false)
                    && !"true".equals(marker.getMetaString(
                            "sartak.shared.marker", "")))
                marker.persist(mapView.getMapEventDispatcher(), null,
                        SharedMapMarkerSyncManager.class);
            return marker;
        } catch (Exception exception) {
            Log.w(TAG, "Unable to apply shared map marker", exception);
            return null;
        }
    }

    private void tagImportedMarker(Marker marker,
            SharedMapMarkerMessage message) {
        marker.setMetaString("entry", "sartak-shared");
        marker.setMetaString("sartak.shared.marker", "true");
        marker.setMetaString("sartak.operation.id", message.getOperationId());
        marker.setMetaString("sartak.sender.uid", message.getSenderUid());
        marker.setMetaString("sartak.sender.callsign",
                message.getSenderCallsign());
        marker.setMetaString("sartak.team.id", message.getTeamId());
        marker.setMetaBoolean("archive", true);
        marker.setMetaBoolean("editable", true);
        marker.setMetaBoolean("movable", true);
        marker.setMetaBoolean("removable", true);
    }

    private void applyCreator(Marker marker, SharedMapMarkerMessage message) {
        String creatorUid = message.getCreatorUid().length() > 0
                ? message.getCreatorUid() : message.getSenderUid();
        String creatorCallsign = message.getCreatorUid().length() > 0
                ? message.getCreatorCallsign() : message.getSenderCallsign();
        if (creatorUid.length() > 0) {
            marker.setMetaString(META_PARENT_UID, creatorUid);
            if (!marker.hasMetaValue(META_PARENT_TYPE))
                marker.setMetaString(META_PARENT_TYPE, "a-f-G-U-C");
        }
        if (creatorCallsign.length() > 0)
            marker.setMetaString(META_PARENT_CALLSIGN, creatorCallsign);
        if (message.getCreatedAt() > 0L)
            marker.setMetaString(META_PRODUCTION_TIME, CoordinatedTime.toCot(
                    new CoordinatedTime(message.getCreatedAt())));
        marker.setMetaString("sartak.created.by", creatorCallsign);
        marker.setMetaLong("sartak.created.time", message.getCreatedAt());
    }

    static long parseProductionTime(String value) {
        String text = safe(value);
        if (text.length() == 0)
            return 0L;
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
        }
        try {
            return CoordinatedTime.fromCot(text).getMilliseconds();
        } catch (Exception ignored) {
            return 0L;
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
                || "true".equals(safe(item.getMetaString(
                        "sartak.ditto.contact", ""))))
            return false;
        // Markers imported for another operation in this session belong to
        // that operation.
        String markerOperation = safe(item.getMetaString(
                "sartak.operation.id", ""));
        if (markerOperation.length() > 0
                && !markerOperation.equals(operationId))
            return false;
        // ATAK uses non-archived markers for transient UI, routes, overlays,
        // contacts and generated helpers. Only persisted user map items are
        // eligible evidence; otherwise every ATAK restart can create a fresh
        // set of transient UIDs and publish them as new operation markers.
        if (!item.getMetaBoolean("archive", false))
            return false;
        String type = item instanceof Marker ? safe(((Marker) item).getType())
                : "";
        return !type.equals("a-f-G-U-C")
                && !type.startsWith("a-f-G-U-C-")
                && !type.equals("a-f-G-U-C-I")
                && !type.startsWith("a-f-G-U-C-I-");
    }

    private String titleFor(Marker marker, String uid) {
        String title = safe(marker.getTitle());
        if (title.length() == 0)
            title = safe(marker.getMetaString("callsign", ""));
        return title.length() == 0 ? uid : title;
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

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
