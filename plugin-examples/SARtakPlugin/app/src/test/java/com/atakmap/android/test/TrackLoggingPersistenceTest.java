package com.atakmap.android.test;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

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

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Sprint 2 — "Validate local SQLite track logging: location points persisting
 * across app restarts".
 * <p>
 * A searcher's track is the record of where they have physically been, so it
 * has to outlive the plugin process. ATAK can be killed by the Android
 * low-memory killer mid-search, the operator can force-stop it, or the handset
 * can simply run flat and be recharged. In every one of those cases the track
 * recorded so far must still be on disk when ATAK comes back, and the searcher
 * must carry on appending to the <em>same</em> session rather than silently
 * starting a fresh one. A fragmented or truncated track is worse than no track,
 * because it looks complete while under-reporting the ground actually covered.
 *
 * <h2>How a restart is simulated</h2>
 * {@link DatabaseHelper} is a {@link android.database.sqlite.SQLiteOpenHelper}
 * over the on-disk file {@code databases/sar_database.db}. Under Robolectric
 * that file is a real file in the per-test temp directory — see
 * {@link #databaseIsFileBackedAndOutlivesTheHelper()}, which pins that fact so
 * the rest of this class cannot quietly become vacuous if the harness ever
 * switches to an in-memory database.
 * <p>
 * {@link #restart()} therefore closes the helper and builds a brand new helper,
 * both repositories and a new {@link SearchTrackManager} over the same file.
 * That reproduces exactly what a process restart does: every scrap of in-memory
 * state is discarded, and only what reached SQLite survives.
 * <p>
 * Note it deliberately uses {@code new DatabaseHelper(context)} rather than
 * {@link DatabaseHelper#getInstance(Context)}. The singleton's static field is
 * re-initialised by a real process restart anyway, so constructing directly is
 * the honest simulation; reusing the cached instance would keep the already-open
 * connection alive and prove nothing.
 *
 * <h2>Dependencies (build.gradle)</h2>
 * <pre>
 *   testImplementation 'junit:junit:4.13.2'
 *   testImplementation 'org.robolectric:robolectric:4.9'
 * </pre>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, manifest = Config.NONE)
public class TrackLoggingPersistenceTest {

    private static final String UID = "ANDROID-1234567890abcdef";
    private static final String CALLSIGN = "ALPHA";
    private static final String OTHER_UID = "ANDROID-fedcba0987654321";
    private static final String OTHER_CALLSIGN = "BRAVO";

    /** A realistic 13-digit epoch-millis timestamp, not a toy value. */
    private static final long T0 = 1_754_092_800_000L;

    private Context context;
    private DatabaseHelper dbHelper;
    private LocationRepository locations;
    private TrackSessionRepository sessions;
    private SearchTrackManager tracks;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        open();
    }

    @After
    public void tearDown() {
        dbHelper.close();
    }

    private void open() {
        dbHelper = new DatabaseHelper(context);
        locations = new LocationRepository(dbHelper);
        sessions = new TrackSessionRepository(dbHelper);
        tracks = new SearchTrackManager(sessions, locations);
    }

    /**
     * Simulates an ATAK process restart: drop the open connection and every
     * object holding in-memory state, then reopen the same database file.
     */
    private void restart() {
        dbHelper.close();
        open();
    }

    // ---------------------------------------------------------------
    // The harness itself
    // ---------------------------------------------------------------

    /**
     * Guards every other test in this class. If the plugin database were ever
     * in-memory, closing the helper would wipe it and a "survives restart"
     * assertion would still pass trivially on a freshly recreated empty table.
     * Pinning that the file exists, is non-empty and keeps its bytes after
     * close makes the restart simulation meaningful.
     */
    @Test
    public void databaseIsFileBackedAndOutlivesTheHelper() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        File file = new File(db.getPath());
        assertTrue("plugin database is not file backed: " + db.getPath(),
                file.exists());

        locations.insert(UID, CALLSIGN, -27.4705, 153.0260, 12.0,
                7.5f, 90.0f, 1.2f, T0, "session-file-check");
        dbHelper.close();

        assertTrue("database file vanished when the helper closed",
                file.exists());
        assertTrue("database file is empty after close", file.length() > 0L);

        open(); // leave the fixture usable for tearDown
    }

    // ---------------------------------------------------------------
    // Location points survive a restart
    // ---------------------------------------------------------------

    @Test
    public void locationPoints_surviveRestart() {
        locations.insert(UID, CALLSIGN, -27.4705, 153.0260, 12.0,
                7.5f, 90.0f, 1.2f, T0, "session1");
        locations.insert(UID, CALLSIGN, -27.4710, 153.0265, 13.0,
                6.5f, 91.0f, 1.3f, T0 + 10_000L, "session1");
        assertEquals(2, locations.countPointsInSession("session1"));

        restart();

        assertEquals("track points were lost across the restart",
                2, locations.countPointsInSession("session1"));
        List<double[]> points = locations.getPointsForSession("session1");
        assertEquals(2, points.size());
        assertEquals(-27.4705, points.get(0)[0], 1e-9);
        assertEquals(153.0260, points.get(0)[1], 1e-9);
    }

    /**
     * {@code getPointsForSession} only projects lat/lon/timestamp/accuracy, so
     * a restart could silently drop the columns it does not read. The recorded
     * uid, callsign, altitude, bearing and speed are all part of the track
     * record, so this reads the stored row directly to prove the whole row
     * survives rather than just the four fields the map happens to use.
     */
    @Test
    public void everyRecordedColumn_survivesRestartWithFullFidelity() {
        locations.insert(UID, CALLSIGN, -27.470512, 153.026042, 42.5,
                7.5f, 271.25f, 1.85f, T0, "session-fidelity");

        restart();

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query("location_points",
                new String[]{"uid", "callsign", "latitude", "longitude",
                        "altitude", "accuracy_meters", "bearing_degrees",
                        "speed_mps", "timestamp", "session_id"},
                "session_id = ?", new String[]{"session-fidelity"},
                null, null, null);
        try {
            assertTrue("stored row missing after restart", c.moveToFirst());
            assertEquals(UID, c.getString(0));
            assertEquals(CALLSIGN, c.getString(1));
            assertEquals(-27.470512, c.getDouble(2), 1e-9);
            assertEquals(153.026042, c.getDouble(3), 1e-9);
            assertEquals(42.5, c.getDouble(4), 1e-9);
            assertEquals(7.5f, c.getFloat(5), 1e-6f);
            assertEquals(271.25f, c.getFloat(6), 1e-6f);
            assertEquals(1.85f, c.getFloat(7), 1e-6f);
            assertEquals(T0, c.getLong(8));
            assertEquals("session-fidelity", c.getString(9));
        } finally {
            c.close();
        }
    }

    @Test
    public void pointOrdering_isPreservedAcrossRestart() {
        locations.insert(UID, CALLSIGN, -27.470, 153.020, 10.0,
                5f, 0f, 0f, T0 + 3000L, "session-order");
        locations.insert(UID, CALLSIGN, -27.471, 153.021, 10.0,
                5f, 0f, 0f, T0, "session-order");
        locations.insert(UID, CALLSIGN, -27.472, 153.022, 10.0,
                5f, 0f, 0f, T0 + 1000L, "session-order");

        restart();

        List<double[]> points = locations.getPointsForSession("session-order");
        assertEquals(3, points.size());
        assertEquals(T0, (long) points.get(0)[2]);
        assertEquals(T0 + 1000L, (long) points.get(1)[2]);
        assertEquals(T0 + 3000L, (long) points.get(2)[2]);
    }

    @Test
    public void separateSessions_stayIsolatedAcrossRestart() {
        locations.insert(UID, CALLSIGN, -27.470, 153.020, 10.0,
                5f, 0f, 0f, T0, "sessionA");
        locations.insert(OTHER_UID, OTHER_CALLSIGN, -27.480, 153.030, 10.0,
                5f, 0f, 0f, T0, "sessionB");

        restart();

        assertEquals(1, locations.countPointsInSession("sessionA"));
        assertEquals(1, locations.countPointsInSession("sessionB"));
        assertEquals(-27.470,
                locations.getPointsForSession("sessionA").get(0)[0], 1e-9);
        assertEquals(-27.480,
                locations.getPointsForSession("sessionB").get(0)[0], 1e-9);
    }

    // ---------------------------------------------------------------
    // Session continuity across a restart
    // ---------------------------------------------------------------

    @Test
    public void activeSession_remainsActiveAcrossRestart() {
        tracks.startOrResume(UID, CALLSIGN);
        String sessionId = tracks.getActiveSessionId();
        assertNotNull(sessionId);

        restart();

        assertEquals("the active session was lost across the restart",
                sessionId, sessions.getActiveSessionId(UID));
    }

    /**
     * The behaviour the whole task hangs on. After a restart the new
     * {@link SearchTrackManager} has no in-memory session, so it must recover
     * the active one from SQLite instead of minting a new id — otherwise the
     * searcher's track silently splits into two partial tracks.
     */
    @Test
    public void startOrResume_afterRestart_resumesTheSameSession() {
        tracks.startOrResume(UID, CALLSIGN);
        String before = tracks.getActiveSessionId();

        restart();
        tracks.startOrResume(UID, CALLSIGN);

        assertEquals("restart started a new session instead of resuming",
                before, tracks.getActiveSessionId());
    }

    /**
     * End-to-end statement of the acceptance criterion: points logged either
     * side of a restart belong to one continuous track, in time order.
     */
    @Test
    public void pointsLoggedEitherSideOfARestart_formOneContinuousTrack() {
        tracks.recordLocation(UID, CALLSIGN, -27.4705, 153.0260, 12.0,
                7.5, 90.0, 1.2, T0);
        tracks.recordLocation(UID, CALLSIGN, -27.4710, 153.0265, 12.0,
                7.5, 90.0, 1.2, T0 + 10_000L);
        String sessionId = tracks.getActiveSessionId();

        restart();

        tracks.recordLocation(UID, CALLSIGN, -27.4715, 153.0270, 12.0,
                7.5, 90.0, 1.2, T0 + 20_000L);
        tracks.recordLocation(UID, CALLSIGN, -27.4720, 153.0275, 12.0,
                7.5, 90.0, 1.2, T0 + 30_000L);

        assertEquals("post-restart points went into a different session",
                sessionId, tracks.getActiveSessionId());

        List<double[]> points = tracks.getTrackPoints();
        assertEquals("track fragmented across the restart", 4, points.size());
        assertEquals(T0, (long) points.get(0)[2]);
        assertEquals(T0 + 10_000L, (long) points.get(1)[2]);
        assertEquals(T0 + 20_000L, (long) points.get(2)[2]);
        assertEquals(T0 + 30_000L, (long) points.get(3)[2]);
        assertEquals(-27.4720, points.get(3)[0], 1e-9);
    }

    /**
     * {@code SearchTrackManager} restores {@code sessionStartedAt} from the
     * stored session on resume. If that were lost, the elapsed duration shown
     * in the track details would reset to zero on every restart and
     * under-report how long the searcher has been out.
     */
    @Test
    public void sessionStartTimestamp_survivesRestart() {
        tracks.startOrResume(UID, CALLSIGN);
        String sessionId = tracks.getActiveSessionId();
        long startedAt = sessions.getSessionStartTimestamp(sessionId);
        assertTrue("session start timestamp was never recorded", startedAt > 0L);

        restart();

        assertEquals("session start timestamp was lost across the restart",
                startedAt, sessions.getSessionStartTimestamp(sessionId));
    }

    /**
     * {@code point_count} is a denormalised counter on the session row,
     * maintained by {@code incrementPointCount}. No production code reads it
     * back today, so it is asserted directly against the stored column.
     */
    @Test
    public void pointCount_survivesRestartAndKeepsIncrementing() {
        tracks.recordLocation(UID, CALLSIGN, -27.4705, 153.0260, 12.0,
                7.5, 90.0, 1.2, T0);
        tracks.recordLocation(UID, CALLSIGN, -27.4710, 153.0265, 12.0,
                7.5, 90.0, 1.2, T0 + 10_000L);
        String sessionId = tracks.getActiveSessionId();
        assertEquals(2, storedPointCount(sessionId));

        restart();

        assertEquals("point_count was lost across the restart",
                2, storedPointCount(sessionId));

        tracks.recordLocation(UID, CALLSIGN, -27.4715, 153.0270, 12.0,
                7.5, 90.0, 1.2, T0 + 20_000L);

        assertEquals("point_count did not continue from its stored value",
                3, storedPointCount(sessionId));
        assertEquals(3, locations.countPointsInSession(sessionId));
    }

    @Test
    public void closedSession_staysClosedAcrossRestart() {
        tracks.startOrResume(UID, CALLSIGN);
        String sessionId = tracks.getActiveSessionId();
        sessions.closeSession(sessionId, T0 + 60_000L);

        restart();

        assertNull("a closed session came back as active after the restart",
                sessions.getActiveSessionId(UID));
        // The points of a closed session are history and must be kept.
        assertEquals(sessionId, storedSessionIdRegardlessOfState(sessionId));
    }

    @Test
    public void separateSearchers_keepIndependentSessionsAcrossRestart() {
        tracks.startOrResume(UID, CALLSIGN);
        String alpha = tracks.getActiveSessionId();
        tracks.startOrResume(OTHER_UID, OTHER_CALLSIGN);
        String bravo = tracks.getActiveSessionId();

        restart();

        assertEquals(alpha, sessions.getActiveSessionId(UID));
        assertEquals(bravo, sessions.getActiveSessionId(OTHER_UID));
    }

    /**
     * A long search is many restarts, not one. This walks five of them and
     * checks the track keeps accumulating into a single session.
     */
    @Test
    public void trackKeepsAccumulatingAcrossRepeatedRestarts() {
        tracks.startOrResume(UID, CALLSIGN);
        String sessionId = tracks.getActiveSessionId();

        for (int i = 0; i < 5; i++) {
            tracks.recordLocation(UID, CALLSIGN, -27.4705 - (i * 0.001),
                    153.0260 + (i * 0.001), 12.0, 7.5, 90.0, 1.2,
                    T0 + (i * 10_000L));
            restart();
            tracks.startOrResume(UID, CALLSIGN);
        }

        assertEquals("session id changed during repeated restarts",
                sessionId, tracks.getActiveSessionId());
        assertEquals("points lost during repeated restarts",
                5, tracks.getTrackPoints().size());
        assertEquals(5, storedPointCount(sessionId));
    }

    @Test
    public void clearedTrack_staysClearedAcrossRestart() {
        tracks.recordLocation(UID, CALLSIGN, -27.4705, 153.0260, 12.0,
                7.5, 90.0, 1.2, T0);
        String cleared = tracks.getActiveSessionId();

        tracks.clearCurrentTrack();
        String replacement = tracks.getActiveSessionId();

        restart();

        assertEquals("cleared points came back after the restart",
                0, locations.countPointsInSession(cleared));
        assertEquals("the replacement session was lost across the restart",
                replacement, sessions.getActiveSessionId(UID));
    }

    // ---------------------------------------------------------------
    // Edge cases and stored-value fidelity
    // ---------------------------------------------------------------

    @Test
    public void restartWithAnEmptyDatabase_isSafe() {
        restart();

        assertNull(sessions.getActiveSessionId(UID));
        assertEquals(0, locations.countPointsInSession("no-such-session"));
        assertNotNull(locations.getPointsForSession("no-such-session"));
        assertEquals(0, locations.getPointsForSession("no-such-session").size());
        assertEquals(0L, sessions.getSessionStartTimestamp("no-such-session"));
    }

    /**
     * SAR positions are compared at sub-metre scale, so the stored latitude and
     * longitude must come back bit-for-bit rather than rounded to float. At
     * this latitude 1e-7 degrees is roughly a centimetre.
     */
    @Test
    public void coordinatePrecision_survivesRestart() {
        double latitude = -27.470512345678;
        double longitude = 153.026042135791;
        locations.insert(UID, CALLSIGN, latitude, longitude, 12.0,
                7.5f, 90.0f, 1.2f, T0, "session-precision");

        restart();

        List<double[]> points =
                locations.getPointsForSession("session-precision");
        assertEquals(latitude, points.get(0)[0], 0.0);
        assertEquals(longitude, points.get(0)[1], 0.0);
    }

    /**
     * Timestamps are 13-digit epoch millis and are carried through
     * {@code getPointsForSession} in a {@code double[]}. A double holds 53 bits
     * of mantissa, so values of this magnitude are exact — this pins that,
     * because a drop to float would shift a fix by weeks.
     */
    @Test
    public void epochMillisTimestamps_surviveRestartExactly() {
        locations.insert(UID, CALLSIGN, -27.4705, 153.0260, 12.0,
                7.5f, 90.0f, 1.2f, T0 + 999L, "session-time");

        restart();

        List<double[]> points = locations.getPointsForSession("session-time");
        assertEquals(T0 + 999L, (long) points.get(0)[2]);

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT timestamp FROM location_points WHERE session_id = ?",
                new String[]{"session-time"});
        try {
            assertTrue(c.moveToFirst());
            assertEquals(T0 + 999L, c.getLong(0));
        } finally {
            c.close();
        }
    }

    // ---------------------------------------------------------------
    // Helpers reading columns no production getter exposes
    // ---------------------------------------------------------------

    private int storedPointCount(String sessionId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT point_count FROM track_sessions WHERE session_id = ?",
                new String[]{sessionId});
        try {
            return c.moveToFirst() ? c.getInt(0) : -1;
        } finally {
            c.close();
        }
    }

    private String storedSessionIdRegardlessOfState(String sessionId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT session_id FROM track_sessions WHERE session_id = ?",
                new String[]{sessionId});
        try {
            return c.moveToFirst() ? c.getString(0) : null;
        } finally {
            c.close();
        }
    }
}
