package com.atakmap.android.plugintemplate.runtime;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The rules for which Quick Pic / marker photos get shared and fetched.
 *
 * SharedPhotoSyncManager does the Ditto and file work, which needs ATAK and a
 * live Ditto instance; every decision it makes comes from this planner, which
 * touches neither.
 *
 * These tests require only JUnit -- no ATAK SDK, no Robolectric.
 */
public class SharedPhotoSyncPlannerTest {

    private static final String OPERATION = "op-1";
    private static final String SELF = "ANDROID-self";
    private static final String PEER = "ANDROID-peer";
    private static final long NOW = 1_800_000_000_000L;
    private static final long SETTLED = NOW - 60_000L;

    private static SharedMapMarkerMessage marker(String markerUid,
            String senderUid, String operationId) {
        return new SharedMapMarkerMessage("shared-marker-" + markerUid,
                markerUid, "b-i-x-i", "Quick Pic", -27.47, 153.02, 0.0,
                senderUid, "Callsign", "team-1", operationId, NOW);
    }

    private static LocalMarkerPhoto photo(String markerUid, String fileName,
            long size, long lastModified) {
        return new LocalMarkerPhoto(markerUid, fileName,
                "/sdcard/atak/attachments/" + markerUid + "/" + fileName,
                size, lastModified);
    }

    private static SharedPhotoMessage remote(String markerUid,
            String fileName, long size, String senderUid, long updatedAt) {
        return new SharedPhotoMessage(
                SharedPhotoSyncPlanner.documentIdFor(OPERATION, markerUid,
                        fileName),
                OPERATION, markerUid, fileName, size, senderUid, "Peer",
                "team-1", updatedAt, updatedAt);
    }

    private static final SharedPhotoSyncPlanner.LocalPhotoIndex NOTHING_LOCAL =
            new SharedPhotoSyncPlanner.LocalPhotoIndex() {
                @Override
                public boolean hasPhoto(String markerUid, String fileName,
                        long sizeBytes) {
                    return false;
                }
            };

    // --- which markers' photos we publish ---------------------------------

    @Test
    public void photosOnlyFollowMarkersThisDeviceShared() {
        List<String> uids = SharedPhotoSyncPlanner.markersSharedBySelf(
                OPERATION, SELF, Arrays.asList(
                        marker("mine", SELF, OPERATION),
                        marker("theirs", PEER, OPERATION),
                        marker("old-op", SELF, "op-0"),
                        marker("mine", SELF, OPERATION)));

        assertEquals(Collections.singletonList("mine"), uids);
    }

    @Test
    public void noSelfIdentityMeansNothingIsPublished() {
        assertTrue(SharedPhotoSyncPlanner.markersSharedBySelf(OPERATION, "",
                Collections.singletonList(marker("mine", "", OPERATION)))
                .isEmpty());
    }

    // --- which local photos we publish ------------------------------------

    @Test
    public void newSettledPhotoIsPublished() {
        List<LocalMarkerPhoto> result = SharedPhotoSyncPlanner.photosToPublish(
                OPERATION, Collections.singletonList(
                        photo("m1", "20261005_101500.jpg", 2_000_000L,
                                SETTLED)),
                new HashSet<String>(), new HashMap<String, Long>(), NOW);

        assertEquals(1, result.size());
    }

    @Test
    public void photoAlreadyInTheOperationIsNotUploadedAgain() {
        Set<String> known = new HashSet<>();
        known.add(SharedPhotoSyncPlanner.documentIdFor(OPERATION, "m1",
                "20261005_101500.jpg"));

        assertTrue(SharedPhotoSyncPlanner.photosToPublish(OPERATION,
                Collections.singletonList(photo("m1", "20261005_101500.jpg",
                        2_000_000L, SETTLED)),
                known, new HashMap<String, Long>(), NOW).isEmpty());
    }

    @Test
    public void photoStillBeingWrittenWaitsForALaterPass() {
        assertTrue(SharedPhotoSyncPlanner.photosToPublish(OPERATION,
                Collections.singletonList(photo("m1", "a.jpg", 2_000L,
                        NOW - 500L)),
                new HashSet<String>(), new HashMap<String, Long>(), NOW)
                .isEmpty());
    }

    @Test
    public void nonImageAttachmentsEmptyFilesAndOversizedFilesAreSkipped() {
        List<LocalMarkerPhoto> result = SharedPhotoSyncPlanner.photosToPublish(
                OPERATION, Arrays.asList(
                        photo("m1", "notes.txt", 100L, SETTLED),
                        photo("m1", "empty.jpg", 0L, SETTLED),
                        photo("m1", "huge.jpg",
                                SharedPhotoSyncPlanner.MAX_PHOTO_BYTES + 1L,
                                SETTLED),
                        photo("m1", "ok.PNG", 100L, SETTLED)),
                new HashSet<String>(), new HashMap<String, Long>(), NOW);

        assertEquals(1, result.size());
        assertEquals("ok.PNG", result.get(0).getFileName());
    }

    @Test
    public void failedUploadBacksOffUntilItsRetryTime() {
        String documentId = SharedPhotoSyncPlanner.documentIdFor(OPERATION,
                "m1", "a.jpg");
        Map<String, Long> retryAfter = new HashMap<>();
        retryAfter.put(documentId, NOW + 1_000L);
        List<LocalMarkerPhoto> candidates = Collections.singletonList(
                photo("m1", "a.jpg", 100L, SETTLED));

        assertTrue(SharedPhotoSyncPlanner.photosToPublish(OPERATION,
                candidates, new HashSet<String>(), retryAfter, NOW).isEmpty());
        assertEquals(1, SharedPhotoSyncPlanner.photosToPublish(OPERATION,
                candidates, new HashSet<String>(), retryAfter, NOW + 1_000L)
                .size());
    }

    // --- which remote photos we download ----------------------------------

    @Test
    public void peerPhotoMissingLocallyIsDownloaded() {
        List<SharedPhotoMessage> result = SharedPhotoSyncPlanner
                .photosToDownload(OPERATION, SELF, Collections.singletonList(
                        remote("m1", "a.jpg", 100L, PEER, NOW)),
                        NOTHING_LOCAL, new HashMap<String, Long>(), NOW, 3);

        assertEquals(1, result.size());
    }

    @Test
    public void ownPhotosAreNeverDownloadedBack() {
        assertTrue(SharedPhotoSyncPlanner.photosToDownload(OPERATION, SELF,
                Collections.singletonList(remote("m1", "a.jpg", 100L, SELF,
                        NOW)),
                NOTHING_LOCAL, new HashMap<String, Long>(), NOW, 3).isEmpty());
    }

    @Test
    public void photoAlreadyHeldAtTheRightSizeIsNotDownloadedAgain() {
        SharedPhotoSyncPlanner.LocalPhotoIndex index =
                new SharedPhotoSyncPlanner.LocalPhotoIndex() {
                    @Override
                    public boolean hasPhoto(String markerUid, String fileName,
                            long sizeBytes) {
                        return "m1".equals(markerUid)
                                && "a.jpg".equals(fileName)
                                && sizeBytes == 100L;
                    }
                };

        assertTrue(SharedPhotoSyncPlanner.photosToDownload(OPERATION, SELF,
                Collections.singletonList(remote("m1", "a.jpg", 100L, PEER,
                        NOW)),
                index, new HashMap<String, Long>(), NOW, 3).isEmpty());
        assertEquals("a partial copy is fetched again", 1,
                SharedPhotoSyncPlanner.photosToDownload(OPERATION, SELF,
                        Collections.singletonList(remote("m1", "a.jpg", 250L,
                                PEER, NOW)),
                        index, new HashMap<String, Long>(), NOW, 3).size());
    }

    @Test
    public void photosFromAnotherOperationAreIgnored() {
        SharedPhotoMessage other = new SharedPhotoMessage("doc", "op-0", "m1",
                "a.jpg", 100L, PEER, "Peer", "team-1", NOW, NOW);

        assertTrue(SharedPhotoSyncPlanner.photosToDownload(OPERATION, SELF,
                Collections.singletonList(other), NOTHING_LOCAL,
                new HashMap<String, Long>(), NOW, 3).isEmpty());
    }

    @Test
    public void downloadsAreCappedPerPassOldestFirst() {
        List<SharedPhotoMessage> result = SharedPhotoSyncPlanner
                .photosToDownload(OPERATION, SELF, Arrays.asList(
                        remote("m1", "c.jpg", 100L, PEER, NOW + 3L),
                        remote("m1", "a.jpg", 100L, PEER, NOW + 1L),
                        remote("m1", "b.jpg", 100L, PEER, NOW + 2L)),
                        NOTHING_LOCAL, new HashMap<String, Long>(), NOW, 2);

        assertEquals(2, result.size());
        assertEquals("a.jpg", result.get(0).getFileName());
        assertEquals("b.jpg", result.get(1).getFileName());
    }

    // --- file names written into ATAK's attachment folder -----------------

    @Test
    public void receivedFileNamesCannotEscapeTheAttachmentFolder() {
        assertEquals("passwd.jpg",
                SharedPhotoSyncPlanner.safeFileName("../../etc/passwd.jpg"));
        assertEquals("evil.jpg",
                SharedPhotoSyncPlanner.safeFileName("..\\..\\evil.jpg"));
        assertEquals("hidden.jpg",
                SharedPhotoSyncPlanner.safeFileName(".hidden.jpg"));
        assertEquals("", SharedPhotoSyncPlanner.safeFileName(".."));
        assertEquals("a_b.jpg", SharedPhotoSyncPlanner.safeFileName("a:b.jpg"));
    }

    @Test
    public void photoTypeIsDecidedByExtension() {
        assertTrue(SharedPhotoSyncPlanner.isPhotoFileName("IMG_1.JPEG"));
        assertTrue(SharedPhotoSyncPlanner.isPhotoFileName("x.webp"));
        assertFalse(SharedPhotoSyncPlanner.isPhotoFileName(".jpg"));
        assertFalse(SharedPhotoSyncPlanner.isPhotoFileName("a.jpg.part"));
        assertFalse(SharedPhotoSyncPlanner.isPhotoFileName(null));
    }

    @Test
    public void retryDelayDoublesUpToFiveMinutes() {
        assertEquals(30_000L, SharedPhotoSyncPlanner.retryDelayMs(1));
        assertEquals(60_000L, SharedPhotoSyncPlanner.retryDelayMs(2));
        assertEquals(120_000L, SharedPhotoSyncPlanner.retryDelayMs(3));
        assertEquals(300_000L, SharedPhotoSyncPlanner.retryDelayMs(10));
    }
}
