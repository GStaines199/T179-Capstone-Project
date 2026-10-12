package com.atakmap.android.plugintemplate.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * Two simulated devices sharing ATAK markers through a simulated Ditto store
 * that merges documents per field (last writer wins per field), as
 * {@code INSERT ... ON ID CONFLICT DO UPDATE} does.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, manifest = Config.NONE)
public class SharedMapMarkerSyncTest {

    private static final String OP = "op-1";
    private static final long SESSION = 1_800_000_000_000L;

    private Network network;
    private Device alpha;
    private Device bravo;

    @Before
    public void setUp() {
        network = new Network();
        alpha = new Device(network, "uid-alpha", "ALPHA", "device-alpha");
        bravo = new Device(network, "uid-bravo", "BRAVO", "device-bravo");
    }

    @Test
    public void markerCreatedOnOneDeviceAppearsOnceOnTheOther() {
        alpha.startSession(SESSION);
        bravo.startSession(SESSION);
        alpha.createMarker("m1", "Footprint", 10.0, 20.0, SESSION + 1000L);

        runRounds(4);

        SharedMapMarkerMessage onBravo = bravo.map.get("m1");
        assertNotNull(onBravo);
        assertEquals("Footprint", onBravo.getTitle());
        assertEquals(1, bravo.map.size());
        assertEquals(1, alpha.published);
        assertEquals("Imported marker must not be echoed back", 0,
                bravo.published);
    }

    @Test
    public void quietRoundsPublishNothing() {
        alpha.startSession(SESSION);
        bravo.startSession(SESSION);
        alpha.createMarker("m1", "Footprint", 10.0, 20.0, SESSION + 1000L);
        runRounds(3);
        int alphaBefore = alpha.published;
        int bravoBefore = bravo.published;

        runRounds(10);

        assertEquals(alphaBefore, alpha.published);
        assertEquals(bravoBefore, bravo.published);
    }

    @Test
    public void moveAndRenameReachTheOtherDevice() {
        alpha.startSession(SESSION);
        bravo.startSession(SESSION);
        alpha.createMarker("m1", "Footprint", 10.0, 20.0, SESSION + 1000L);
        runRounds(3);

        alpha.edit("m1", "Footprint (fresh)", 10.001, 20.002);
        runRounds(3);

        SharedMapMarkerMessage onBravo = bravo.map.get("m1");
        assertEquals("Footprint (fresh)", onBravo.getTitle());
        assertEquals(10.001, onBravo.getLatitude(), 1e-9);
        assertEquals(20.002, onBravo.getLongitude(), 1e-9);
        assertEquals(1, bravo.map.size());
    }

    @Test
    public void editOnReceivingDeviceKeepsOriginalCreator() {
        alpha.startSession(SESSION);
        bravo.startSession(SESSION);
        alpha.createMarker("m1", "Footprint", 10.0, 20.0, SESSION + 1000L);
        runRounds(3);

        bravo.edit("m1", "Footprint", 10.5, 20.5);
        runRounds(3);

        SharedMapMarkerMessage onAlpha = alpha.map.get("m1");
        assertEquals(10.5, onAlpha.getLatitude(), 1e-9);
        SharedMapMarkerMessage document = network.document("m1");
        assertEquals("device-alpha", document.getCreatorUid());
        assertEquals("ALPHA", document.getCreatorCallsign());
        assertEquals(SESSION + 1000L, document.getCreatedAt());
        assertEquals("uid-bravo", document.getSenderUid());
        assertEquals("device-alpha", bravo.map.get("m1").getCreatorUid());
    }

    @Test
    public void remarksColourAndIconTravelWithTheMarker() {
        alpha.startSession(SESSION);
        bravo.startSession(SESSION);
        alpha.map.put("m1", alpha.localMarker("m1", "Bag", 1.0, 2.0,
                SESSION + 1L).toBuilder().remarks("Red backpack")
                .color(true, 0xffff0000)
                .iconsetPath("COT_MAPPING_SPOTMAP/b-m-p-s-m/-65536")
                .build());

        runRounds(3);

        SharedMapMarkerMessage onBravo = bravo.map.get("m1");
        assertEquals("Red backpack", onBravo.getRemarks());
        assertTrue(onBravo.hasColor());
        assertEquals(0xffff0000, onBravo.getColor());
        assertEquals("COT_MAPPING_SPOTMAP/b-m-p-s-m/-65536",
                onBravo.getIconsetPath());
    }

    @Test
    public void deletionPropagatesAndStays() {
        alpha.startSession(SESSION);
        bravo.startSession(SESSION);
        alpha.createMarker("m1", "Footprint", 10.0, 20.0, SESSION + 1000L);
        runRounds(3);

        alpha.map.remove("m1");
        runRounds(3);

        assertNull(bravo.map.get("m1"));
        assertTrue(network.document("m1").isDeleted());
        assertEquals("uid-alpha", network.document("m1").getDeletedByUid());
        runRounds(5);
        assertNull(alpha.map.get("m1"));
        assertNull(bravo.map.get("m1"));
    }

    @Test
    public void markerWhoseQueuedWriteFailsIsPublishedAgain() {
        alpha.startSession(SESSION);
        bravo.startSession(SESSION);
        alpha.createMarker("m1", "Footprint", 10.0, 20.0, SESSION + 1000L);
        alpha.failNextWrites = 1;

        runRounds(4);

        assertEquals("Published once, lost, then published again", 2,
                alpha.published);
        assertNotNull(network.document("m1"));
        assertEquals("Footprint", bravo.map.get("m1").getTitle());
    }

    @Test
    public void editWhoseQueuedWriteFailsIsPublishedAgain() {
        alpha.startSession(SESSION);
        bravo.startSession(SESSION);
        alpha.createMarker("m1", "Footprint", 10.0, 20.0, SESSION + 1000L);
        runRounds(3);

        alpha.failNextWrites = 1;
        alpha.edit("m1", "Footprint (fresh)", 10.001, 20.002);
        runRounds(4);

        assertEquals("Footprint (fresh)", bravo.map.get("m1").getTitle());
    }

    @Test
    public void deletionWhoseQueuedWriteFailsIsSentAgain() {
        alpha.startSession(SESSION);
        bravo.startSession(SESSION);
        alpha.createMarker("m1", "Footprint", 10.0, 20.0, SESSION + 1000L);
        runRounds(3);

        alpha.failNextWrites = 1;
        alpha.map.remove("m1");
        runRounds(4);

        assertTrue(network.document("m1").isDeleted());
        assertNull(bravo.map.get("m1"));
        assertNull(alpha.map.get("m1"));
    }

    @Test
    public void deletionOnReceivingDevicePropagatesToCreator() {
        alpha.startSession(SESSION);
        bravo.startSession(SESSION);
        alpha.createMarker("m1", "Footprint", 10.0, 20.0, SESSION + 1000L);
        runRounds(3);

        bravo.map.remove("m1");
        runRounds(3);

        assertNull(alpha.map.get("m1"));
    }

    @Test
    public void lateOfflineEditCannotResurrectDeletedMarker() {
        alpha.startSession(SESSION);
        bravo.startSession(SESSION);
        alpha.createMarker("m1", "Footprint", 10.0, 20.0, SESSION + 1000L);
        runRounds(3);

        network.disconnect(bravo);
        alpha.map.remove("m1");
        runRounds(2);
        // Bravo has not heard about the delete and moves the marker.
        bravo.edit("m1", "Footprint moved", 11.0, 21.0);
        runRounds(2);
        assertNotNull(bravo.map.get("m1"));

        network.reconnect(bravo);
        runRounds(4);

        assertTrue(network.document("m1").isDeleted());
        assertNull(alpha.map.get("m1"));
        assertNull(bravo.map.get("m1"));
    }

    @Test
    public void tombstonedMarkerRestoredByAtakIsRemovedAgain() {
        alpha.startSession(SESSION);
        bravo.startSession(SESSION);
        alpha.createMarker("m1", "Footprint", 10.0, 20.0, SESSION + 1000L);
        runRounds(3);
        alpha.map.remove("m1");
        runRounds(3);

        // ATAK restarts on Bravo and its own store brings the marker back.
        bravo.restart(SESSION + 60_000L);
        bravo.map.put("m1", bravo.importedCopy("m1", "Footprint", 10.0, 20.0,
                "device-alpha", "ALPHA", SESSION + 1000L));
        runRounds(2);

        assertNull(bravo.map.get("m1"));
        assertEquals(0, bravo.published);
    }

    @Test
    public void restartDoesNotRepublishOrDuplicate() {
        alpha.startSession(SESSION);
        bravo.startSession(SESSION);
        alpha.createMarker("m1", "Footprint", 10.0, 20.0, SESSION + 1000L);
        runRounds(3);

        // restart() zeroes the publish counters.
        alpha.restart(SESSION + 3_600_000L);
        bravo.restart(SESSION + 3_600_000L);
        runRounds(5);

        assertEquals(0, alpha.published);
        assertEquals(0, bravo.published);
        assertEquals(1, alpha.map.size());
        assertEquals(1, bravo.map.size());
    }

    @Test
    public void editsMadeWhileOtherDeviceWasOffAreKeptAfterItRestarts() {
        alpha.startSession(SESSION);
        bravo.startSession(SESSION);
        alpha.createMarker("m1", "Footprint", 10.0, 20.0, SESSION + 1000L);
        runRounds(3);

        network.disconnect(alpha);
        bravo.edit("m1", "Footprint", 12.0, 22.0);
        runRounds(2);
        alpha.restart(SESSION + 60_000L);
        runRounds(2);
        network.reconnect(alpha);
        runRounds(3);

        assertEquals(12.0, alpha.map.get("m1").getLatitude(), 1e-9);
        assertEquals(12.0, network.document("m1").getLatitude(), 1e-9);
    }

    @Test
    public void concurrentEditsConvergeWithoutDuplicates() {
        alpha.startSession(SESSION);
        bravo.startSession(SESSION);
        alpha.createMarker("m1", "Footprint", 10.0, 20.0, SESSION + 1000L);
        runRounds(3);

        network.disconnect(bravo);
        alpha.edit("m1", "Alpha says", 10.1, 20.1);
        bravo.edit("m1", "Bravo says", 10.2, 20.2);
        runRounds(2);
        network.reconnect(bravo);
        runRounds(6);

        SharedMapMarkerMessage a = alpha.map.get("m1");
        SharedMapMarkerMessage b = bravo.map.get("m1");
        assertEquals(a.contentSignature(), b.contentSignature());
        assertEquals(1, alpha.map.size());
        assertEquals(1, bravo.map.size());
        int alphaBefore = alpha.published;
        int bravoBefore = bravo.published;
        runRounds(5);
        assertEquals(alphaBefore, alpha.published);
        assertEquals(bravoBefore, bravo.published);
    }

    @Test
    public void ownMarkersFromBeforeStartupAreShared() {
        alpha.createMarker("old-own", "Old", 5.0, 5.0,
                SESSION - 86_400_000L);
        alpha.startSession(SESSION);
        bravo.startSession(SESSION);

        runRounds(3);

        assertNotNull(bravo.map.get("old-own"));
        assertEquals(SESSION - 86_400_000L,
                network.document("old-own").getCreatedAt());
    }

    @Test
    public void otherPeoplesOldMarkersAreNotPushedIntoTheOperation() {
        alpha.map.put("old-other", alpha.importedCopy("old-other", "Theirs",
                5.0, 5.0, "device-charlie", "CHARLIE",
                SESSION - 86_400_000L));
        alpha.map.put("old-unknown", alpha.localMarker("old-unknown",
                "Unknown", 6.0, 6.0, 0L).toBuilder()
                .creator("", "", 0L).build());
        alpha.startSession(SESSION);
        bravo.startSession(SESSION);

        runRounds(3);

        assertNull(network.document("old-other"));
        assertNull(network.document("old-unknown"));
        assertEquals(0, alpha.published);
    }

    @Test
    public void markerWithoutMetadataAppearingDuringSessionIsShared() {
        alpha.startSession(SESSION);
        bravo.startSession(SESSION);
        runRounds(1);
        alpha.map.put("plain", alpha.localMarker("plain", "Plain", 7.0, 7.0,
                0L).toBuilder().creator("", "", 0L).build());

        runRounds(3);

        SharedMapMarkerMessage document = network.document("plain");
        assertNotNull(document);
        assertEquals("uid-alpha", document.getCreatorUid());
        assertNotNull(bravo.map.get("plain"));
    }

    @Test
    public void markerCreatedByAnotherTakUserDuringSessionIsShared() {
        alpha.startSession(SESSION);
        bravo.startSession(SESSION);
        runRounds(1);
        alpha.map.put("cot", alpha.importedCopy("cot", "From TAK", 8.0, 8.0,
                "device-charlie", "CHARLIE", SESSION + 5000L));

        runRounds(3);

        assertEquals("CHARLIE", network.document("cot").getCreatorCallsign());
        assertNotNull(bravo.map.get("cot"));
    }

    @Test
    public void documentsOfOtherOperationsAreIgnored() {
        alpha.startSession(SESSION);
        network.writeRaw(alpha, new SharedMapMarkerMessage.Builder()
                .markerUid("x").title("Other op").position(1.0, 1.0, 0.0)
                .sender("uid-zulu", "ZULU").operationId("op-2")
                .updatedAt(SESSION).build().toDocument("op-2"));

        runRounds(2);

        assertNull(alpha.map.get("x"));
    }

    @Test
    public void ordinaryUpdateDocumentNeverCarriesTombstoneFields() {
        Map<String, Object> document = alpha.localMarker("m1", "A", 1.0, 2.0,
                SESSION).toDocument(OP);
        assertFalse(document.containsKey("deleted"));
        assertFalse(document.containsKey("deletedAt"));
        Map<String, Object> tombstone = new SharedMapMarkerMessage.Builder()
                .markerUid("m1").deleted(true, SESSION, "u", "U").build()
                .toDeletionDocument(OP);
        assertEquals(true, tombstone.get("deleted"));
        assertFalse(tombstone.containsKey("latitude"));
        assertEquals(document.get("_id"), tombstone.get("_id"));
    }

    @Test
    public void jsonRoundTripKeepsEveryField() throws Exception {
        SharedMapMarkerMessage original = alpha.localMarker("m1", "Bag", 1.5,
                2.5, SESSION).toBuilder().remarks("note").color(true, 0x7f00ff00)
                .iconsetPath("set/icon.png").sender("uid-alpha", "ALPHA")
                .operationId(OP).updatedAt(SESSION + 5L).teamId("team-1")
                .build();

        SharedMapMarkerMessage parsed = SharedMapMarkerMessage.fromJson(
                new JSONObject(original.toDocument(OP)));

        assertEquals(original.versionSignature(), parsed.versionSignature());
        assertEquals("device-alpha", parsed.getCreatorUid());
        assertEquals(SESSION, parsed.getCreatedAt());
        assertEquals("team-1", parsed.getTeamId());
        assertFalse(parsed.isDeleted());
    }

    @Test
    public void oldFormatDocumentsStillParse() throws Exception {
        JSONObject legacy = new JSONObject();
        legacy.put("markerUid", "m1");
        legacy.put("title", "Legacy");
        legacy.put("latitude", 1.0);
        legacy.put("longitude", 2.0);
        legacy.put("senderUid", "uid-alpha");
        legacy.put("operationId", OP);
        legacy.put("updatedAt", 5L);

        SharedMapMarkerMessage parsed = SharedMapMarkerMessage.fromJson(legacy);

        assertEquals("Legacy", parsed.getTitle());
        assertFalse(parsed.isDeleted());
        assertFalse(parsed.hasColor());
        assertEquals("", parsed.getCreatorUid());
    }

    @Test
    public void describeShowsCreatorTimeAndLastEditor() {
        SharedMapMarkerMessage marker = new SharedMapMarkerMessage.Builder()
                .markerUid("m1").title("Footprint")
                .creator("device-alpha", "ALPHA", 1_791_720_300_000L)
                .sender("uid-bravo", "BRAVO").updatedAt(1_791_721_200_000L)
                .build();

        String line = SharedMapMarkerReconciler.describe(marker,
                TimeZone.getTimeZone("UTC"));

        assertEquals("Footprint - by ALPHA 12:05 11 Oct"
                + " (edited by BRAVO 12:20 11 Oct)", line);
    }

    @Test
    public void largeMarkerSetSyncsOnceAndThenStaysQuiet() {
        alpha.startSession(SESSION);
        bravo.startSession(SESSION);
        for (int i = 0; i < 500; i++)
            alpha.createMarker("m" + i, "Marker " + i, i * 0.001, i * 0.002,
                    SESSION + i);

        runRounds(3);
        assertEquals(500, bravo.map.size());
        assertEquals(500, alpha.published);
        runRounds(5);
        assertEquals(500, alpha.published);
        assertEquals(0, bravo.published);
    }

    private void runRounds(int rounds) {
        for (int i = 0; i < rounds; i++) {
            network.tick();
            alpha.sync();
            bravo.sync();
            network.tick();
        }
    }

    /** A device: its ATAK map, its Ditto replica and its reconciler. */
    private static class Device implements SharedMapMarkerReconciler.LocalMap,
            SharedMapMarkerReconciler.Publisher {
        final Network network;
        final String uid;
        final String callsign;
        final String deviceUid;
        final Map<String, SharedMapMarkerMessage> map = new LinkedHashMap<>();
        final Map<String, Map<String, Stamp>> replica = new LinkedHashMap<>();
        SharedMapMarkerReconciler reconciler;
        boolean connected = true;
        int published;
        long now;
        /**
         * Writes still to be accepted then lost, like a queued Ditto write
         * that throws on the writer thread after publish() returned true.
         */
        int failNextWrites;
        final List<Runnable> failures = new ArrayList<>();

        Device(Network network, String uid, String callsign,
                String deviceUid) {
            this.network = network;
            this.uid = uid;
            this.callsign = callsign;
            this.deviceUid = deviceUid;
            network.devices.add(this);
        }

        void startSession(long startedAt) {
            reconciler = new SharedMapMarkerReconciler();
            reconciler.reset(OP, startedAt);
            now = startedAt;
        }

        void restart(long startedAt) {
            published = 0;
            // ATAK drops in-memory meta; SARtak-created markers come back
            // only if ATAK saved them, which the tests model explicitly.
            startSession(startedAt);
        }

        void sync() {
            if (reconciler == null)
                return;
            now += 5000L;
            reconciler.sync(documents(), this, this, uid, callsign,
                    deviceUid, "team-1", now);
            // Failures are reported after the sync, as the main-thread post
            // does on a device.
            List<Runnable> reported = new ArrayList<>(failures);
            failures.clear();
            for (Runnable failure : reported)
                failure.run();
        }

        /** Accepts the write, or loses it if a failure is pending. */
        private boolean write(Map<String, Object> document, Runnable onFailed) {
            if (failNextWrites > 0) {
                failNextWrites--;
                if (onFailed != null)
                    failures.add(onFailed);
                return true;
            }
            network.write(this, document);
            return true;
        }

        SharedMapMarkerMessage localMarker(String markerUid, String title,
                double lat, double lon, long createdAt) {
            return new SharedMapMarkerMessage.Builder().markerUid(markerUid)
                    .markerType("b-m-p-s-m").title(title)
                    .position(lat, lon, 0.0)
                    .creator(deviceUid, callsign, createdAt).build();
        }

        SharedMapMarkerMessage importedCopy(String markerUid, String title,
                double lat, double lon, String creatorUid,
                String creatorCallsign, long createdAt) {
            return localMarker(markerUid, title, lat, lon, createdAt)
                    .toBuilder().creator(creatorUid, creatorCallsign,
                            createdAt).build();
        }

        void createMarker(String markerUid, String title, double lat,
                double lon, long createdAt) {
            map.put(markerUid, localMarker(markerUid, title, lat, lon,
                    createdAt));
        }

        void edit(String markerUid, String title, double lat, double lon) {
            SharedMapMarkerMessage current = map.get(markerUid);
            map.put(markerUid, current.toBuilder().title(title)
                    .position(lat, lon, 0.0).build());
        }

        List<SharedMapMarkerMessage> documents() {
            List<SharedMapMarkerMessage> documents = new ArrayList<>();
            for (Map<String, Stamp> fields : replica.values()) {
                Map<String, Object> values = new LinkedHashMap<>();
                for (Map.Entry<String, Stamp> entry : fields.entrySet())
                    values.put(entry.getKey(), entry.getValue().value);
                if (!OP.equals(values.get("operationId")))
                    continue;
                documents.add(SharedMapMarkerMessage.fromJson(
                        new JSONObject(values)));
            }
            return documents;
        }

        @Override
        public List<SharedMapMarkerMessage> scanShareable() {
            return new ArrayList<>(map.values());
        }

        @Override
        public boolean exists(String markerUid) {
            return map.containsKey(markerUid);
        }

        @Override
        public SharedMapMarkerMessage find(String markerUid) {
            return map.get(markerUid);
        }

        @Override
        public void remove(String markerUid) {
            map.remove(markerUid);
        }

        @Override
        public SharedMapMarkerMessage apply(SharedMapMarkerMessage document) {
            // Same fields SharedMapMarkerSyncManager copies onto a Marker.
            SharedMapMarkerMessage local = new SharedMapMarkerMessage
                    .Builder().markerUid(document.getMarkerUid())
                    .markerType(document.getMarkerType())
                    .title(document.getTitle())
                    .position(document.getLatitude(),
                            document.getLongitude(), document.getAltitude())
                    .remarks(document.getRemarks())
                    .color(document.hasColor(), document.getColor())
                    .iconsetPath(document.getIconsetPath())
                    .creator(document.getCreatorUid().length() > 0
                            ? document.getCreatorUid()
                            : document.getSenderUid(),
                            document.getCreatorCallsign(),
                            document.getCreatedAt())
                    .build();
            map.put(document.getMarkerUid(), local);
            return local;
        }

        @Override
        public boolean publish(SharedMapMarkerMessage message,
                Runnable onFailed) {
            published++;
            return write(message.toDocument(OP), onFailed);
        }

        @Override
        public boolean publishDeletion(SharedMapMarkerMessage tombstone,
                Runnable onFailed) {
            return write(tombstone.toDeletionDocument(OP), onFailed);
        }
    }

    private static class Stamp {
        final Object value;
        final long clock;
        final String writer;

        Stamp(Object value, long clock, String writer) {
            this.value = value;
            this.clock = clock;
            this.writer = writer;
        }

        boolean newerThan(Stamp other) {
            if (other == null || clock != other.clock)
                return other == null || clock > other.clock;
            return writer.compareTo(other.writer) > 0;
        }
    }

    /** Per-field last-writer-wins replication between connected devices. */
    private static class Network {
        final List<Device> devices = new ArrayList<>();
        long clock;

        void write(Device device, Map<String, Object> document) {
            writeRaw(device, document);
        }

        void writeRaw(Device device, Map<String, Object> document) {
            clock++;
            String id = String.valueOf(document.get("_id"));
            Map<String, Stamp> fields = device.replica.get(id);
            if (fields == null) {
                fields = new LinkedHashMap<>();
                device.replica.put(id, fields);
            }
            for (Map.Entry<String, Object> entry : document.entrySet())
                fields.put(entry.getKey(), new Stamp(entry.getValue(), clock,
                        device.uid));
        }

        void disconnect(Device device) {
            device.connected = false;
        }

        void reconnect(Device device) {
            device.connected = true;
        }

        void tick() {
            for (Device from : devices) {
                if (!from.connected)
                    continue;
                for (Device to : devices) {
                    if (to == from || !to.connected)
                        continue;
                    merge(from, to);
                }
            }
        }

        private void merge(Device from, Device to) {
            for (Map.Entry<String, Map<String, Stamp>> document
                    : from.replica.entrySet()) {
                Map<String, Stamp> target = to.replica.get(document.getKey());
                if (target == null) {
                    target = new LinkedHashMap<>();
                    to.replica.put(document.getKey(), target);
                }
                for (Map.Entry<String, Stamp> field
                        : document.getValue().entrySet()) {
                    if (field.getValue().newerThan(target.get(field.getKey())))
                        target.put(field.getKey(), field.getValue());
                }
            }
        }

        SharedMapMarkerMessage document(String markerUid) {
            Device any = null;
            for (Device device : devices) {
                if (device.connected) {
                    any = device;
                    break;
                }
            }
            for (SharedMapMarkerMessage document : any.documents()) {
                if (document.getMarkerUid().equals(markerUid))
                    return document;
            }
            return null;
        }
    }
}
