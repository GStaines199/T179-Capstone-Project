package com.atakmap.android.plugintemplate.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Decides which marker photos to publish and which to download.
 *
 * <p>A photo follows its marker: this device only publishes photos for
 * markers it has already shared into the operation itself, so a photo is never
 * shared for a marker the rest of the team cannot see, and markers imported
 * from another device are never echoed back.
 *
 * <p>Kept free of ATAK and Android types so the rules can be unit tested on
 * the JVM, following {@link CotPublishPoint}.
 */
public final class SharedPhotoSyncPlanner {

    /** Larger files are skipped rather than pushed over a field mesh. */
    public static final long MAX_PHOTO_BYTES = 20L * 1024L * 1024L;
    public static final long FIRST_RETRY_DELAY_MS = 30000L;
    public static final long MAX_RETRY_DELAY_MS = 5L * 60L * 1000L;
    /** A file touched more recently than this may still be being written. */
    public static final long SETTLE_TIME_MS = 3000L;

    private static final String DOCUMENT_PREFIX = "shared-photo-";
    private static final int MAX_FILE_NAME_LENGTH = 120;
    private static final String[] PHOTO_EXTENSIONS = {
            ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp"
    };

    /** Answers whether this device already holds a complete copy. */
    public interface LocalPhotoIndex {
        boolean hasPhoto(String markerUid, String fileName, long sizeBytes);
    }

    private SharedPhotoSyncPlanner() {
    }

    /**
     * Reduces an incoming or local file name to a single safe path segment,
     * or returns "" when nothing usable is left. Received names are written
     * into ATAK's attachment folder, so directory parts are always dropped.
     */
    public static String safeFileName(String name) {
        if (name == null)
            return "";
        String trimmed = name.trim();
        int slash = Math.max(trimmed.lastIndexOf('/'),
                trimmed.lastIndexOf('\\'));
        if (slash >= 0)
            trimmed = trimmed.substring(slash + 1);
        StringBuilder builder = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '.' || c == '-'
                    || c == '_' || c == ' ';
            builder.append(allowed ? c : '_');
        }
        String cleaned = builder.toString();
        while (cleaned.startsWith("."))
            cleaned = cleaned.substring(1);
        cleaned = cleaned.trim();
        if (cleaned.length() > MAX_FILE_NAME_LENGTH)
            cleaned = cleaned.substring(cleaned.length()
                    - MAX_FILE_NAME_LENGTH);
        return cleaned;
    }

    public static boolean isPhotoFileName(String name) {
        String safe = safeFileName(name);
        if (safe.length() == 0)
            return false;
        String lower = safe.toLowerCase(Locale.US);
        for (String extension : PHOTO_EXTENSIONS) {
            if (lower.endsWith(extension) && lower.length() > extension
                    .length())
                return true;
        }
        return false;
    }

    public static String documentIdFor(String operationId, String markerUid,
            String fileName) {
        return DOCUMENT_PREFIX + safe(operationId) + "-" + safe(markerUid)
                + "-" + safeFileName(fileName);
    }

    /** Markers in this operation whose shared copy was published by us. */
    public static List<String> markersSharedBySelf(String operationId,
            String selfUid, Collection<SharedMapMarkerMessage> sharedMarkers) {
        List<String> result = new ArrayList<>();
        String operation = safe(operationId);
        String self = safe(selfUid);
        if (operation.length() == 0 || self.length() == 0
                || sharedMarkers == null)
            return result;
        Set<String> seen = new LinkedHashSet<>();
        for (SharedMapMarkerMessage marker : sharedMarkers) {
            if (marker == null || !operation.equals(marker.getOperationId())
                    || !self.equals(marker.getSenderUid()))
                continue;
            String markerUid = marker.getMarkerUid();
            if (markerUid.length() > 0 && seen.add(markerUid))
                result.add(markerUid);
        }
        return result;
    }

    /**
     * Local photos that still need publishing. {@code knownDocumentIds} holds
     * every photo document already in the operation, including ones this
     * device wrote before a restart, so nothing is uploaded twice. Files
     * modified within {@link #SETTLE_TIME_MS} are left for a later pass in
     * case the camera is still writing them.
     */
    public static List<LocalMarkerPhoto> photosToPublish(String operationId,
            Collection<LocalMarkerPhoto> candidates,
            Set<String> knownDocumentIds, Map<String, Long> retryAfter,
            long now) {
        List<LocalMarkerPhoto> result = new ArrayList<>();
        if (safe(operationId).length() == 0 || candidates == null)
            return result;
        Set<String> planned = new LinkedHashSet<>();
        for (LocalMarkerPhoto photo : candidates) {
            if (photo == null || photo.getMarkerUid().length() == 0
                    || !isPhotoFileName(photo.getFileName())
                    || !withinSizeLimit(photo.getSizeBytes())
                    || now - photo.getLastModified() < SETTLE_TIME_MS)
                continue;
            String documentId = documentIdFor(operationId,
                    photo.getMarkerUid(), photo.getFileName());
            if (knownDocumentIds != null
                    && knownDocumentIds.contains(documentId))
                continue;
            if (isWaitingToRetry(retryAfter, documentId, now))
                continue;
            if (planned.add(documentId))
                result.add(photo);
        }
        return result;
    }

    /**
     * Remote photos this device should fetch, oldest first, capped at
     * {@code limit} so one slow mesh link cannot hold up the sync loop.
     */
    public static List<SharedPhotoMessage> photosToDownload(
            String operationId, String selfUid,
            Collection<SharedPhotoMessage> remote, LocalPhotoIndex index,
            Map<String, Long> retryAfter, long now, int limit) {
        List<SharedPhotoMessage> result = new ArrayList<>();
        String operation = safe(operationId);
        if (operation.length() == 0 || remote == null || limit <= 0)
            return result;
        String self = safe(selfUid);
        Map<String, SharedPhotoMessage> byDocument = new LinkedHashMap<>();
        for (SharedPhotoMessage photo : remote) {
            if (photo == null || !operation.equals(photo.getOperationId())
                    || photo.getMarkerUid().length() == 0
                    || photo.getDocumentId().length() == 0)
                continue;
            if (self.length() > 0 && self.equals(photo.getSenderUid()))
                continue;
            String fileName = safeFileName(photo.getFileName());
            if (!isPhotoFileName(fileName)
                    || !withinSizeLimit(photo.getSizeBytes()))
                continue;
            if (index != null && index.hasPhoto(photo.getMarkerUid(),
                    fileName, photo.getSizeBytes()))
                continue;
            if (isWaitingToRetry(retryAfter, photo.getDocumentId(), now))
                continue;
            SharedPhotoMessage existing = byDocument.get(
                    photo.getDocumentId());
            if (existing == null
                    || photo.getUpdatedAt() > existing.getUpdatedAt())
                byDocument.put(photo.getDocumentId(), photo);
        }
        List<SharedPhotoMessage> ordered = new ArrayList<>(
                byDocument.values());
        Collections.sort(ordered, new Comparator<SharedPhotoMessage>() {
            @Override
            public int compare(SharedPhotoMessage a, SharedPhotoMessage b) {
                return Long.compare(a.getUpdatedAt(), b.getUpdatedAt());
            }
        });
        for (SharedPhotoMessage photo : ordered) {
            if (result.size() >= limit)
                break;
            result.add(photo);
        }
        return result;
    }

    /** 30 s after the first failure, doubling up to five minutes. */
    public static long retryDelayMs(int failures) {
        long delay = FIRST_RETRY_DELAY_MS;
        for (int i = 1; i < failures && delay < MAX_RETRY_DELAY_MS; i++)
            delay *= 2L;
        return Math.min(delay, MAX_RETRY_DELAY_MS);
    }

    private static boolean withinSizeLimit(long sizeBytes) {
        return sizeBytes > 0L && sizeBytes <= MAX_PHOTO_BYTES;
    }

    private static boolean isWaitingToRetry(Map<String, Long> retryAfter,
            String documentId, long now) {
        if (retryAfter == null)
            return false;
        Long until = retryAfter.get(documentId);
        return until != null && until > now;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
