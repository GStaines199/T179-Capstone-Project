package com.atakmap.android.plugintemplate.runtime;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.AttachmentManager;
import com.atakmap.coremap.log.Log;
import com.ditto.kotlin.Ditto;
import com.ditto.kotlin.DittoSyncSubscription;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shares Quick Pic and other marker photos with the rest of the operation.
 *
 * <p>{@link SharedMapMarkerSyncManager} already shares the marker itself; this
 * class sends the image files ATAK keeps in that marker's attachment folder.
 * Each photo is one document in {@value #COLLECTION} carrying the file as a
 * Ditto attachment, so peers sync the small document straight away and pull
 * the bytes on demand, over whichever transport Ditto has.
 *
 * <p>A received photo is written into the same marker UID's attachment folder,
 * which is where ATAK's own image viewer and attachment list look, so the
 * photo shows up on the shared marker without SARtak drawing anything.
 *
 * <p>All Ditto and file work runs off the main thread: one worker plans and
 * publishes, a second fetches, so a photo crawling over a slow mesh link
 * never holds up publishing. The sync loop calls {@link #sync(String)} on the
 * main thread and never waits on it.
 */
public class SharedPhotoSyncManager {

    static final String COLLECTION = "sartak_shared_photos";
    static final String ATTACHMENT_FIELD = "photo";

    private static final String TAG = "SARtakSharedPhotos";
    private static final String SUBSCRIPTION_QUERY = "SELECT * FROM COLLECTION "
            + COLLECTION + " (" + ATTACHMENT_FIELD + " ATTACHMENT)";
    private static final String OPERATION_QUERY = SUBSCRIPTION_QUERY
            + " WHERE operationId = :operationId";
    private static final long MIN_SYNC_INTERVAL_MS = 5000L;
    /** Generous, because a full-size photo over Bluetooth mesh is slow. */
    private static final long FETCH_TIMEOUT_MS = 10L * 60L * 1000L;
    private static final int MAX_QUEUED_DOWNLOADS = 3;
    private static final String PARTIAL_SUFFIX = ".part";

    private final MapView mapView;
    private final IdentityManager identityManager;
    private final DittoSyncManager dittoSyncManager;
    private final ExecutorService worker;
    private final ExecutorService fetcher;
    private final Set<String> downloadsInFlight =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final AtomicBoolean passQueued = new AtomicBoolean(false);
    private final Map<String, Long> retryAfter = new ConcurrentHashMap<>();
    private final Map<String, Integer> failureCounts =
            new ConcurrentHashMap<>();

    private volatile String operationId = "";
    private volatile String teamId = "";
    private volatile boolean disposed;
    private volatile Ditto boundDitto;
    private volatile DittoSyncSubscription subscription;
    private long lastSyncTime;

    public SharedPhotoSyncManager(MapView mapView,
            IdentityManager identityManager,
            DittoSyncManager dittoSyncManager) {
        this.mapView = mapView;
        this.identityManager = identityManager;
        this.dittoSyncManager = dittoSyncManager;
        this.worker = Executors.newSingleThreadExecutor(
                daemonThreads("SARtak-SharedPhotos"));
        this.fetcher = Executors.newSingleThreadExecutor(
                daemonThreads("SARtak-PhotoFetch"));
    }

    private static ThreadFactory daemonThreads(final String name) {
        return new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, name);
                thread.setDaemon(true);
                return thread;
            }
        };
    }

    public void setOperationId(String operationId) {
        String next = operationId == null ? "" : operationId.trim();
        if (next.equals(this.operationId))
            return;
        this.operationId = next;
        retryAfter.clear();
        failureCounts.clear();
    }

    /** Queues one publish/download pass; returns immediately. */
    public void sync(String teamId) {
        if (disposed)
            return;
        this.teamId = teamId == null ? "" : teamId.trim();
        long now = System.currentTimeMillis();
        if (now - lastSyncTime < MIN_SYNC_INTERVAL_MS)
            return;
        lastSyncTime = now;
        // Identity resolution can touch ATAK's map, so read it here on the
        // main thread and hand the result to the worker.
        final IdentityManager.Identity identity = identityManager
                .getCurrentIdentity();
        if (identity == null || !identity.isResolved())
            return;
        if (!passQueued.compareAndSet(false, true))
            return;
        try {
            worker.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        runPass(identity);
                    } catch (Throwable throwable) {
                        Log.w(TAG, "Shared photo sync pass failed", throwable);
                    } finally {
                        passQueued.set(false);
                    }
                }
            });
        } catch (RejectedExecutionException exception) {
            passQueued.set(false);
        }
    }

    /** Call before {@link DittoSyncManager#stop()} when the plugin unloads. */
    public void dispose() {
        disposed = true;
        worker.shutdownNow();
        fetcher.shutdownNow();
        unbind();
    }

    private void runPass(IdentityManager.Identity identity) {
        String operation = operationId;
        Ditto ditto = dittoSyncManager.getActiveDitto();
        if (disposed || ditto == null || operation.length() == 0) {
            unbind();
            return;
        }
        bind(ditto);

        List<SharedPhotoMessage> remote = loadPhotos(ditto, operation);
        if (remote == null)
            return;
        Set<String> knownDocumentIds = new HashSet<>();
        for (SharedPhotoMessage photo : remote)
            knownDocumentIds.add(photo.getDocumentId());

        publishPending(ditto, operation, identity, knownDocumentIds);
        downloadPending(ditto, operation, identity.getUid(), remote);
    }

    private void bind(Ditto ditto) {
        if (ditto == boundDitto && subscription != null)
            return;
        unbind();
        try {
            subscription = DittoSdkBridge.registerSubscription(ditto,
                    SUBSCRIPTION_QUERY);
            boundDitto = ditto;
        } catch (Throwable throwable) {
            Log.w(TAG, "Unable to subscribe to shared photos", throwable);
        }
    }

    private void unbind() {
        DittoSyncSubscription current = subscription;
        subscription = null;
        boundDitto = null;
        DittoSdkBridge.closeSubscription(current);
    }

    /** Returns null when the query fails, so nothing is re-uploaded blind. */
    private List<SharedPhotoMessage> loadPhotos(Ditto ditto,
            String operation) {
        Map<String, Object> args = new HashMap<>();
        args.put("operationId", operation);
        List<String> documents;
        try {
            documents = DittoSdkBridge.queryJson(ditto, OPERATION_QUERY, args);
        } catch (Throwable throwable) {
            Log.w(TAG, "Unable to read shared photos", throwable);
            return null;
        }
        List<SharedPhotoMessage> photos = new ArrayList<>();
        for (String json : documents) {
            try {
                photos.add(fromJson(json));
            } catch (JSONException exception) {
                Log.w(TAG, "Ignoring invalid shared photo document",
                        exception);
            }
        }
        return photos;
    }

    static SharedPhotoMessage fromJson(String json) throws JSONException {
        JSONObject object = new JSONObject(json);
        return new SharedPhotoMessage(
                object.optString("_id", ""),
                object.optString("operationId", ""),
                object.optString("markerUid", ""),
                object.optString("fileName", ""),
                object.optLong("sizeBytes", 0L),
                object.optString("senderUid", ""),
                object.optString("senderCallsign", ""),
                object.optString("teamId", ""),
                object.optLong("capturedAt", 0L),
                object.optLong("updatedAt", 0L));
    }

    private void publishPending(Ditto ditto, String operation,
            IdentityManager.Identity identity, Set<String> knownDocumentIds) {
        List<String> markerUids = SharedPhotoSyncPlanner.markersSharedBySelf(
                operation, identity.getUid(),
                dittoSyncManager.getSharedMapMarkers());
        if (markerUids.isEmpty())
            return;
        List<LocalMarkerPhoto> candidates = new ArrayList<>();
        for (String markerUid : markerUids)
            candidates.addAll(localPhotos(markerUid));
        List<LocalMarkerPhoto> pending = SharedPhotoSyncPlanner
                .photosToPublish(operation, candidates, knownDocumentIds,
                        retryAfter, System.currentTimeMillis());
        for (LocalMarkerPhoto photo : pending) {
            if (disposed || !operation.equals(operationId))
                return;
            publish(ditto, operation, identity, photo);
        }
    }

    private List<LocalMarkerPhoto> localPhotos(String markerUid) {
        List<LocalMarkerPhoto> photos = new ArrayList<>();
        try {
            List<File> files = AttachmentManager.getAttachments(markerUid);
            if (files == null)
                return photos;
            for (File file : files) {
                if (file == null || !file.isFile()
                        || file.getName().startsWith(".")
                        || file.getName().endsWith(PARTIAL_SUFFIX))
                    continue;
                photos.add(new LocalMarkerPhoto(markerUid, file.getName(),
                        file.getAbsolutePath(), file.length(),
                        file.lastModified()));
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Unable to list attachments for " + markerUid,
                    throwable);
        }
        return photos;
    }

    private void publish(Ditto ditto, String operation,
            IdentityManager.Identity identity, LocalMarkerPhoto photo) {
        String fileName = SharedPhotoSyncPlanner.safeFileName(
                photo.getFileName());
        String documentId = SharedPhotoSyncPlanner.documentIdFor(operation,
                photo.getMarkerUid(), fileName);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("_id", documentId);
        fields.put("operationId", operation);
        fields.put("markerUid", photo.getMarkerUid());
        fields.put("fileName", fileName);
        fields.put("sizeBytes", photo.getSizeBytes());
        fields.put("senderUid", identity.getUid());
        fields.put("senderCallsign", identity.getCallsign());
        fields.put("teamId", teamId);
        fields.put("capturedAt", photo.getLastModified());
        fields.put("updatedAt", System.currentTimeMillis());
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("markerUid", photo.getMarkerUid());
        metadata.put("fileName", fileName);
        try {
            DittoSdkBridge.insertDocumentWithAttachment(ditto, COLLECTION,
                    ATTACHMENT_FIELD, photo.getPath(), fields, metadata);
            clearFailure(documentId);
            Log.d(TAG, "Shared photo " + fileName + " for marker "
                    + photo.getMarkerUid());
        } catch (Throwable throwable) {
            recordFailure(documentId);
            Log.w(TAG, "Unable to share photo " + fileName, throwable);
        }
    }

    private void downloadPending(final Ditto ditto, final String operation,
            String selfUid, List<SharedPhotoMessage> remote) {
        int capacity = MAX_QUEUED_DOWNLOADS - downloadsInFlight.size();
        if (capacity <= 0)
            return;
        List<SharedPhotoMessage> notQueued = new ArrayList<>();
        for (SharedPhotoMessage photo : remote) {
            if (!downloadsInFlight.contains(photo.getDocumentId()))
                notQueued.add(photo);
        }
        List<SharedPhotoMessage> pending = SharedPhotoSyncPlanner
                .photosToDownload(operation, selfUid, notQueued,
                        new SharedPhotoSyncPlanner.LocalPhotoIndex() {
                            @Override
                            public boolean hasPhoto(String markerUid,
                                    String fileName, long sizeBytes) {
                                return hasCompleteCopy(markerUid, fileName,
                                        sizeBytes);
                            }
                        }, retryAfter, System.currentTimeMillis(), capacity);
        for (final SharedPhotoMessage photo : pending) {
            if (!downloadsInFlight.add(photo.getDocumentId()))
                continue;
            try {
                fetcher.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (!disposed && operation.equals(operationId))
                                download(ditto, photo);
                        } catch (Throwable throwable) {
                            recordFailure(photo.getDocumentId());
                            Log.w(TAG, "Photo download failed", throwable);
                        } finally {
                            downloadsInFlight.remove(photo.getDocumentId());
                        }
                    }
                });
            } catch (RejectedExecutionException exception) {
                downloadsInFlight.remove(photo.getDocumentId());
            }
        }
    }

    private boolean hasCompleteCopy(String markerUid, String fileName,
            long sizeBytes) {
        try {
            File file = new File(AttachmentManager.getFolderPath(markerUid,
                    false), fileName);
            return file.isFile() && file.length() == sizeBytes;
        } catch (Throwable throwable) {
            return false;
        }
    }

    private void download(Ditto ditto, SharedPhotoMessage photo) {
        String documentId = photo.getDocumentId();
        String fileName = SharedPhotoSyncPlanner.safeFileName(
                photo.getFileName());
        File folder;
        try {
            folder = new File(AttachmentManager.getFolderPath(
                    photo.getMarkerUid(), true));
        } catch (Throwable throwable) {
            recordFailure(documentId);
            Log.w(TAG, "No attachment folder for " + photo.getMarkerUid(),
                    throwable);
            return;
        }
        File target = new File(folder, fileName);
        File partial = new File(folder, "." + fileName + PARTIAL_SUFFIX);
        deleteQuietly(partial);

        String failure = DittoSdkBridge.fetchAttachmentToPath(ditto,
                COLLECTION, ATTACHMENT_FIELD, documentId,
                partial.getAbsolutePath(), FETCH_TIMEOUT_MS);
        if (failure.length() > 0) {
            deleteQuietly(partial);
            recordFailure(documentId);
            Log.d(TAG, "Photo " + fileName + " not fetched yet: " + failure);
            return;
        }
        if (partial.length() != photo.getSizeBytes()) {
            deleteQuietly(partial);
            recordFailure(documentId);
            Log.w(TAG, "Photo " + fileName + " arrived with "
                    + partial.length() + " bytes, expected "
                    + photo.getSizeBytes());
            return;
        }
        if (target.exists())
            deleteQuietly(target);
        if (!partial.renameTo(target)) {
            deleteQuietly(partial);
            recordFailure(documentId);
            Log.w(TAG, "Unable to move photo into " + target);
            return;
        }
        clearFailure(documentId);
        Log.d(TAG, "Received photo " + fileName + " from "
                + photo.getSenderCallsign());
        notifyAttachmentChanged(photo.getMarkerUid());
    }

    private void notifyAttachmentChanged(final String markerUid) {
        if (mapView == null)
            return;
        mapView.post(new Runnable() {
            @Override
            public void run() {
                try {
                    AttachmentManager.notifyAttachmentChange(markerUid);
                } catch (Throwable throwable) {
                    Log.w(TAG, "Attachment refresh failed for " + markerUid,
                            throwable);
                }
            }
        });
    }

    private void recordFailure(String documentId) {
        Integer previous = failureCounts.get(documentId);
        int failures = previous == null ? 1 : previous + 1;
        failureCounts.put(documentId, failures);
        retryAfter.put(documentId, System.currentTimeMillis()
                + SharedPhotoSyncPlanner.retryDelayMs(failures));
    }

    private void clearFailure(String documentId) {
        failureCounts.remove(documentId);
        retryAfter.remove(documentId);
    }

    private static void deleteQuietly(File file) {
        try {
            if (file != null && file.exists() && !file.delete())
                Log.w(TAG, "Unable to delete " + file);
        } catch (Throwable ignored) {
        }
    }
}
