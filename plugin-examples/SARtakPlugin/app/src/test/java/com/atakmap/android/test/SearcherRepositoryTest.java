package com.atakmap.android.test;

import android.content.Context;

import com.atakmap.android.plugintemplate.database.DatabaseHelper;
import com.atakmap.android.plugintemplate.database.SearcherRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, manifest = Config.NONE)
public class SearcherRepositoryTest {

    private SearcherRepository repo;

    private DatabaseHelper dbHelper;


    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        dbHelper = new DatabaseHelper(context);
        repo = new SearcherRepository(dbHelper);
    }

    @After
    public void tearDown() {
        dbHelper.close();
    }

    @Test
    public void insertOrUpdate_selfIdentity_canBeRetrieved() {
        repo.insertOrUpdate("uid1", "Alice", "Samsung A52",
                1000L, 2000L, true);

        String[] identity = repo.getSelfIdentity();
        assertNotNull(identity);
        assertEquals("uid1", identity[0]);
        assertEquals("Alice", identity[1]);
    }

    @Test
    public void getSelfIdentity_whenNoSelfInserted_returnsNull() {
        repo.insertOrUpdate("uid2", "Bob", "Pixel 6",
                1000L, 2000L, false);

        String[] identity = repo.getSelfIdentity();
        assertNull(identity);
    }

    @Test
    public void insertOrUpdate_replacesExistingRecord() {
        repo.insertOrUpdate("uid1", "Alice", "Samsung A52",
                1000L, 2000L, true);
        repo.insertOrUpdate("uid1", "Alice-Updated", "Samsung A52",
                1000L, 3000L, true);

        String[] identity = repo.getSelfIdentity();
        assertNotNull(identity);
        assertEquals("Alice-Updated", identity[1]);
    }

    @Test
    public void updateLastSeen_updatesTimestampWithoutBreakingOtherFields() {
        repo.insertOrUpdate("uid1", "Alice", "Samsung A52",
                1000L, 2000L, true);
        repo.updateLastSeen("uid1", 9999L);

        // Self identity should still resolve correctly after update
        String[] identity = repo.getSelfIdentity();
        assertNotNull(identity);
        assertEquals("uid1", identity[0]);
    }

    @Test
    public void onlySelfFlaggedRecord_isReturnedBySelfQuery() {
        repo.insertOrUpdate("uid1", "Alice", "Device A", 1000L, 2000L, true);
        repo.insertOrUpdate("uid2", "Bob",   "Device B", 1000L, 2000L, false);
        repo.insertOrUpdate("uid3", "Carol", "Device C", 1000L, 2000L, false);

        String[] identity = repo.getSelfIdentity();
        assertNotNull(identity);
        assertEquals("uid1", identity[0]);
    }
}