package com.atakmap.android.plugintemplate.runtime;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Decides what to do with shared map markers on one device: which synced
 * documents to apply to the ATAK map, which local markers to publish, and
 * which deletions to propagate. It has no ATAK dependencies so the rules can
 * be tested with a simulated map and Ditto store.
 *
 * <p>Rules:
 * <ul>
 * <li>Every marker is keyed by its ATAK UID, both locally and in Ditto, so a
 * marker can never be imported twice.</li>
 * <li>A tombstoned document always wins: the marker is removed locally and is
 * never applied or published again for this operation.</li>
 * <li>A local marker is published when it differs from the state it had the
 * last time this device was in sync with the document (after publishing,
 * applying a remote edit, or finding it already equal). Comparing with that
 * remembered state rather than with the document stops two devices bouncing
 * an edit back and forth when ATAK normalises a value on import.</li>
 * <li>A marker with no document yet is shared when it was created on this
 * device (ATAK {@code parent_uid}), created during this session, or first
 * seen after the session's first scan. Markers restored from ATAK's own
 * store that other people made are not pushed into the operation.</li>
 * <li>A shared marker that disappears from the map between two scans was
 * deleted on this device; a tombstone is published.</li>
 * </ul>
 */
public class SharedMapMarkerReconciler {

    /** Grace period for "created during this session". */
    static final long SESSION_CREATION_GRACE_MS = 5L * 60L * 1000L;

    public interface LocalMap {
        /** Snapshots of every shareable local marker. */
        List<SharedMapMarkerMessage> scanShareable();

        /** True when any map item with this UID is still on the map. */
        boolean exists(String markerUid);

        /** Snapshot of the marker with this UID, or null. */
        SharedMapMarkerMessage find(String markerUid);

        void remove(String markerUid);

        /**
         * Creates or updates the local marker from a synced document and
         * returns its snapshot afterwards, or null if that failed.
         */
        SharedMapMarkerMessage apply(SharedMapMarkerMessage document);
    }

    /**
     * Writes documents. A true return means the write was accepted (queued),
     * not that it finished; if it later fails, the publisher runs
     * {@code onFailed} so the next scan tries again. {@code onFailed} must be
     * run later on the thread that calls {@link #sync}, never from inside
     * the publish call, since the reconciler is not thread-safe.
     */
    public interface Publisher {
        boolean publish(SharedMapMarkerMessage message, Runnable onFailed);

        boolean publishDeletion(SharedMapMarkerMessage tombstone,
                Runnable onFailed);
    }

    public static class Result {
        public int applied;
        public int removed;
        public int published;
        public int deleted;

        public boolean changedAnything() {
            return applied + removed + published + deleted > 0;
        }

        @Override
        public String toString() {
            return "applied=" + applied + " removed=" + removed
                    + " published=" + published + " deleted=" + deleted;
        }
    }

    private final Map<String, String> syncedSignatures = new HashMap<>();
    private final Map<String, String> appliedVersions = new HashMap<>();
    private final Set<String> tombstoned = new HashSet<>();
    private final Set<String> baseline = new HashSet<>();
    private Set<String> previouslyPresent = new HashSet<>();
    private boolean baselineEstablished;
    private String operationId = "";
    private long sessionStartedAt;

    public void reset(String operationId, long sessionStartedAt) {
        this.operationId = operationId == null ? "" : operationId.trim();
        this.sessionStartedAt = sessionStartedAt;
        syncedSignatures.clear();
        appliedVersions.clear();
        tombstoned.clear();
        baseline.clear();
        previouslyPresent = new HashSet<>();
        baselineEstablished = false;
    }

    public String getOperationId() {
        return operationId;
    }

    /**
     * A publish of this marker failed after it was queued. Forgets that the
     * marker is in sync, unless it has changed or synced again since, so the
     * next scan publishes it again.
     */
    void publishFailed(String failedOperationId, String markerUid,
            String signature) {
        if (!operationId.equals(failedOperationId))
            return;
        if (signature.equals(syncedSignatures.get(markerUid)))
            syncedSignatures.remove(markerUid);
    }

    /**
     * A tombstone for this marker failed after it was queued. Puts the marker
     * back to "shared, and gone from the map since the last scan", so the
     * next scan sends the deletion again.
     */
    void deletionFailed(String failedOperationId, String markerUid) {
        if (!operationId.equals(failedOperationId)
                || !tombstoned.remove(markerUid))
            return;
        if (!syncedSignatures.containsKey(markerUid))
            syncedSignatures.put(markerUid, "");
        previouslyPresent.add(markerUid);
    }

    /** Applies synced documents to the map. Safe to call at any rate. */
    public Result applyRemote(List<SharedMapMarkerMessage> documents,
            LocalMap local, String selfUid) {
        Result result = new Result();
        applyRemote(index(documents), local, safe(selfUid), result);
        return result;
    }

    /**
     * Applies synced documents, then publishes local creates, edits and
     * deletions. {@code documents} must be the full current snapshot of the
     * operation's marker documents.
     */
    public Result sync(List<SharedMapMarkerMessage> documents, LocalMap local,
            Publisher publisher, String selfUid, String selfCallsign,
            String deviceUid, String teamId, long now) {
        Result result = new Result();
        if (operationId.length() == 0)
            return result;
        Map<String, SharedMapMarkerMessage> byUid = index(documents);
        applyRemote(byUid, local, safe(selfUid), result);
        publishLocal(byUid, local, publisher, safe(selfUid),
                safe(selfCallsign), safe(deviceUid), safe(teamId), now,
                result);
        return result;
    }

    private Map<String, SharedMapMarkerMessage> index(
            List<SharedMapMarkerMessage> documents) {
        Map<String, SharedMapMarkerMessage> byUid = new LinkedHashMap<>();
        if (documents == null)
            return byUid;
        for (SharedMapMarkerMessage document : documents) {
            if (document == null || document.getMarkerUid().length() == 0
                    || !operationId.equals(document.getOperationId()))
                continue;
            byUid.put(document.getMarkerUid(), document);
        }
        return byUid;
    }

    private void applyRemote(Map<String, SharedMapMarkerMessage> byUid,
            LocalMap local, String selfUid, Result result) {
        if (operationId.length() == 0)
            return;
        for (SharedMapMarkerMessage document : byUid.values()) {
            String uid = document.getMarkerUid();
            if (document.isDeleted()) {
                tombstoned.add(uid);
                syncedSignatures.remove(uid);
                previouslyPresent.remove(uid);
                if (local.exists(uid)) {
                    local.remove(uid);
                    result.removed++;
                }
                continue;
            }
            if (tombstoned.contains(uid))
                continue;
            String version = document.versionSignature();
            if (version.equals(appliedVersions.get(uid)))
                continue;
            appliedVersions.put(uid, version);
            // Our own writes are already on the map; a difference between
            // the two is a local edit that publishLocal() will send.
            if (selfUid.length() > 0 && selfUid.equals(document.getSenderUid()))
                continue;
            if (!document.hasValidPosition())
                continue;
            SharedMapMarkerMessage current = local.find(uid);
            if (current != null && current.contentSignature()
                    .equals(document.contentSignature())) {
                syncedSignatures.put(uid, current.contentSignature());
                continue;
            }
            SharedMapMarkerMessage after = local.apply(document);
            if (after == null) {
                appliedVersions.remove(uid);
                continue;
            }
            syncedSignatures.put(uid, after.contentSignature());
            result.applied++;
        }
    }

    private void publishLocal(Map<String, SharedMapMarkerMessage> byUid,
            LocalMap local, Publisher publisher, String selfUid,
            String selfCallsign, String deviceUid, String teamId, long now,
            Result result) {
        List<SharedMapMarkerMessage> snapshots = local.scanShareable();
        Set<String> present = new HashSet<>();
        for (SharedMapMarkerMessage snapshot : snapshots) {
            String uid = snapshot.getMarkerUid();
            if (uid.length() == 0 || !snapshot.hasValidPosition())
                continue;
            if (tombstoned.contains(uid)) {
                local.remove(uid);
                result.removed++;
                continue;
            }
            present.add(uid);
            boolean firstSeenInBaseline = !baselineEstablished;
            if (firstSeenInBaseline)
                baseline.add(uid);
            String signature = snapshot.contentSignature();
            SharedMapMarkerMessage document = byUid.get(uid);
            String synced = syncedSignatures.get(uid);
            if (synced != null) {
                if (synced.equals(signature))
                    continue;
            } else if (document != null) {
                if (document.contentSignature().equals(signature)) {
                    syncedSignatures.put(uid, signature);
                    continue;
                }
            } else if (!shouldShareNewMarker(snapshot, deviceUid)) {
                continue;
            }
            SharedMapMarkerMessage message = outgoing(snapshot, document,
                    selfUid, selfCallsign, teamId, now);
            final String publishedOperationId = operationId;
            final String publishedUid = uid;
            final String publishedSignature = signature;
            if (publisher.publish(message, new Runnable() {
                @Override
                public void run() {
                    publishFailed(publishedOperationId, publishedUid,
                            publishedSignature);
                }
            })) {
                syncedSignatures.put(uid, signature);
                result.published++;
            }
        }

        for (final String uid : previouslyPresent) {
            if (present.contains(uid) || !syncedSignatures.containsKey(uid))
                continue;
            SharedMapMarkerMessage document = byUid.get(uid);
            if (document == null || document.isDeleted()
                    || local.exists(uid))
                continue;
            SharedMapMarkerMessage tombstone = new SharedMapMarkerMessage
                    .Builder().markerUid(uid).operationId(operationId)
                    .deleted(true, now, selfUid, selfCallsign).build();
            final String deletedOperationId = operationId;
            if (publisher.publishDeletion(tombstone, new Runnable() {
                @Override
                public void run() {
                    deletionFailed(deletedOperationId, uid);
                }
            })) {
                tombstoned.add(uid);
                syncedSignatures.remove(uid);
                result.deleted++;
            } else {
                // Try again on the next scan.
                present.add(uid);
            }
        }
        previouslyPresent = present;
        baselineEstablished = true;
    }

    private boolean shouldShareNewMarker(SharedMapMarkerMessage snapshot,
            String deviceUid) {
        if (deviceUid.length() > 0
                && deviceUid.equals(snapshot.getCreatorUid()))
            return true;
        if (snapshot.getCreatedAt() > 0L)
            return snapshot.getCreatedAt()
                    >= sessionStartedAt - SESSION_CREATION_GRACE_MS;
        // No creation metadata: fall back to "appeared during this session".
        return !baseline.contains(snapshot.getMarkerUid());
    }

    private SharedMapMarkerMessage outgoing(SharedMapMarkerMessage snapshot,
            SharedMapMarkerMessage document, String selfUid,
            String selfCallsign, String teamId, long now) {
        String creatorUid = snapshot.getCreatorUid();
        String creatorCallsign = snapshot.getCreatorCallsign();
        long createdAt = snapshot.getCreatedAt();
        if (document != null && document.getCreatorUid().length() > 0) {
            creatorUid = document.getCreatorUid();
            creatorCallsign = document.getCreatorCallsign();
            createdAt = document.getCreatedAt();
        } else if (creatorUid.length() == 0
                && !baseline.contains(snapshot.getMarkerUid())) {
            creatorUid = selfUid;
            creatorCallsign = selfCallsign;
            if (createdAt <= 0L)
                createdAt = now;
        }
        String type = snapshot.getMarkerType().length() == 0
                ? SharedMapMarkerMessage.DEFAULT_TYPE
                : snapshot.getMarkerType();
        return snapshot.toBuilder()
                .uid(SharedMapMarkerMessage.documentId(operationId,
                        snapshot.getMarkerUid()))
                .markerType(type)
                .sender(selfUid, selfCallsign).teamId(teamId)
                .operationId(operationId).updatedAt(now)
                .creator(creatorUid, creatorCallsign, createdAt)
                .deleted(false, 0L, "", "")
                .build();
    }

    /** Documents that are live (not tombstoned), newest first. */
    public static List<SharedMapMarkerMessage> liveMarkers(
            List<SharedMapMarkerMessage> documents, String operationId) {
        List<SharedMapMarkerMessage> live = new ArrayList<>();
        if (documents == null)
            return live;
        for (SharedMapMarkerMessage document : documents) {
            if (document != null && !document.isDeleted()
                    && document.hasValidPosition()
                    && safe(operationId).equals(document.getOperationId()))
                live.add(document);
        }
        live.sort((a, b) -> Long.compare(b.getUpdatedAt(), a.getUpdatedAt()));
        return live;
    }

    /** "Title - by Alpha 14:05 11 Oct (edited by Bravo 14:20 11 Oct)". */
    public static String describe(SharedMapMarkerMessage marker,
            TimeZone timeZone) {
        StringBuilder line = new StringBuilder(marker.getTitle().length() == 0
                ? marker.getMarkerUid() : marker.getTitle());
        String creator = marker.getCreatorCallsign().length() > 0
                ? marker.getCreatorCallsign() : marker.getCreatorUid();
        if (creator.length() > 0 || marker.getCreatedAt() > 0L) {
            line.append(" - by ").append(creator.length() == 0 ? "unknown"
                    : creator);
            if (marker.getCreatedAt() > 0L)
                line.append(' ').append(formatTime(marker.getCreatedAt(),
                        timeZone));
        }
        boolean editedByOther = marker.getSenderUid().length() > 0
                && !marker.getSenderUid().equals(marker.getCreatorUid());
        if (editedByOther && marker.getUpdatedAt() > 0L)
            line.append(" (edited by ").append(marker.getSenderCallsign())
                    .append(' ').append(formatTime(marker.getUpdatedAt(),
                            timeZone))
                    .append(')');
        return line.toString();
    }

    private static String formatTime(long millis, TimeZone timeZone) {
        SimpleDateFormat format = new SimpleDateFormat("HH:mm d MMM",
                Locale.US);
        format.setTimeZone(timeZone);
        return format.format(new Date(millis));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
