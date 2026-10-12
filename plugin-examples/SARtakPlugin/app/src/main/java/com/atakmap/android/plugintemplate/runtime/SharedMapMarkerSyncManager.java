package com.atakmap.android.plugintemplate.runtime;

import android.app.AlertDialog;
import android.os.SystemClock;
import android.widget.Toast;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final SharedMarkerStateStore stateStore;

    private String operationId = "";
    private long lastScanTime;
    // Markers imported for other operations, by operation, still to take
    // off the map. ATAK may reload saved ones a while after startup.
    private Map<String, Set<String>> foreignImports = new HashMap<>();
    private AlertDialog existingMarkersPrompt;

    public SharedMapMarkerSyncManager(MapView mapView,
            IdentityManager identityManager, DittoSyncManager dittoSyncManager,
            SharedMarkerStateStore stateStore) {
        this.mapView = mapView;
        this.identityManager = identityManager;
        this.dittoSyncManager = dittoSyncManager;
        this.stateStore = stateStore;
    }

    public void setOperationId(String operationId) {
        String next = safe(operationId);
        if (next.equals(this.operationId)
                && next.equals(reconciler.getOperationId()))
            return;
        this.operationId = next;
        long now = System.currentTimeMillis();
        reconciler.reset(next, now);
        lastScanTime = 0L;
        if (existingMarkersPrompt != null && existingMarkersPrompt.isShowing())
            existingMarkersPrompt.dismiss();
        existingMarkersPrompt = null;
        if (stateStore == null)
            return;
        if (next.length() > 0) {
            reconciler.setOperationJoinedAt(stateStore.getOrRecordJoinedAt(
                    next, now));
            reconciler.setExistingMarkers(stateStore.getExistingMarkers(next));
            reconciler.restorePresent(stateStore.getPresent(next));
        }
        foreignImports = SharedMarkerStateStore.foreignImports(
                stateStore.getImportedByOperation(), next);
        removeForeignImports();
    }

    /**
     * Takes markers imported for other operations off the map (finding
     * T4-2), so leaving or switching operation doesn't leave the old one's
     * markers behind. They come back from Ditto on returning to it.
     */
    private void removeForeignImports() {
        if (foreignImports.isEmpty() || mapView == null
                || mapView.getRootGroup() == null)
            return;
        for (Map.Entry<String, Set<String>> entry : foreignImports.entrySet()) {
            List<String> removed = new ArrayList<>();
            for (String uid : entry.getValue()) {
                MapItem item = findItem(uid);
                if (item == null)
                    continue;
                try {
                    item.removeFromGroup();
                    removed.add(uid);
                } catch (Exception exception) {
                    Log.w(TAG, "Unable to remove another operation's marker",
                            exception);
                }
            }
            if (!removed.isEmpty()) {
                stateStore.forgetPresent(entry.getKey(), removed);
                Log.i(TAG, "removed " + removed.size()
                        + " markers of another operation");
            }
        }
    }

    /**
     * @param canDeleteOthersMarkers true for leaders and HQ; a searcher may
     *            only delete shared markers they created (finding T4-6)
     */
    public void sync(String teamId, boolean canDeleteOthersMarkers) {
        reconciler.setCanDeleteOthersMarkers(canDeleteOthersMarkers);
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
            removeForeignImports();
            result = reconciler.sync(documents, localMap, publisher,
                    selfUid, identity.getCallsign(), MapView.getDeviceUid(),
                    teamId, now);
            if (stateStore != null) {
                stateStore.savePresent(operationId,
                        reconciler.getPresentForRestart());
                // Includes imports ATAK saved itself (any the user edited),
                // which come back after a restart without being imported.
                stateStore.addImported(operationId,
                        reconciler.getOthersPresent());
            }
            tellAboutRefusedDeletes(result.refusedDeletes);
            if (result.existingWaiting > 0)
                askAboutExistingMarkers(operationId, result.existingWaiting);
        } else {
            result = reconciler.applyRemote(documents, localMap, selfUid);
        }
        long elapsed = SystemClock.elapsedRealtime() - started;
        if (result.changedAnything())
            Log.i(TAG, "shared markers " + result + " docs="
                    + documents.size());
        if (BuildConfig.DEBUG && (scanDue || elapsed >= 16L))
            Log.d(PERF_TAG, "shared markers ms=" + elapsed + " scan="
                    + scanDue + " docs=" + documents.size() + " " + result);
    }

    private void tellAboutRefusedDeletes(List<String> titles) {
        if (titles.isEmpty())
            return;
        String what = titles.size() == 1 ? "\"" + titles.get(0) + "\""
                : titles.size() + " shared markers";
        Toast.makeText(mapView.getContext(), "Only the creator, a team leader"
                + " or HQ can delete " + what + ". It has been put back.",
                Toast.LENGTH_LONG).show();
    }

    /**
     * Asks once per operation whether to share the user's own markers from
     * before they joined (finding T4-7). Until they answer, those markers
     * stay on this device only.
     */
    private void askAboutExistingMarkers(final String forOperation,
            int count) {
        if (existingMarkersPrompt != null || stateStore == null
                || mapView == null)
            return;
        existingMarkersPrompt = new AlertDialog.Builder(mapView.getContext())
                .setTitle("Shared markers")
                .setMessage("Share " + count + (count == 1
                        ? " existing marker" : " existing markers")
                        + " with this operation?\n\nThese are markers you made"
                        + " on this device before joining it. Markers you make"
                        + " from now on are shared either way.")
                .setCancelable(false)
                .setPositiveButton("Share", (dialog, which) -> answerExisting(
                        forOperation,
                        SharedMapMarkerReconciler.ExistingMarkers.SHARE))
                .setNegativeButton("Keep on this device",
                        (dialog, which) -> answerExisting(forOperation,
                                SharedMapMarkerReconciler.ExistingMarkers
                                        .KEEP_PRIVATE))
                .show();
    }

    private void answerExisting(String forOperation,
            SharedMapMarkerReconciler.ExistingMarkers answer) {
        stateStore.setExistingMarkers(forOperation, answer);
        if (!forOperation.equals(operationId))
            return;
        existingMarkersPrompt = null;
        reconciler.setExistingMarkers(answer);
        // Share straight away rather than on the next 9.7 s refresh.
        lastScanTime = 0L;
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

    /** Count and newest markers, for the Alerts tab on every role. */
    public String summarizeLiveMarkers(int limit) {
        return SharedMapMarkerReconciler.summarize(getLiveMarkers(), limit,
                TimeZone.getDefault());
    }

    private final SharedMapMarkerReconciler.Publisher publisher =
            new SharedMapMarkerReconciler.Publisher() {
                @Override
                public boolean publish(SharedMapMarkerMessage message,
                        Runnable onFailed) {
                    debug("publish", message);
                    return dittoSyncManager.publishSharedMapMarker(message,
                            onMainThread(onFailed));
                }

                @Override
                public boolean publishDeletion(
                        SharedMapMarkerMessage tombstone, Runnable onFailed) {
                    debug("publish-delete", tombstone);
                    return dittoSyncManager.publishSharedMapMarkerDeletion(
                            tombstone, onMainThread(onFailed));
                }
            };

    /** The reconciler runs on the main thread; failures arrive on the writer. */
    private Runnable onMainThread(final Runnable action) {
        if (action == null)
            return null;
        return new Runnable() {
            @Override
            public void run() {
                Log.w(TAG, "shared marker write failed; retrying next scan");
                mapView.post(action);
            }
        };
    }

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
                if (stateStore != null)
                    stateStore.addImported(operationId,
                            message.getMarkerUid());
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
