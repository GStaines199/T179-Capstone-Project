package com.atakmap.android.plugintemplate.runtime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides which marker photos to upload and download in one run. Pure, so it
 * can be tested without ATAK or Ditto.
 */
public final class SharedMarkerImagePlanner {

    static final long RETRY_AFTER_MS = 60_000L;
    static final int MAX_UPLOADS_PER_RUN = 3;
    static final int MAX_DOWNLOADS_PER_RUN = 3;

    private SharedMarkerImagePlanner() {
    }

    /** A local attachment file. */
    public static class LocalFile {
        final String markerUid;
        final String name;
        final long size;
        final String path;

        public LocalFile(String markerUid, String name, long size,
                String path) {
            this.markerUid = markerUid;
            this.name = name;
            this.size = size;
            this.path = path;
        }

        String key() {
            return markerUid + "/" + SharedMarkerImage.safeFileName(name)
                    + "/" + size;
        }
    }

    /** What one run should upload and download. */
    public static class Plan {
        public final List<LocalFile> uploads = new ArrayList<>();
        public final List<SharedMarkerImage> downloads = new ArrayList<>();
    }

    /**
     * Upload local images that have no matching document; download
     * documents whose file is missing locally. Only markers present in
     * {@code localFiles} (shared markers on this device) are considered.
     */
    public static Plan plan(Map<String, List<LocalFile>> localFiles,
            List<SharedMarkerImage> documents, String selfUid,
            String operationId, Set<String> done, Map<String, Long> failedAt,
            long now) {
        Plan plan = new Plan();
        Set<String> documentKeys = new HashSet<>();
        for (SharedMarkerImage document : documents) {
            if (operationId.equals(document.getOperationId()))
                documentKeys.add(document.key());
        }
        for (List<LocalFile> files : localFiles.values()) {
            for (LocalFile file : files) {
                if (plan.uploads.size() >= MAX_UPLOADS_PER_RUN)
                    break;
                String key = file.key();
                if (!SharedMarkerImage.isShareableImageName(file.name)
                        || file.size <= 0L
                        || file.size > SharedMarkerImage.MAX_BYTES
                        || documentKeys.contains(key) || done.contains(key)
                        || recentlyFailed(failedAt, key, now))
                    continue;
                plan.uploads.add(file);
            }
        }
        for (SharedMarkerImage document : documents) {
            if (plan.downloads.size() >= MAX_DOWNLOADS_PER_RUN)
                break;
            List<LocalFile> files = localFiles.get(document.getMarkerUid());
            if (files == null || document.getToken() == null
                    || !operationId.equals(document.getOperationId())
                    || document.getSize() > SharedMarkerImage.MAX_BYTES
                    || selfUid.equals(document.getSenderUid())
                    || done.contains(document.key())
                    || recentlyFailed(failedAt, document.key(), now))
                continue;
            boolean present = false;
            for (LocalFile file : files) {
                if (file.key().equals(document.key())) {
                    present = true;
                    break;
                }
            }
            if (!present)
                plan.downloads.add(document);
        }
        return plan;
    }

    private static boolean recentlyFailed(Map<String, Long> failedAt,
            String key, long now) {
        Long at = failedAt.get(key);
        return at != null && now - at < RETRY_AFTER_MS;
    }
}
