package com.atakmap.android.plugintemplate.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.atakmap.android.plugintemplate.runtime.SharedMarkerImagePlanner.LocalFile;
import com.atakmap.android.plugintemplate.runtime.SharedMarkerImagePlanner.Plan;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, manifest = Config.NONE)
public class SharedMarkerImagePlannerTest {

    private static final String OP = "op-1";
    private static final Map<String, Object> TOKEN =
            Collections.<String, Object>singletonMap("id", "blob");

    private final Set<String> done = new HashSet<>();
    private final Map<String, Long> failedAt = new HashMap<>();

    @Test
    public void localPhotoWithoutDocumentIsUploaded() {
        Plan plan = plan(files("m1", file("m1", "pic.jpg", 2_000_000L)),
                Collections.<SharedMarkerImage>emptyList(), "alpha");

        assertEquals(1, plan.uploads.size());
        assertEquals("pic.jpg", plan.uploads.get(0).name);
        assertTrue(plan.downloads.isEmpty());
    }

    @Test
    public void photoAlreadyInDittoIsNotUploadedAgain() {
        Plan plan = plan(files("m1", file("m1", "pic.jpg", 2_000_000L)),
                Arrays.asList(document("m1", "pic.jpg", 2_000_000L, "alpha")),
                "alpha");

        assertTrue(plan.uploads.isEmpty());
        assertTrue(plan.downloads.isEmpty());
    }

    @Test
    public void downloadedPhotoIsNotEchoedBack() {
        Plan plan = plan(files("m1", file("m1", "pic.jpg", 2_000_000L)),
                Arrays.asList(document("m1", "pic.jpg", 2_000_000L, "alpha")),
                "bravo");

        assertTrue(plan.uploads.isEmpty());
        assertTrue(plan.downloads.isEmpty());
    }

    @Test
    public void missingRemotePhotoIsDownloaded() {
        Plan plan = plan(files("m1"),
                Arrays.asList(document("m1", "pic.jpg", 2_000_000L, "alpha")),
                "bravo");

        assertEquals(1, plan.downloads.size());
    }

    @Test
    public void photosForMarkersNotOnThisDeviceAreIgnored() {
        Plan plan = plan(files("m1"),
                Arrays.asList(document("other", "pic.jpg", 10L, "alpha")),
                "bravo");

        assertTrue(plan.downloads.isEmpty());
    }

    @Test
    public void oversizedAndNonImageFilesAreSkipped() {
        Plan plan = plan(files("m1",
                file("m1", "huge.jpg", SharedMarkerImage.MAX_BYTES + 1L),
                file("m1", "notes.txt", 100L),
                file("m1", ".hidden.jpg", 100L)),
                Collections.<SharedMarkerImage>emptyList(), "alpha");

        assertTrue(plan.uploads.isEmpty());
    }

    @Test
    public void failedTransfersWaitBeforeRetrying() {
        LocalFile pic = file("m1", "pic.jpg", 10L);
        failedAt.put(pic.key(), 1000L);

        assertTrue(plan(files("m1", pic),
                Collections.<SharedMarkerImage>emptyList(), "alpha", 2000L)
                .uploads.isEmpty());
        assertEquals(1, plan(files("m1", pic),
                Collections.<SharedMarkerImage>emptyList(), "alpha",
                1000L + SharedMarkerImagePlanner.RETRY_AFTER_MS)
                .uploads.size());
    }

    @Test
    public void uploadsPerRunAreCapped() {
        List<LocalFile> many = new ArrayList<>();
        for (int i = 0; i < 10; i++)
            many.add(file("m1", "pic" + i + ".jpg", 10L));
        Map<String, List<LocalFile>> local = new LinkedHashMap<>();
        local.put("m1", many);

        Plan plan = SharedMarkerImagePlanner.plan(local,
                Collections.<SharedMarkerImage>emptyList(), "alpha", OP, done,
                failedAt, 0L);

        assertEquals(SharedMarkerImagePlanner.MAX_UPLOADS_PER_RUN,
                plan.uploads.size());
    }

    @Test
    public void fileNamesCannotEscapeTheAttachmentFolder() {
        assertEquals("evil.jpg",
                SharedMarkerImage.safeFileName("../../etc/evil.jpg"));
        assertEquals("a_b.jpg", SharedMarkerImage.safeFileName("a b.jpg"));
        assertEquals("x.jpg", SharedMarkerImage.safeFileName("..x.jpg"));
        assertFalse(SharedMarkerImage.isShareableImageName(".x.jpg"));
    }

    @Test
    public void attachmentTokenIsReadFromTheDocument() throws Exception {
        JSONObject json = new JSONObject();
        json.put("operationId", OP);
        json.put("markerUid", "m1");
        json.put("fileName", "pic.jpg");
        json.put("size", 42);
        json.put("senderUid", "alpha");
        JSONObject token = new JSONObject();
        token.put("_id", "abc");
        token.put("_len", 42);
        token.put("_meta", new JSONObject().put("name", "pic.jpg"));
        json.put(SharedMarkerImage.ATTACHMENT_FIELD, token);

        SharedMarkerImage image = SharedMarkerImage.fromJson(json);

        assertNotNull(image.getToken());
        assertEquals("abc", image.getToken().get("_id"));
        assertEquals(42L, image.getToken().get("_len"));
        assertEquals("m1/pic.jpg/42", image.key());
    }

    private Plan plan(Map<String, List<LocalFile>> local,
            List<SharedMarkerImage> documents, String selfUid) {
        return plan(local, documents, selfUid, 0L);
    }

    private Plan plan(Map<String, List<LocalFile>> local,
            List<SharedMarkerImage> documents, String selfUid, long now) {
        return SharedMarkerImagePlanner.plan(local, documents, selfUid, OP,
                done, failedAt, now);
    }

    private static Map<String, List<LocalFile>> files(String markerUid,
            LocalFile... files) {
        Map<String, List<LocalFile>> local = new LinkedHashMap<>();
        local.put(markerUid, new ArrayList<>(Arrays.asList(files)));
        return local;
    }

    private static LocalFile file(String markerUid, String name, long size) {
        return new LocalFile(markerUid, name, size, "/tmp/" + name);
    }

    private static SharedMarkerImage document(String markerUid, String name,
            long size, String sender) {
        return new SharedMarkerImage(OP, markerUid, name, size, sender, 0L,
                TOKEN);
    }
}
