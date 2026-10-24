package com.atakmap.android.plugintemplate.runtime;

import android.content.Context;

import com.atakmap.android.plugintemplate.plugin.BuildConfig;

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

/**
 * Saving a profile doesn't take over the selection (finding T1-6), and
 * joining an operation doesn't rename the user's matching profile
 * (finding T1-4). All values here are dummies.
 */
@RunWith(RobolectricTestRunner.class)
public class DittoCredentialStoreTest {

    private DittoCredentialStore store;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("sartak_ditto_credentials",
                Context.MODE_PRIVATE).edit().clear().commit();
        store = new DittoCredentialStore(context);
    }

    private static DittoCredentialProfile profile(String label, String db) {
        return DittoCredentialProfile.create(label,
                DittoCredentialProfile.CONNECTION_SDK, db,
                "https://auth.example.test", "token-" + db);
    }

    @Test
    public void freshInstallHasNoPresetAccount() {
        assertTrue(store.getProfiles().isEmpty());
        assertNull(store.getSelectedProfile());
        assertEquals("", store.getSelectedProfileId());
    }

    @Test
    public void buildConfigDoesNotContainDittoCredentials() {
        for (java.lang.reflect.Field field : BuildConfig.class.getDeclaredFields())
            assertFalse("Bundled credential field: " + field.getName(),
                    field.getName().startsWith("DITTO_"));
    }

    @Test
    public void userConfiguredProfileSurvivesStoreRecreation() {
        DittoCredentialProfile saved = profile("Organisation", "db-user");
        store.saveProfile(saved);

        DittoCredentialStore reopened = new DittoCredentialStore(
                RuntimeEnvironment.getApplication());

        assertEquals(saved.getId(), reopened.getSelectedProfileId());
        assertEquals(saved.getDevelopmentToken(),
                reopened.getSelectedProfile().getDevelopmentToken());
    }

    @Test
    public void theFirstSavedProfileIsSelected() {
        DittoCredentialProfile first = profile("First", "db-1");
        store.saveProfile(first);

        assertEquals(first.getId(), store.getSelectedProfileId());
    }

    @Test
    public void addingAnotherProfileKeepsTheSelection() {
        DittoCredentialProfile first = profile("First", "db-1");
        store.saveProfile(first);
        store.saveProfile(profile("Dummy", "db-2"));

        assertEquals(first.getId(), store.getSelectedProfileId());
    }

    @Test
    public void editingAnotherProfileKeepsTheSelection() {
        DittoCredentialProfile first = profile("First", "db-1");
        DittoCredentialProfile second = profile("Second", "db-2");
        store.saveProfile(first);
        store.saveProfile(second);
        store.saveProfile(second.updated("Second renamed", "db-2",
                "https://auth.example.test", "token-new"));

        assertEquals(first.getId(), store.getSelectedProfileId());
    }

    @Test
    public void removingAnotherProfileKeepsTheSelection() {
        DittoCredentialProfile first = profile("First", "db-1");
        DittoCredentialProfile second = profile("Second", "db-2");
        DittoCredentialProfile third = profile("Third", "db-3");
        store.saveProfile(first);
        store.saveProfile(second);
        store.saveProfile(third);
        store.selectProfile(third.getId());

        store.removeProfile(first.getId());

        assertEquals(third.getId(), store.getSelectedProfileId());
    }

    @Test
    public void removingTheSelectedProfileSelectsAnother() {
        DittoCredentialProfile first = profile("First", "db-1");
        DittoCredentialProfile second = profile("Second", "db-2");
        store.saveProfile(first);
        store.saveProfile(second);

        store.removeProfile(first.getId());

        assertEquals(second.getId(), store.getSelectedProfileId());
    }

    @Test
    public void joiningWithNoMatchingProfileCreatesOne() {
        assertEquals(DittoCredentialStore.ImportResult.CREATED,
                store.importFromOperation("Regression-B", "db-1",
                        "https://auth.example.test", "token-db-1"));

        List<DittoCredentialProfile> profiles = store.getProfiles();
        assertEquals(1, profiles.size());
        assertEquals("Regression-B", profiles.get(0).getRawLabel());
    }

    @Test
    public void joiningKeepsTheUsersLabelOnAMatchingProfile() {
        store.saveProfile(profile("My team database", "db-1"));

        assertEquals(DittoCredentialStore.ImportResult.UNCHANGED,
                store.importFromOperation("Regression-B", "db-1",
                        "https://auth.example.test", "token-db-1"));
        assertEquals("My team database",
                store.getProfiles().get(0).getRawLabel());
    }

    @Test
    public void aDifferentTokenUpdatesOnlyTheToken() {
        DittoCredentialProfile mine = profile("My team database", "db-1");
        store.saveProfile(mine);

        assertEquals(DittoCredentialStore.ImportResult.TOKEN_UPDATED,
                store.importFromOperation("Regression-B", "db-1",
                        "https://auth.example.test", "token-rotated"));

        List<DittoCredentialProfile> profiles = store.getProfiles();
        assertEquals(1, profiles.size());
        assertEquals(mine.getId(), profiles.get(0).getId());
        assertEquals("My team database", profiles.get(0).getRawLabel());
        assertEquals("token-rotated", profiles.get(0).getDevelopmentToken());
    }

    @Test
    public void joiningDoesNotChangeTheSelection() {
        DittoCredentialProfile mine = profile("Mine", "db-1");
        store.saveProfile(mine);

        store.importFromOperation("Other op", "db-2",
                "https://auth.example.test", "token-db-2");

        assertEquals(mine.getId(), store.getSelectedProfileId());
    }
}
