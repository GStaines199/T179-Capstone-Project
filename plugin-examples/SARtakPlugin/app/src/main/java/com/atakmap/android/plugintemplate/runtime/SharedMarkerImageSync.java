package com.atakmap.android.plugintemplate.runtime;

import android.os.Handler;
import android.os.Looper;

import com.atakmap.android.plugintemplate.plugin.BuildConfig;
import com.atakmap.android.plugintemplate.runtime.SharedMarkerImagePlanner.LocalFile;
import com.atakmap.android.plugintemplate.runtime.SharedMarkerImagePlanner.Plan;
import com.atakmap.android.util.AttachmentManager;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Syncs photos attached to shared markers (ATAK Quick Pic and any image
 * attachment) through Ditto attachments. All file and Ditto work runs on one
 * background thread; only ATAK's attachment-change broadcast is posted back
 * to the main thread.
 */
public class SharedMarkerImageSync {

    private static final String TAG = "SARtakMarkerImages";
    private static final long MIN_INTERVAL_MS = 15_000L;

    private final DittoSyncManager dittoSyncManager;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            runnable -> {
                Thread thread = new Thread(runnable, "SARtak-Marker-Images");
                thread.setDaemon(true);
                return thread;
            });
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile String operationId = "";
    private volatile int generation;
    private long lastRunAt;
    // Touched only on the worker thread.
    private final Set<String> done = new HashSet<>();
    private final Map<String, Long> failedAt = new HashMap<>();
    private int workerGeneration = -1;

    public SharedMarkerImageSync(DittoSyncManager dittoSyncManager) {
        this.dittoSyncManager = dittoSyncManager;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId == null ? "" : operationId.trim();
        generation++;
        lastRunAt = 0L;
    }

    /**
     * Called on the main thread with the shared markers present on this
     * device. Returns immediately; the work happens on the worker thread.
     */
    public void sync(Collection<String> localLiveMarkerUids, String selfUid,
            long now) {
        if (operationId.length() == 0 || now - lastRunAt < MIN_INTERVAL_MS
                || !dittoSyncManager.isStarted()
                || !running.compareAndSet(false, true))
            return;
        lastRunAt = now;
        final List<String> uids = new ArrayList<>(localLiveMarkerUids);
        final String self = selfUid == null ? "" : selfUid;
        final String op = operationId;
        final int runGeneration = generation;
        try {
            worker.execute(() -> {
                try {
                    run(uids, self, op, runGeneration);
                } catch (Throwable throwable) {
                    Log.w(TAG, "Marker image sync failed", throwable);
                } finally {
                    running.set(false);
                }
            });
        } catch (Throwable throwable) {
            running.set(false);
        }
    }

    private void run(List<String> uids, String selfUid, String op,
            int runGeneration) {
        if (runGeneration != workerGeneration) {
            done.clear();
            failedAt.clear();
            workerGeneration = runGeneration;
        }
        Map<String, List<LocalFile>> localFiles = new LinkedHashMap<>();
        for (String uid : uids) {
            List<LocalFile> files = new ArrayList<>();
            // List the folder ourselves: AttachmentManager.getAttachments()
            // caches per UID until ATAK's own change notification, so it can
            // miss a file that arrived some other way.
            String folderPath = AttachmentManager.getFolderPath(uid, false);
            File[] attachments = folderPath == null ? null
                    : new File(folderPath).listFiles();
            if (attachments != null) {
                for (File file : attachments) {
                    if (file != null && file.isFile())
                        files.add(new LocalFile(uid, file.getName(),
                                file.length(), file.getAbsolutePath()));
                }
            }
            localFiles.put(uid, files);
        }
        long now = System.currentTimeMillis();
        List<SharedMarkerImage> documents = dittoSyncManager.getMarkerImages();
        Plan plan = SharedMarkerImagePlanner.plan(localFiles, documents,
                selfUid, op, done, failedAt, now);
        if (BuildConfig.DEBUG)
            Log.d(TAG, "run markers=" + uids.size() + " docs="
                    + documents.size() + " uploads=" + plan.uploads.size()
                    + " downloads=" + plan.downloads.size());
        for (LocalFile file : plan.uploads) {
            if (runGeneration != generation)
                return;
            upload(file, selfUid, op, now);
        }
        for (SharedMarkerImage image : plan.downloads) {
            if (runGeneration != generation)
                return;
            download(image, now);
        }
    }

    private void upload(LocalFile file, String selfUid, String op, long now) {
        SharedMarkerImage image = new SharedMarkerImage(op, file.markerUid,
                file.name, file.size, selfUid, now, null);
        long started = System.currentTimeMillis();
        if (dittoSyncManager.publishMarkerImageBlocking(image, file.path)) {
            done.add(file.key());
            Log.i(TAG, "shared marker photo " + image.getFileName()
                    + " bytes=" + file.size + " ms="
                    + (System.currentTimeMillis() - started));
        } else {
            failedAt.put(file.key(), now);
        }
    }

    private void download(SharedMarkerImage image, long now) {
        String folderPath = AttachmentManager.getFolderPath(
                image.getMarkerUid(), true);
        if (folderPath == null) {
            failedAt.put(image.key(), now);
            return;
        }
        File folder = new File(folderPath);
        File destination = new File(folder, image.getFileName());
        // Download next to (not inside) the attachment folder so ATAK never
        // lists a half-written file, then move it in.
        File partial = new File(folder.getParentFile(), "."
                + image.getMarkerUid() + "-" + image.getFileName() + ".part");
        if (partial.exists() && !partial.delete()) {
            failedAt.put(image.key(), now);
            return;
        }
        long started = System.currentTimeMillis();
        String failure = dittoSyncManager.fetchMarkerImageBlocking(image,
                partial.getAbsolutePath());
        if (failure.length() > 0 || !partial.isFile()) {
            Log.w(TAG, "marker photo fetch failed " + image.getFileName()
                    + ": " + failure);
            failedAt.put(image.key(), now);
            partial.delete();
            return;
        }
        if (destination.exists())
            destination.delete();
        if (!partial.renameTo(destination)) {
            failedAt.put(image.key(), now);
            partial.delete();
            return;
        }
        done.add(image.key());
        Log.i(TAG, "received marker photo " + image.getFileName() + " bytes="
                + destination.length() + " ms="
                + (System.currentTimeMillis() - started));
        final String uid = image.getMarkerUid();
        mainHandler.post(() -> {
            try {
                AttachmentManager.notifyAttachmentChange(uid);
            } catch (Throwable throwable) {
                Log.w(TAG, "Unable to refresh marker attachments", throwable);
            }
        });
    }
}
