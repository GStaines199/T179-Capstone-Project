package com.atakmap.android.test;

import android.content.Context;

import com.atakmap.android.plugintemplate.database.DatabaseHelper;
import com.atakmap.android.plugintemplate.database.LocationRepository;
import com.atakmap.android.plugintemplate.database.TrackSessionRepository;
import com.atakmap.android.plugintemplate.grid.SearchTrackManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the track-session UID generated when a searcher's GNSS
 * capture starts: "track-<uid>-<random UUID>", one active session per searcher
 * UID, reused across restarts and regenerated after the track is cleared.
 *
 * Dependencies (build.gradle):
 *   testImplementation 'junit:junit:4.13.2'
 *   testImplementation 'org.robolectric:robolectric:4.9'
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, manifest = Config.NONE)
public class SearchTrackSessionUidTest {

    private static final String UID = "ANDROID-1234567890abcdef";
    private static final String CALLSIGN = "ALPHA";

    private static final Pattern UUID_SUFFIX = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private DatabaseHelper dbHelper;
    private TrackSessionRepository sessions;
    private SearchTrackManager manager;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        dbHelper = new DatabaseHelper(context);
        sessions = new TrackSessionRepository(dbHelper);
        manager = new SearchTrackManager(sessions,
                new LocationRepository(dbHelper));
    }

    @After
    public void tearDown() {
        dbHelper.close();
    }

    @Test
    public void startOrResume_sessionUidEmbedsSearcherUidAndRandomUuid() {
        manager.startOrResume(UID, CALLSIGN);

        String sessionId = manager.getActiveSessionId();
        assertTrue("unexpected session id: " + sessionId,
                sessionId.startsWith("track-" + UID + "-"));
        assertTrue("suffix is not a UUID: " + sessionId,
                UUID_SUFFIX.matcher(uuidSuffix(sessionId, UID)).matches());
        // Parses as a UUID, so it round-trips through java.util.UUID.
        UUID.fromString(uuidSuffix(sessionId, UID));
    }

    @Test
    public void startOrResume_generatesDistinctUidPerSearcher() {
        manager.startOrResume(UID, CALLSIGN);
        String first = manager.getActiveSessionId();

        manager.startOrResume("ANDROID-fedcba0987654321", "BRAVO");
        String second = manager.getActiveSessionId();

        assertNotEquals(first, second);
        assertTrue(second.startsWith("track-ANDROID-fedcba0987654321-"));
    }

    @Test
    public void startOrResume_reusesTheActiveSessionForTheSameSearcher() {
        manager.startOrResume(UID, CALLSIGN);
        String first = manager.getActiveSessionId();

        manager.startOrResume(UID, CALLSIGN);

        assertEquals(first, manager.getActiveSessionId());
    }

    @Test
    public void startOrResume_afterSessionClosed_generatesANewUid() {
        manager.startOrResume(UID, CALLSIGN);
        String first = manager.getActiveSessionId();
        sessions.closeSession(first, System.currentTimeMillis());

        manager.startOrResume(UID, CALLSIGN);

        assertNotEquals(first, manager.getActiveSessionId());
        assertTrue(manager.getActiveSessionId()
                .startsWith("track-" + UID + "-"));
    }

    @Test
    public void clearCurrentTrack_whileRecording_generatesANewSessionUid() {
        manager.startOrResume(UID, CALLSIGN);
        String first = manager.getActiveSessionId();

        manager.clearCurrentTrack();

        String second = manager.getActiveSessionId();
        assertNotEquals(first, second);
        assertTrue(second.startsWith("track-" + UID + "-"));
        UUID.fromString(uuidSuffix(second, UID));
    }

    @Test
    public void recordLocation_forANewSearcherUid_startsANewSession() {
        manager.recordLocation(UID, CALLSIGN, -27.4705, 153.0260, 12.0,
                7.5, 90.0, 1.2, 1000L);
        String first = manager.getActiveSessionId();

        manager.recordLocation("ANDROID-fedcba0987654321", "BRAVO",
                -27.4710, 153.0265, 12.0, 7.5, 90.0, 1.2, 2000L);

        assertNotEquals(first, manager.getActiveSessionId());
        assertTrue(manager.getActiveSessionId()
                .startsWith("track-ANDROID-fedcba0987654321-"));
    }

    @Test
    public void recordLocation_whenPaused_generatesNoSessionUid() {
        manager.toggleRecording();

        manager.recordLocation(UID, CALLSIGN, -27.4705, 153.0260, 12.0,
                7.5, 90.0, 1.2, 1000L);

        assertNull(manager.getActiveSessionId());
    }

    private static String uuidSuffix(String sessionId, String uid) {
        return sessionId.substring(("track-" + uid + "-").length());
    }
}
