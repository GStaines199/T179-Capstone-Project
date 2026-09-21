package com.atakmap.android.plugintemplate.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import com.atakmap.android.plugintemplate.runtime.PluginHealthManager;
import com.atakmap.android.plugintemplate.runtime.PluginHealthState;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Storage health is measured, not asserted.
 *
 * <p>The plugin used to report "Local storage ready" unconditionally at
 * startup. That made the storage half of {@link PluginHealthState#INACTIVE}
 * unreachable: with SQLite broken the plugin claimed health and logged nothing,
 * which is the one failure a searcher cannot notice while it is happening.
 *
 * <p>These tests drive {@link StorageAvailability} against a real Robolectric
 * SQLite database for the cases that can be staged on a file, and through the
 * {@link StorageAvailability.Opener} seam for the ones that cannot -- a
 * throwing open, a null handle, a handle that is already closed. The last group
 * carries the probe result into a real {@link PluginHealthManager} to confirm
 * the state actually comes out INACTIVE, which is the behaviour the audit asked
 * for rather than merely the flag behind it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, manifest = Config.NONE)
public class StorageHealthTest {

    private DatabaseHelper dbHelper;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        dbHelper = new DatabaseHelper(context);
    }

    @After
    public void tearDown() {
        dbHelper.close();
    }

    /** Returns a fixed handle, so a test can hand the probe a broken one. */
    private static StorageAvailability.Opener opening(final SQLiteDatabase db) {
        return new StorageAvailability.Opener() {
            @Override
            public SQLiteDatabase openForWriting() {
                return db;
            }
        };
    }

    /** Fails the way SQLiteOpenHelper does when the file cannot be opened. */
    private static StorageAvailability.Opener throwing(
            final RuntimeException e) {
        return new StorageAvailability.Opener() {
            @Override
            public SQLiteDatabase openForWriting() {
                throw e;
            }
        };
    }

    // --- the happy path ---------------------------------------------------

    @Test
    public void probe_whenTheDatabaseOpensCleanly_isReady() {
        StorageAvailability.Result result = StorageAvailability.probe(dbHelper);

        assertTrue(result.isReady());
        assertEquals(StorageAvailability.READY_MESSAGE, result.getMessage());
    }

    @Test
    public void probe_onARealHelper_findsEveryTableTheRuntimeNeeds() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Guards against REQUIRED_TABLES and the CREATE statements drifting
        // apart: each named table must both exist and actually be checked for.
        for (String table : StorageAvailability.REQUIRED_TABLES) {
            assertTrue("schema is missing " + table,
                    StorageAvailability.probe(opening(db)).isReady());
            db.execSQL("DROP TABLE " + table);
            assertFalse("probe passed without " + table,
                    StorageAvailability.probe(opening(db)).isReady());
            dbHelper.onCreate(db);
        }
    }

    // --- the open itself --------------------------------------------------

    @Test
    public void probe_whenThereIsNoHelperAtAll_isNotReady() {
        StorageAvailability.Result result =
                StorageAvailability.probe((DatabaseHelper) null);

        assertFalse(result.isReady());
        assertTrue(result.getMessage(),
                result.getMessage().contains("no database was created"));
    }

    @Test
    public void probe_whenThereIsNoOpener_isNotReady() {
        StorageAvailability.Result result =
                StorageAvailability.probe((StorageAvailability.Opener) null);

        assertFalse(result.isReady());
        // Exact, for the same reason as the closed-handle case: without the
        // explicit guard the null still reaches the catch and reports
        // unavailable, but as a bare "NullPointerException".
        assertEquals("Storage unavailable: no database was created",
                result.getMessage());
    }

    @Test
    public void probe_whenOpeningThrows_isNotReadyAndSaysWhy() {
        StorageAvailability.Result result = StorageAvailability.probe(
                throwing(new SQLiteException("disk I/O error")));

        assertFalse(result.isReady());
        assertTrue(result.getMessage(),
                result.getMessage().contains("disk I/O error"));
    }

    @Test
    public void probe_whenOpeningThrowsWithoutAMessage_namesTheFailureAnyway() {
        StorageAvailability.Result result =
                StorageAvailability.probe(throwing(new IllegalStateException()));

        assertFalse(result.isReady());
        assertTrue(result.getMessage(),
                result.getMessage().contains("IllegalStateException"));
    }

    @Test
    public void probe_catchesAnyRuntimeFailure_notJustSqliteOnes() {
        // The catch is deliberately broad: an unforeseen storage fault must not
        // be able to leave the plugin claiming health.
        StorageAvailability.Result result = StorageAvailability.probe(
                throwing(new NullPointerException("helper was torn down")));

        assertFalse(result.isReady());
    }

    // --- a handle that opened but is not usable ---------------------------

    @Test
    public void probe_whenTheOpenReturnsNothing_isNotReady() {
        StorageAvailability.Result result =
                StorageAvailability.probe(opening(null));

        assertFalse(result.isReady());
        assertEquals("Storage unavailable: the database did not open",
                result.getMessage());
    }

    @Test
    public void probe_whenTheHandleIsAlreadyClosed_isNotReady() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.close();

        StorageAvailability.Result result =
                StorageAvailability.probe(opening(db));

        assertFalse(result.isReady());
        // Asserted exactly, not by keyword. Touching a closed handle throws an
        // IllegalStateException whose own message contains "closed", so a
        // substring check here would pass just as well with the explicit
        // isOpen branch deleted -- and the operator would get a raw SQLite
        // dump instead of a sentence.
        assertEquals("Storage unavailable: the database closed immediately",
                result.getMessage());
    }

    @Test
    public void probe_whenStorageIsReadOnly_isNotReady() {
        // A full or remounted filesystem opens fine and fails every write, so a
        // handle that looks healthy is not enough on its own.
        String path = dbHelper.getWritableDatabase().getPath();
        dbHelper.close();
        SQLiteDatabase readOnly = SQLiteDatabase.openDatabase(path, null,
                SQLiteDatabase.OPEN_READONLY);

        StorageAvailability.Result result =
                StorageAvailability.probe(opening(readOnly));

        assertFalse(result.isReady());
        assertEquals("Storage unavailable: storage is read-only, so no track"
                + " can be logged", result.getMessage());
        readOnly.close();
    }

    // --- a schema that did not finish being built -------------------------

    @Test
    public void probe_whenTheTrackTableIsMissing_isNotReadyAndNamesIt() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.execSQL("DROP TABLE location_points");

        StorageAvailability.Result result =
                StorageAvailability.probe(opening(db));

        assertFalse(result.isReady());
        assertEquals("Storage unavailable: the location_points table is"
                + " missing", result.getMessage());
    }

    @Test
    public void probe_whenTheSessionTableIsMissing_isNotReady() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.execSQL("DROP TABLE track_sessions");

        assertFalse(StorageAvailability.probe(opening(db)).isReady());
    }

    @Test
    public void probe_whenTheIdentityTableIsMissing_isNotReady() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.execSQL("DROP TABLE searcher_identity");

        assertFalse(StorageAvailability.probe(opening(db)).isReady());
    }

    // --- no failure may ever read as ready --------------------------------

    @Test
    public void probe_noFailingPathEverReportsTheReadyMessage() {
        SQLiteDatabase closed = dbHelper.getWritableDatabase();
        closed.close();
        StorageAvailability.Opener[] broken = {
                null,
                opening(null),
                opening(closed),
                throwing(new SQLiteException("no such file")),
        };

        for (StorageAvailability.Opener opener : broken) {
            StorageAvailability.Result result =
                    StorageAvailability.probe(opener);
            assertFalse(result.isReady());
            assertNotEquals(StorageAvailability.READY_MESSAGE,
                    result.getMessage());
            assertTrue(result.getMessage(), result.getMessage()
                    .startsWith(StorageAvailability.UNAVAILABLE_PREFIX));
        }
    }

    // --- what the operator actually sees ----------------------------------

    @Test
    public void health_whenTheProbeFails_theStateIsInactive() {
        StorageAvailability.Result result = StorageAvailability.probe(
                throwing(new SQLiteException("unable to open database file")));

        PluginHealthManager health = startedManager();
        health.setStorageReady(result.isReady(), result.getMessage());

        assertEquals(PluginHealthState.INACTIVE, health.getState());
    }

    @Test
    public void health_whenTheProbeFails_theReasonReachesThePanel() {
        StorageAvailability.Result result = StorageAvailability.probe(
                throwing(new SQLiteException("unable to open database file")));

        PluginHealthManager health = startedManager();
        health.setStorageReady(result.isReady(), result.getMessage());

        assertTrue(health.getStorageMessage(),
                health.getStorageMessage().contains("unable to open"));
        assertTrue(health.getSummary(),
                health.getSummary().contains("unable to open"));
    }

    @Test
    public void health_whenStorageIsBroken_inactiveOutranksAFreshFix() {
        // A fix arriving does not make the plugin healthy: nothing is being
        // written, so ACTIVE would be a lie even with GPS working perfectly.
        PluginHealthManager health = startedManager();
        health.setStorageReady(false, "Storage unavailable: disk full");
        health.recordLocationSuccess(System.currentTimeMillis(), 5.0);

        assertEquals(PluginHealthState.INACTIVE, health.getState());
    }

    @Test
    public void health_whenTheProbeSucceeds_theStateCanReachActive() {
        StorageAvailability.Result result = StorageAvailability.probe(dbHelper);

        PluginHealthManager health = startedManager();
        health.setStorageReady(result.isReady(), result.getMessage());
        health.recordLocationSuccess(System.currentTimeMillis(), 5.0);

        assertEquals(PluginHealthState.ACTIVE, health.getState());
    }

    private PluginHealthManager startedManager() {
        PluginHealthManager health = new PluginHealthManager();
        health.start();
        health.setIdentityResolved(true, "Identity: RESCUE-1");
        health.setTrackingActive(true);
        return health;
    }
}
