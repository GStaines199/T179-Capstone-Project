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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the track-session UID generated when a searcher's GNSS
 * capture starts: {@code track-<uid>-<random UUID>}, one active session per
 * searcher UID, reused across restarts and regenerated after the track is
 * cleared.
 * <p>
 * Dependencies (build.gradle):
 *   testImplementation 'junit:junit:4.13.2'
 *   testImplementation 'org.robolectric:robolectric:4.9'
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, manifest = Config.NONE)
public class SearchTrackSessionUidTest {

    private static final String UID = "ANDROID-1234567890abcdef";
    private static final String CALLSIGN = "ALPHA";
    private static final String OTHER_UID = "ANDROID-fedcba0987654321";
    private static final String OTHER_CALLSIGN = "BRAVO";

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

        assertSessionUid(UID, manager.getActiveSessionId());
    }

    @Test
    public void startOrResume_generatesDistinctUidPerSearcher() {
        manager.startOrResume(UID, CALLSIGN);
        String first = manager.getActiveSessionId();

        manager.startOrResume(OTHER_UID, OTHER_CALLSIGN);
        String second = manager.getActiveSessionId();

        assertNotEquals(first, second);
        assertSessionUid(UID, first);
        assertSessionUid(OTHER_UID, second);
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
        assertSessionUid(UID, manager.getActiveSessionId());
    }

    @Test
    public void clearCurrentTrack_whileRecording_generatesANewSessionUid() {
        manager.startOrResume(UID, CALLSIGN);
        String first = manager.getActiveSessionId();

        manager.clearCurrentTrack();

        String second = manager.getActiveSessionId();
        assertNotEquals(first, second);
        assertSessionUid(UID, second);
    }

    @Test
    public void recordLocation_forANewSearcherUid_startsANewSession() {
        manager.recordLocation(UID, CALLSIGN, -27.4705, 153.0260, 12.0,
                7.5, 90.0, 1.2, 1000L);
        String first = manager.getActiveSessionId();

        manager.recordLocation(OTHER_UID, OTHER_CALLSIGN,
                -27.4710, 153.0265, 12.0, 7.5, 90.0, 1.2, 2000L);

        assertNotEquals(first, manager.getActiveSessionId());
        assertSessionUid(OTHER_UID, manager.getActiveSessionId());
    }

    @Test
    public void recordLocation_whenPaused_generatesNoSessionUid() {
        manager.toggleRecording();

        manager.recordLocation(UID, CALLSIGN, -27.4705, 153.0260, 12.0,
                7.5, 90.0, 1.2, 1000L);

        assertNull(manager.getActiveSessionId());
    }

    /** Asserts a session id is "track-&lt;uid&gt;-&lt;random UUID&gt;". */
    private static void assertSessionUid(String expectedUid, String sessionId) {
        String prefix = "track-" + expectedUid + "-";
        assertTrue("unexpected session id: " + sessionId,
                sessionId.startsWith(prefix));

        // A canonical random UUID round-trips through java.util.UUID unchanged,
        // which covers both the 8-4-4-4-12 shape and the lower case hex digits.
        String suffix = sessionId.substring(prefix.length());
        assertEquals(suffix, UUID.fromString(suffix).toString());
    }
}
