package com.atakmap.android.plugintemplate.runtime;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.atakmap.android.plugintemplate.database.DatabaseHelper;
import com.atakmap.android.plugintemplate.database.SearcherRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Identity resolution survives a database that does not work.
 *
 * <p>Companion to {@code StorageHealthTest}, which covers the startup probe.
 * That probe holds the plugin in INACTIVE when storage fails, but the state is
 * only worth reporting if the plugin stays up long enough to show it. Identity
 * is the path that decides: who this device is comes from ATAK, yet resolving
 * it also stores a self row, and the CoT workflows and capture loop ask for the
 * identity every few seconds on ATAK's own thread. An unguarded write there
 * turned a storage fault into a crash, which is strictly worse than the
 * fabricated-health bug it was meant to replace.
 *
 * <p>These tests stage the fault against real SQLite rather than a stub, so
 * they exercise the exception SQLite genuinely throws.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, manifest = Config.NONE)
public class IdentityStorageFailureTest {

    private static final String UID = "uid-rescue-1";
    private static final String CALLSIGN = "RESCUE-1";
    private static final String MODEL = "Pixel 6";
    private static final long NOW = 1_700_000_000_000L;

    private DatabaseHelper dbHelper;
    private SearcherRepository repository;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        dbHelper = new DatabaseHelper(context);
        repository = new SearcherRepository(dbHelper);
    }

    @After
    public void tearDown() {
        dbHelper.close();
    }

    private static IdentityManager.Identity identity() {
        return IdentityManager.resolve(UID, CALLSIGN, null, null, null);
    }

    @Test
    public void remember_whenStorageWorks_storesTheSelfRow() {
        boolean stored = IdentityManager.remember(repository, identity(),
                MODEL, NOW);

        assertTrue(stored);
        String[] self = repository.getSelfIdentity();
        assertEquals(UID, self[0]);
        assertEquals(CALLSIGN, self[1]);
    }

    @Test
    public void remember_whenTheIdentityTableIsMissing_doesNotThrow() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.execSQL("DROP TABLE searcher_identity");

        // The assertion is that this call returns at all. Before the guard it
        // threw SQLiteException straight out onto ATAK's thread.
        boolean stored = IdentityManager.remember(repository, identity(),
                MODEL, NOW);

        assertFalse(stored);
    }

    @Test
    public void remember_whenTheUidChanges_onlyTheNewRowStaysFlaggedSelf() {
        // ATAK can gain or lose an identity mid-session, changing the UID.
        // clearSelfFlagExcept exists for that and is covered at repository
        // level, but nothing proved IdentityManager actually calls it --
        // deleting the call passed the whole suite. Two rows flagged self makes
        // getSelfIdentity return whichever the ORDER BY happens to reach.
        IdentityManager.remember(repository,
                IdentityManager.resolve("uid-old", "OLD-1", null, null, null),
                MODEL, NOW);
        IdentityManager.remember(repository, identity(), MODEL, NOW + 1000L);

        assertEquals(1, countSelfFlagged());
        assertEquals(UID, repository.getSelfIdentity()[0]);
    }

    private int countSelfFlagged() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        android.database.Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM searcher_identity WHERE is_self = 1",
                null);
        try {
            cursor.moveToFirst();
            return cursor.getInt(0);
        } finally {
            cursor.close();
        }
    }

    @Test
    public void remember_whenTheIdentityTableHasTheWrongShape_doesNotThrow() {
        // What a half-applied migration leaves behind: the table is there, so
        // nothing looks missing, but every column the write names is gone.
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.execSQL("DROP TABLE searcher_identity");
        db.execSQL("CREATE TABLE searcher_identity (unrelated TEXT)");

        assertFalse(IdentityManager.remember(repository, identity(), MODEL,
                NOW));
    }

    @Test
    public void remember_whenStorageFails_theIdentityItselfIsStillUsable() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.execSQL("DROP TABLE searcher_identity");

        IdentityManager.Identity resolved = identity();
        IdentityManager.remember(repository, resolved, MODEL, NOW);

        // Who we are comes from ATAK, so a storage fault must not downgrade it.
        // If it did, health would report DEGRADED for an unresolved identity
        // and mask the real cause, which is INACTIVE for storage.
        assertTrue(resolved.isResolved());
        assertEquals(UID, resolved.getUid());
        assertEquals(CALLSIGN, resolved.getCallsign());
    }
}
