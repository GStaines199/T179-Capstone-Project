package com.atakmap.android.plugintemplate.runtime;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;

import com.atakmap.android.plugintemplate.database.DatabaseHelper;
import com.atakmap.android.plugintemplate.database.LocationRepository;
import com.atakmap.android.plugintemplate.database.SearcherRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the UID the plugin tracks under, covering the whole strategy
 * in the order it is decided.
 * <p>
 * <b>Generation and persistence</b> ({@link DeviceIdentityStore}) - the UID the
 * plugin falls back to when ATAK supplies no identity: a namespaced random
 * UUID, minted once and kept in SharedPreferences, plus a callsign that stays
 * distinct across identical handsets.
 * <p>
 * <b>Resolution</b> ({@code IdentityManager.resolve}) - which of ATAK's device
 * identity, the ATAK self marker or the plugin's own persistent identity wins.
 * <p>
 * <b>Collision avoidance</b> - that the generated UID carries nothing
 * device-derived which clones or handset batches could share, and what actually
 * goes wrong downstream when a UID is shared: two searchers collapsing into one
 * row in {@code searcher_identity}, and their fixes interleaving in
 * {@code location_points}. Those consequences are what makes uniqueness a
 * requirement rather than a nicety, so they are asserted rather than assumed.
 * <p>
 * The point of the whole exercise is that two devices never end up on one UID
 * and one device never splits itself across two, so the tests cover both halves
 * of that: uniqueness between installs, and stability within an install across
 * restarts - including under concurrent first calls, which is when a device
 * splits. First launch is the one moment the UID does not exist yet, and the
 * capture poll and the UI both ask for it; the racing tests hold minting to a
 * single answer across threads and across store instances, since the
 * preferences file is process wide.
 * <p>
 * ATAK SDK types are kept out - MapView and friends fail JVM bytecode
 * verification off-device - so {@code IdentityManager.resolveIdentity()} itself
 * is not covered and resolution is driven through the {@code DeviceFallback}
 * seam instead. Where the real UID matters, that seam is wired to a real
 * {@link DeviceIdentityStore} exactly the way {@link IdentityManager} wires it.
 * <p>
 * Dependencies (build.gradle):
 *   testImplementation 'junit:junit:4.13.2'
 *   testImplementation 'org.robolectric:robolectric:4.9'
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, manifest = Config.NONE)
public class UidGenerationTest {

    private static final String ATAK_UID = "ANDROID-1234567890abcdef";
    private static final String ATAK_CALLSIGN = "ALPHA";
    private static final String MARKER_UID = "MARKER-abcdef0123456789";
    private static final String MARKER_CALLSIGN = "BRAVO";
    private static final String DEVICE_ID = "9774d56d682e549c";

    /** Two searchers issued the same handset from the same batch. */
    private static final String DEVICE_MODEL = "Pixel 7";

    private final RecordingFallback fallback =
            new RecordingFallback(DEVICE_ID, DEVICE_MODEL);

    private Context context;
    private DeviceIdentityStore store;
    private DatabaseHelper dbHelper;
    private SearcherRepository searchers;
    private LocationRepository locations;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        preferences().edit().clear().commit();
        store = new DeviceIdentityStore(context);

        dbHelper = new DatabaseHelper(context);
        dbHelper.getWritableDatabase().delete("searcher_identity", null, null);
        dbHelper.getWritableDatabase().delete("location_points", null, null);
        searchers = new SearcherRepository(dbHelper);
        locations = new LocationRepository(dbHelper);
    }

    @After
    public void tearDown() {
        dbHelper.close();
    }

    // =========================================================================
    // Generation
    // =========================================================================

    @Test
    public void getOrCreateUid_mintsANamespacedRandomUuid() {
        String uid = store.getOrCreateUid();

        assertTrue("unexpected uid: " + uid,
                uid.startsWith(DeviceIdentityStore.UID_PREFIX));

        // A canonical UUID round-trips through java.util.UUID unchanged, which
        // covers both the 8-4-4-4-12 shape and the hex digits.
        String body = uid.substring(DeviceIdentityStore.UID_PREFIX.length());
        assertEquals(body.toLowerCase(), UUID.fromString(body).toString());
    }

    @Test
    public void generateUid_neverRepeats() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10000; i++)
            assertTrue("duplicate uid at " + i,
                    seen.add(DeviceIdentityStore.generateUid()));
    }

    @Test
    public void getOrCreateUid_onASecondInstall_differsFromTheFirst() {
        String first = store.getOrCreateUid();

        // Reinstalling the plugin, or clearing its data, leaves it with empty
        // preferences - the state a second device is also in on first launch.
        preferences().edit().clear().commit();
        String second = new DeviceIdentityStore(context).getOrCreateUid();

        assertNotEquals(first, second);
    }

    // =========================================================================
    // Persistence
    // =========================================================================

    @Test
    public void getOrCreateUid_isStableAcrossCalls() {
        assertEquals(store.getOrCreateUid(), store.getOrCreateUid());
    }

    @Test
    public void getOrCreateUid_isStableAcrossStoreInstances() {
        String first = store.getOrCreateUid();

        // A new store on the same preferences is what a plugin or ATAK restart
        // produces.
        assertEquals(first, new DeviceIdentityStore(context).getOrCreateUid());
    }

    @Test
    public void getOrCreateUid_reusesAUidWrittenByAnEarlierRun() {
        String existing = "SARTAK-0F3A1C2B-1111-2222-3333-444455556666";
        preferences().edit()
                .putString(DeviceIdentityStore.KEY_UID, existing).commit();

        assertEquals(existing,
                new DeviceIdentityStore(context).getOrCreateUid());
    }

    @Test
    public void getOrCreateUid_whenStoredValueIsBlank_mintsAReplacement() {
        preferences().edit()
                .putString(DeviceIdentityStore.KEY_UID, "   ").commit();

        String uid = store.getOrCreateUid();

        assertTrue(uid.startsWith(DeviceIdentityStore.UID_PREFIX));
        assertEquals(uid,
                preferences().getString(DeviceIdentityStore.KEY_UID, null));
    }

    // =========================================================================
    // Fallback callsign
    // =========================================================================

    @Test
    public void getFallbackCallsign_appendsTheUidSuffixToTheModel() {
        String callsign = store.getFallbackCallsign("Pixel 7");

        String suffix = DeviceIdentityStore.uidSuffix(store.getOrCreateUid());
        assertEquals("Pixel 7-" + suffix, callsign);
    }

    @Test
    public void buildFallbackCallsign_separatesIdenticalHandsets() {
        String first = DeviceIdentityStore.buildFallbackCallsign("Pixel 7",
                "SARTAK-0F3A1C2B-1111-2222-3333-444455556666");
        String second = DeviceIdentityStore.buildFallbackCallsign("Pixel 7",
                "SARTAK-9D8E7A6B-1111-2222-3333-444455556666");

        assertNotEquals(first, second);
        assertEquals("Pixel 7-0F3A", first);
        assertEquals("Pixel 7-9D8E", second);
    }

    @Test
    public void buildFallbackCallsign_ignoresTheHexLettersInThePrefix() {
        // "SARTAK" contains A twice; taking the first hex digits of the whole
        // string would hand every device the same suffix.
        String callsign = DeviceIdentityStore.buildFallbackCallsign("Pixel 7",
                "SARTAK-0F3A1C2B-1111-2222-3333-444455556666");

        assertFalse(callsign.endsWith("-AA0F"));
        assertEquals("Pixel 7-0F3A", callsign);
    }

    @Test
    public void buildFallbackCallsign_withNoModel_stillIdentifiesTheSearcher() {
        assertEquals("Searcher-0F3A", DeviceIdentityStore.buildFallbackCallsign(
                null, "SARTAK-0F3A1C2B-1111-2222-3333-444455556666"));
        assertEquals("Searcher-0F3A", DeviceIdentityStore.buildFallbackCallsign(
                "  ", "SARTAK-0F3A1C2B-1111-2222-3333-444455556666"));
    }

    @Test
    public void buildFallbackCallsign_withNoHexInTheUid_keepsTheModelAlone() {
        assertEquals("Pixel 7",
                DeviceIdentityStore.buildFallbackCallsign("Pixel 7", null));
        assertEquals("Pixel 7", DeviceIdentityStore
                .buildFallbackCallsign("Pixel 7", "SARTAK-"));
    }

    @Test
    public void getFallbackCallsign_isStableAcrossStoreInstances() {
        String first = store.getFallbackCallsign("Pixel 7");

        assertEquals(first, new DeviceIdentityStore(context)
                .getFallbackCallsign("Pixel 7"));
    }

    // =========================================================================
    // Resolution: ATAK identity
    // =========================================================================

    @Test
    public void resolve_prefersAtakDeviceIdentity() {
        IdentityManager.Identity identity = IdentityManager.resolve(ATAK_UID,
                ATAK_CALLSIGN, MARKER_UID, MARKER_CALLSIGN, fallback);

        assertEquals(ATAK_UID, identity.getUid());
        assertEquals(ATAK_CALLSIGN, identity.getCallsign());
        assertTrue(identity.isAtakIdentity());
        assertTrue(identity.isResolved());
        assertEquals("Identity: " + ATAK_CALLSIGN, identity.getMessage());
    }

    @Test
    public void resolve_whenAtakIdentityPresent_neverTouchesDeviceFallback() {
        IdentityManager.resolve(ATAK_UID, ATAK_CALLSIGN, null, null, fallback);

        assertEquals(0, fallback.deviceIdCalls);
        assertEquals(0, fallback.deviceModelCalls);
    }

    // =========================================================================
    // Resolution: self marker fallback
    // =========================================================================

    @Test
    public void resolve_whenDeviceUidMissing_usesSelfMarkerUid() {
        IdentityManager.Identity identity = IdentityManager.resolve(null,
                ATAK_CALLSIGN, MARKER_UID, MARKER_CALLSIGN, fallback);

        assertEquals(MARKER_UID, identity.getUid());
        assertEquals(ATAK_CALLSIGN, identity.getCallsign());
        assertTrue(identity.isAtakIdentity());
    }

    @Test
    public void resolve_whenDeviceUidBlank_usesSelfMarkerUid() {
        IdentityManager.Identity identity = IdentityManager.resolve("   ",
                ATAK_CALLSIGN, MARKER_UID, MARKER_CALLSIGN, fallback);

        assertEquals(MARKER_UID, identity.getUid());
    }

    @Test
    public void resolve_whenDeviceCallsignMissing_usesSelfMarkerCallsign() {
        IdentityManager.Identity identity = IdentityManager.resolve(ATAK_UID,
                null, MARKER_UID, MARKER_CALLSIGN, fallback);

        assertEquals(ATAK_UID, identity.getUid());
        assertEquals(MARKER_CALLSIGN, identity.getCallsign());
        assertTrue(identity.isAtakIdentity());
        assertEquals("Identity: " + MARKER_CALLSIGN, identity.getMessage());
    }

    @Test
    public void resolve_whenDeviceCallsignBlank_usesSelfMarkerCallsign() {
        IdentityManager.Identity identity = IdentityManager.resolve(ATAK_UID,
                "\t ", MARKER_UID, MARKER_CALLSIGN, fallback);

        assertEquals(MARKER_CALLSIGN, identity.getCallsign());
    }

    @Test
    public void resolve_withOnlySelfMarkerValues_isStillAnAtakIdentity() {
        IdentityManager.Identity identity = IdentityManager.resolve(null, null,
                MARKER_UID, MARKER_CALLSIGN, fallback);

        assertEquals(MARKER_UID, identity.getUid());
        assertEquals(MARKER_CALLSIGN, identity.getCallsign());
        assertTrue(identity.isAtakIdentity());
        assertEquals(0, fallback.deviceIdCalls);
    }

    // =========================================================================
    // Resolution: device fallback
    // =========================================================================

    @Test
    public void resolve_withNoAtakIdentity_fallsBackToDeviceValues() {
        IdentityManager.Identity identity = IdentityManager.resolve(null, null,
                null, null, fallback);

        assertEquals(DEVICE_ID, identity.getUid());
        assertEquals(DEVICE_MODEL, identity.getCallsign());
        assertFalse(identity.isAtakIdentity());
        assertTrue(identity.isResolved());
        assertEquals("Using device identity fallback", identity.getMessage());
    }

    @Test
    public void resolve_withUidButNoCallsign_keepsUidAndFallsBackToModel() {
        IdentityManager.Identity identity = IdentityManager.resolve(ATAK_UID,
                null, null, null, fallback);

        assertEquals(ATAK_UID, identity.getUid());
        assertEquals(DEVICE_MODEL, identity.getCallsign());
        assertFalse(identity.isAtakIdentity());
        assertEquals("Using device identity fallback", identity.getMessage());
        assertEquals(0, fallback.deviceIdCalls);
        assertEquals(1, fallback.deviceModelCalls);
    }

    @Test
    public void resolve_withCallsignButNoUid_keepsCallsignAndFallsBackToId() {
        IdentityManager.Identity identity = IdentityManager.resolve(null,
                ATAK_CALLSIGN, null, null, fallback);

        assertEquals(DEVICE_ID, identity.getUid());
        assertEquals(ATAK_CALLSIGN, identity.getCallsign());
        assertFalse(identity.isAtakIdentity());
        assertEquals(1, fallback.deviceIdCalls);
        assertEquals(0, fallback.deviceModelCalls);
    }

    @Test
    public void resolve_whenDeviceIdUnavailable_isUnresolved() {
        IdentityManager.Identity identity = IdentityManager.resolve(null, null,
                null, null, new RecordingFallback(null, DEVICE_MODEL));

        assertNull(identity.getUid());
        assertFalse(identity.isResolved());
        assertFalse(identity.isAtakIdentity());
        assertEquals("Using device identity fallback", identity.getMessage());
    }

    @Test
    public void resolve_whenDeviceIdEmpty_isUnresolved() {
        IdentityManager.Identity identity = IdentityManager.resolve(null, null,
                null, null, new RecordingFallback("", DEVICE_MODEL));

        assertEquals("", identity.getUid());
        assertFalse(identity.isResolved());
    }

    @Test
    public void resolve_whenDeviceModelUnavailable_isUnresolved() {
        IdentityManager.Identity identity = IdentityManager.resolve(null, null,
                null, null, new RecordingFallback(DEVICE_ID, null));

        assertEquals(DEVICE_ID, identity.getUid());
        assertNull(identity.getCallsign());
        assertFalse(identity.isResolved());
    }

    // =========================================================================
    // Collision avoidance: nothing device-derived goes into the UID
    // =========================================================================

    @Test
    public void uid_differsBetweenInstallsOnIdenticalHardware() {
        // Same model, same everything: two emulators cloned from one snapshot,
        // or two handsets out of the same box. Only the random body separates
        // them.
        String first = newInstall().getOrCreateUid();
        String second = newInstall().getOrCreateUid();

        assertNotEquals(first, second);
    }

    @Test
    public void uid_isDistinctAcrossAWholeFleetOfInstalls() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 250; i++)
            assertTrue("duplicate uid at install " + i,
                    seen.add(newInstall().getOrCreateUid()));
    }

    @Test
    public void callsign_differsBetweenInstallsOnIdenticalHardware() {
        // The model alone would give a whole team the same callsign.
        String first = newInstall().getFallbackCallsign(DEVICE_MODEL);
        String second = newInstall().getFallbackCallsign(DEVICE_MODEL);

        assertTrue(first.startsWith(DEVICE_MODEL + "-"));
        assertNotEquals(first, second);
    }

    @Test
    public void uid_isNeverBlank() {
        for (int i = 0; i < 50; i++) {
            String uid = newInstall().getOrCreateUid();
            assertNotNull(uid);
            assertFalse("blank uid", uid.trim().isEmpty());
        }
    }

    @Test
    public void uid_usesOnlyCharactersThatSurviveBeingAnIdentifier() {
        // The UID travels as a CoT attribute, a primary key and part of a
        // session id, so anything quoted, spaced or non-ASCII would be a
        // problem long before a collision was.
        String uid = newInstall().getOrCreateUid();

        assertTrue("unexpected uid: " + uid, uid.matches("SARTAK-[0-9A-F-]+"));
    }

    @Test
    public void uid_cannotCollideWithAnAtakDeviceUid() {
        // ATAK issues ANDROID-<hex>; the namespace prefix means the two
        // generators can never land on the same string, whatever they mint.
        for (int i = 0; i < 50; i++) {
            String uid = newInstall().getOrCreateUid();
            assertFalse(uid.startsWith("ANDROID-"));
            assertNotEquals(ATAK_UID, uid);
        }
    }

    @Test
    public void uid_isTheSameLengthEveryTime() {
        // A fixed-width UID keeps the derived session id and the CoT attribute
        // predictable, and catches a truncated or padded value early.
        int expected = newInstall().getOrCreateUid().length();

        for (int i = 0; i < 20; i++)
            assertEquals(expected, newInstall().getOrCreateUid().length());
    }

    // =========================================================================
    // Collision avoidance: resolution through the real store
    // =========================================================================

    @Test
    public void resolve_withNoAtakIdentity_usesThePersistedDeviceUid() {
        DeviceIdentityStore install = newInstall();

        IdentityManager.Identity identity = resolveFallback(install);

        assertTrue(identity.isResolved());
        assertFalse(identity.isAtakIdentity());
        assertEquals(install.getOrCreateUid(), identity.getUid());
        assertEquals(install.getFallbackCallsign(DEVICE_MODEL),
                identity.getCallsign());
    }

    @Test
    public void resolve_afterARestart_keepsTheSameFallbackIdentity() {
        IdentityManager.Identity before =
                resolveFallback(new DeviceIdentityStore(context));

        // A new store over the same preferences is what a plugin, ATAK or
        // device restart leaves behind.
        IdentityManager.Identity after =
                resolveFallback(new DeviceIdentityStore(context));

        assertEquals(before.getUid(), after.getUid());
        assertEquals(before.getCallsign(), after.getCallsign());
    }

    @Test
    public void resolve_onTwoInstalls_producesTwoDistinctIdentities() {
        IdentityManager.Identity first = resolveFallback(newInstall());
        IdentityManager.Identity second = resolveFallback(newInstall());

        assertNotEquals(first.getUid(), second.getUid());
    }

    @Test
    public void resolve_whenAtakSuppliesAnIdentity_neverMintsAFallbackUid() {
        // A UID minted needlessly would be left in preferences and become this
        // device's identity the moment ATAK's went away, splitting one searcher
        // across two UIDs mid-search.
        IdentityManager.Identity identity = IdentityManager.resolve(ATAK_UID,
                ATAK_CALLSIGN, null, null,
                fallbackBackedBy(new DeviceIdentityStore(context)));

        assertEquals(ATAK_UID, identity.getUid());
        assertNull("a fallback uid was minted and persisted anyway",
                preferences().getString(DeviceIdentityStore.KEY_UID, null));
    }

    // =========================================================================
    // Collision avoidance: what a shared UID would actually cost
    // =========================================================================

    @Test
    public void distinctUids_areStoredAsSeparateSearchers() {
        String first = newInstall().getOrCreateUid();
        String second = newInstall().getOrCreateUid();

        searchers.insertOrUpdate(first, "Pixel 7-AAAA", DEVICE_MODEL,
                1000L, 1000L, false);
        searchers.insertOrUpdate(second, "Pixel 7-BBBB", DEVICE_MODEL,
                1000L, 1000L, false);

        assertEquals(2, countSearchers());
    }

    @Test
    public void aSharedUid_collapsesTwoSearchersIntoOneRow() {
        // The failure the random UID exists to prevent: identical UIDs mean the
        // second searcher overwrites the first, and the team sees one marker
        // where there are two people.
        String shared = "ANDROID-9774d56d682e549c";
        searchers.insertOrUpdate(shared, "Pixel 7", DEVICE_MODEL,
                1000L, 1000L, false);
        searchers.insertOrUpdate(shared, "Pixel 7", DEVICE_MODEL,
                2000L, 2000L, false);

        assertEquals(1, countSearchers());
    }

    @Test
    public void aSharedUid_mergesTwoSearchersTrackPointsIntoOne() {
        String shared = "ANDROID-9774d56d682e549c";

        locations.insert(shared, "Pixel 7", -27.4705, 153.0260, 12.0,
                5.0f, 0f, 0f, 1000L, "track-" + shared + "-a");
        locations.insert(shared, "Pixel 7", -27.9000, 152.5000, 30.0,
                5.0f, 0f, 0f, 2000L, "track-" + shared + "-b");

        // Two searchers kilometres apart, indistinguishable by UID.
        assertEquals(2, countPointsForUid(shared));
    }

    @Test
    public void distinctUids_keepTrackPointsSeparate() {
        String first = newInstall().getOrCreateUid();
        String second = newInstall().getOrCreateUid();

        locations.insert(first, "Pixel 7-AAAA", -27.4705, 153.0260, 12.0,
                5.0f, 0f, 0f, 1000L, "track-" + first + "-a");
        locations.insert(second, "Pixel 7-BBBB", -27.9000, 152.5000, 30.0,
                5.0f, 0f, 0f, 2000L, "track-" + second + "-a");

        assertEquals(1, countPointsForUid(first));
        assertEquals(1, countPointsForUid(second));
    }

    @Test
    public void aGeneratedUid_roundTripsThroughTheSearcherTable() {
        // The UID is the primary key, so it has to survive storage byte for
        // byte - no case folding, no trimming, nothing needing escaping.
        String uid = newInstall().getOrCreateUid();

        searchers.insertOrUpdate(uid, "Pixel 7-AAAA", DEVICE_MODEL,
                1000L, 1000L, true);

        String[] identity = searchers.getSelfIdentity();
        assertNotNull(identity);
        assertEquals(uid, identity[0]);
    }

    @Test
    public void selfIdentity_followsTheUidNotTheCallsign() {
        // Callsigns are display text and two searchers can share one; only the
        // UID decides who "self" is.
        String self = newInstall().getOrCreateUid();
        String other = newInstall().getOrCreateUid();

        searchers.insertOrUpdate(other, "Pixel 7", DEVICE_MODEL,
                1000L, 3000L, false);
        searchers.insertOrUpdate(self, "Pixel 7", DEVICE_MODEL,
                1000L, 1000L, true);

        String[] identity = searchers.getSelfIdentity();
        assertNotNull(identity);
        assertEquals(self, identity[0]);
    }

    @Test
    public void aFallbackUid_stillMatchesItsSelfRowAfterARestart() {
        IdentityManager.Identity identity =
                resolveFallback(new DeviceIdentityStore(context));
        searchers.insertOrUpdate(identity.getUid(), identity.getCallsign(),
                DEVICE_MODEL, 1000L, 1000L, true);

        IdentityManager.Identity afterRestart =
                resolveFallback(new DeviceIdentityStore(context));

        String[] stored = searchers.getSelfIdentity();
        assertNotNull(stored);
        assertEquals("the device lost track of its own row across a restart",
                stored[0], afterRestart.getUid());
    }

    // =========================================================================
    // Collision avoidance: one device does not mint two UIDs
    // =========================================================================

    /** Enough callers to lose a race reliably if minting is not guarded. */
    private static final int RACING_CALLERS = 16;

    @Test
    public void getOrCreateUid_underConcurrentFirstCalls_mintsExactlyOneUid()
            throws Exception {
        // First launch is exactly when this happens: the capture poll and the
        // UI both ask for the identity, both find nothing stored, and both
        // mint. The device then reports under two UIDs and appears as two
        // searchers - the same failure as a collision, from the other side.
        final DeviceIdentityStore store = newInstall();

        Set<String> minted = callConcurrently(new Call() {
            @Override
            public String get() {
                return store.getOrCreateUid();
            }
        });

        assertEquals("the device minted more than one uid: " + minted,
                1, minted.size());
    }

    @Test
    public void getOrCreateUid_underConcurrentFirstCalls_persistsTheOneItReturned()
            throws Exception {
        // A UID handed out but not the one persisted is worse than a duplicate:
        // this run tracks under one, the next run under another.
        final DeviceIdentityStore store = newInstall();

        Set<String> minted = callConcurrently(new Call() {
            @Override
            public String get() {
                return store.getOrCreateUid();
            }
        });

        assertEquals(1, minted.size());
        assertEquals(minted.iterator().next(),
                preferences().getString(DeviceIdentityStore.KEY_UID, null));
    }

    @Test
    public void getOrCreateUid_acrossConcurrentStoreInstances_mintsOneUid()
            throws Exception {
        // Two stores over one preferences file are two views of one identity.
        // IdentityManager holds its own, and anything else constructing one
        // gets a second - guarding only a single instance would not be enough.
        newInstall();

        Set<String> minted = callConcurrently(new Call() {
            @Override
            public String get() {
                return new DeviceIdentityStore(context).getOrCreateUid();
            }
        });

        assertEquals("two stores minted separate uids: " + minted,
                1, minted.size());
    }

    @Test
    public void getFallbackCallsign_underConcurrentFirstCalls_agreesOnOne()
            throws Exception {
        // The callsign carries the UID's suffix, so a split identity shows up
        // on the map as two differently named markers for one searcher.
        final DeviceIdentityStore store = newInstall();

        Set<String> callsigns = callConcurrently(new Call() {
            @Override
            public String get() {
                return store.getFallbackCallsign(DEVICE_MODEL);
            }
        });

        assertEquals("the device answered to more than one callsign: "
                + callsigns, 1, callsigns.size());
    }

    @Test
    public void resolve_underConcurrentFirstCalls_yieldsOneFallbackIdentity()
            throws Exception {
        newInstall();

        Set<String> uids = callConcurrently(new Call() {
            @Override
            public String get() {
                return resolveFallback(new DeviceIdentityStore(context))
                        .getUid();
            }
        });

        assertEquals(1, uids.size());
    }

    // =========================================================================
    // A value already in preferences
    // =========================================================================

    @Test
    public void getOrCreateUid_whenPreferencesHoldALegacyUid_keepsIt() {
        // Pins the current rule: anything non-blank already stored is this
        // device's identity, whatever it looks like. That is what keeps the UID
        // stable, but it also means a UID written by the old ANDROID_ID
        // fallback would survive - and those are the values that collide across
        // cloned emulators. If such installs exist in the field, this is where
        // the migration goes.
        preferences().edit()
                .putString(DeviceIdentityStore.KEY_UID, DEVICE_ID).commit();

        assertEquals(DEVICE_ID,
                new DeviceIdentityStore(context).getOrCreateUid());
    }

    @Test
    public void getFallbackCallsign_fromALegacyUid_stillSeparatesHandsets() {
        // Even off a non-namespaced UID the suffix has to come from the stored
        // value, or a fleet on the old scheme all answers to "Pixel 7".
        preferences().edit()
                .putString(DeviceIdentityStore.KEY_UID, DEVICE_ID).commit();

        assertEquals("Pixel 7-9774", new DeviceIdentityStore(context)
                .getFallbackCallsign(DEVICE_MODEL));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** One concurrent caller's work; kept an interface for Java 7 source. */
    private interface Call {
        String get();
    }

    /**
     * Runs {@code call} on {@link #RACING_CALLERS} threads released together,
     * and returns the distinct values they saw. One value means they agreed.
     */
    private Set<String> callConcurrently(final Call call) throws Exception {
        final CyclicBarrier start = new CyclicBarrier(RACING_CALLERS);
        final CountDownLatch done = new CountDownLatch(RACING_CALLERS);
        final Set<String> results =
                Collections.synchronizedSet(new HashSet<String>());
        final List<Throwable> failures =
                Collections.synchronizedList(new ArrayList<Throwable>());

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < RACING_CALLERS; i++) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        start.await(10, TimeUnit.SECONDS);
                        results.add(call.get());
                    } catch (Throwable t) {
                        failures.add(t);
                    } finally {
                        done.countDown();
                    }
                }
            });
            threads.add(thread);
            thread.start();
        }

        assertTrue("callers did not finish", done.await(30, TimeUnit.SECONDS));
        for (Thread thread : threads)
            thread.join(10000L);
        if (!failures.isEmpty())
            throw new AssertionError("a caller threw: " + failures.get(0),
                    failures.get(0));
        return results;
    }

    /**
     * A store with no identity yet - a fresh install, which is also the state a
     * second device is in on first launch.
     */
    private DeviceIdentityStore newInstall() {
        preferences().edit().clear().commit();
        return new DeviceIdentityStore(context);
    }

    private IdentityManager.Identity resolveFallback(DeviceIdentityStore store) {
        return IdentityManager.resolve(null, null, null, null,
                fallbackBackedBy(store));
    }

    /** Mirrors how IdentityManager wires its own DeviceFallback. */
    private static IdentityManager.DeviceFallback fallbackBackedBy(
            final DeviceIdentityStore store) {
        return new IdentityManager.DeviceFallback() {
            @Override
            public String getFallbackUid() {
                return store.getOrCreateUid();
            }

            @Override
            public String getFallbackCallsign() {
                return store.getFallbackCallsign(DEVICE_MODEL);
            }
        };
    }

    private SharedPreferences preferences() {
        return context.getSharedPreferences(DeviceIdentityStore.PREFS_NAME,
                Context.MODE_PRIVATE);
    }

    private int countSearchers() {
        return countOf("SELECT COUNT(*) FROM searcher_identity", null);
    }

    private int countPointsForUid(String uid) {
        return countOf("SELECT COUNT(*) FROM location_points WHERE uid = ?",
                new String[]{uid});
    }

    private int countOf(String sql, String[] args) {
        Cursor cursor = dbHelper.getReadableDatabase().rawQuery(sql, args);
        int count = 0;
        if (cursor.moveToFirst())
            count = cursor.getInt(0);
        cursor.close();
        return count;
    }

    /** Counts lookups so precedence can be asserted, not just the outcome. */
    private static class RecordingFallback
            implements IdentityManager.DeviceFallback {

        private final String deviceId;
        private final String deviceModel;
        int deviceIdCalls;
        int deviceModelCalls;

        RecordingFallback(String deviceId, String deviceModel) {
            this.deviceId = deviceId;
            this.deviceModel = deviceModel;
        }

        @Override
        public String getFallbackUid() {
            deviceIdCalls++;
            return deviceId;
        }

        @Override
        public String getFallbackCallsign() {
            deviceModelCalls++;
            return deviceModel;
        }
    }
}
