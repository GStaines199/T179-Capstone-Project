package com.atakmap.android.plugintemplate.runtime;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class OperationStateStoreTest {

    private OperationStateStore store;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("sartak_operation_state",
                Context.MODE_PRIVATE).edit().clear().commit();
        store = new OperationStateStore(context);
    }

    @Test
    public void saveKeepsPreviouslyJoinedOperationsAvailableForSwitching() {
        store.save(profile("OP-ONE", "First", 100L));
        store.save(profile("OP-TWO", "Second", 200L));

        List<OperationProfile> saved = store.loadSavedProfiles();

        assertEquals(2, saved.size());
        assertEquals("OP-TWO", saved.get(0).getOperationId());
        assertEquals("OP-ONE", saved.get(1).getOperationId());
        assertEquals("OP-TWO", store.load().getOperationId());
    }

    @Test
    public void clearingActiveOperationDoesNotForgetSavedOperations() {
        store.save(profile("OP-ONE", "First", 100L));

        store.clear();

        assertNull(store.load());
        assertEquals(1, store.loadSavedProfiles().size());
        assertEquals("OP-ONE", store.loadSavedProfiles().get(0)
                .getOperationId());
    }

    @Test
    public void savingAnUpdatedProfileReplacesItsCatalogueEntry() {
        store.save(profile("OP-ONE", "Old name", 100L));
        store.save(profile("OP-ONE", "Updated name", 100L));

        assertEquals(1, store.loadSavedProfiles().size());
        assertEquals("Updated name", store.loadSavedProfiles().get(0)
                .getOperationName());
    }

    @Test
    public void removingAnInactiveOperationDropsItFromTheList() {
        store.save(profile("OP-ONE", "First", 100L));
        store.save(profile("OP-TWO", "Second", 200L));

        assertTrue(store.removeSavedProfile("OP-ONE"));

        List<OperationProfile> saved = store.loadSavedProfiles();
        assertEquals(1, saved.size());
        assertEquals("OP-TWO", saved.get(0).getOperationId());
        // Survives a restart: a fresh store reads the same preferences.
        assertEquals(1, new OperationStateStore(RuntimeEnvironment
                .getApplication()).loadSavedProfiles().size());
    }

    @Test
    public void theActiveOperationCannotBeRemoved() {
        store.save(profile("OP-ONE", "First", 100L));

        assertFalse(store.removeSavedProfile("OP-ONE"));
        assertEquals(1, store.loadSavedProfiles().size());
        assertEquals("OP-ONE", store.load().getOperationId());
    }

    @Test
    public void aLeftOperationCanBeRemoved() {
        store.save(profile("OP-ONE", "First", 100L));
        store.clear();

        assertTrue(store.removeSavedProfile("OP-ONE"));
        assertTrue(store.loadSavedProfiles().isEmpty());
    }

    @Test
    public void removingAnUnknownOperationChangesNothing() {
        store.save(profile("OP-ONE", "First", 100L));

        assertFalse(store.removeSavedProfile("OP-NOPE"));
        assertFalse(store.removeSavedProfile(""));
        assertFalse(store.removeSavedProfile(null));
        assertEquals(1, store.loadSavedProfiles().size());
    }

    private OperationProfile profile(String id, String name, long createdAt) {
        return new OperationProfile(id, name,
                OperationProfile.SYNC_DITTO_DEVELOPMENT, "database",
                "https://example.invalid", "token", "HQ-UID", "HQ",
                createdAt, createdAt + 1000L);
    }
}
