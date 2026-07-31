package com.atakmap.android.plugintemplate.runtime;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the UID the plugin falls back to when ATAK supplies no
 * identity: a namespaced random UUID, minted once and persisted, plus a
 * callsign that stays distinct across identical handsets.
 * <p>
 * The point of the exercise is that two devices never end up on one UID, so
 * the tests cover both halves of that: uniqueness between installs, and
 * stability within an install across restarts.
 * <p>
 * Dependencies (build.gradle):
 *   testImplementation 'junit:junit:4.13.2'
 *   testImplementation 'org.robolectric:robolectric:4.9'
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, manifest = Config.NONE)
public class DeviceIdentityStoreTest {

    private Context context;
    private DeviceIdentityStore store;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        preferences().edit().clear().commit();
        store = new DeviceIdentityStore(context);
    }

    // -------------------------------------------------------------------------
    // UID generation
    // -------------------------------------------------------------------------

    @Test
    public void getOrCreateUid_mintsANamespacedRandomUuid() {
        String uid = store.getOrCreateUid();

        assertTrue("unexpected uid: " + uid,
                uid.startsWith(DeviceIdentityStore.UID_PREFIX));

        // A canonical UUID round-trips through java.util.UUID unchanged, which
        // covers both the 8-4-4-4-12 shape and the hex digits.
        String body = uid.substring(DeviceIdentityStore.UID_PREFIX.length());
        assertEquals(body.toLowerCase(),
                UUID.fromString(body).toString());
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

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

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
        assertEquals(uid, preferences()
                .getString(DeviceIdentityStore.KEY_UID, null));
    }

    // -------------------------------------------------------------------------
    // Fallback callsign
    // -------------------------------------------------------------------------

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
        assertEquals("Pixel 7",
                DeviceIdentityStore.buildFallbackCallsign("Pixel 7", "SARTAK-"));
    }

    @Test
    public void getFallbackCallsign_isStableAcrossStoreInstances() {
        String first = store.getFallbackCallsign("Pixel 7");

        assertEquals(first, new DeviceIdentityStore(context)
                .getFallbackCallsign("Pixel 7"));
    }

    private SharedPreferences preferences() {
        return context.getSharedPreferences(DeviceIdentityStore.PREFS_NAME,
                Context.MODE_PRIVATE);
    }
}
