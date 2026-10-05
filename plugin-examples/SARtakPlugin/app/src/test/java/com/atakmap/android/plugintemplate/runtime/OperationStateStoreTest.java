package com.atakmap.android.plugintemplate.runtime;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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

    private OperationProfile profile(String id, String name, long createdAt) {
        return new OperationProfile(id, name,
                OperationProfile.SYNC_DITTO_DEVELOPMENT, "database",
                "https://example.invalid", "token", "HQ-UID", "HQ",
                createdAt, createdAt + 1000L);
    }
}
