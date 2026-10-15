package com.atakmap.android.plugintemplate.runtime;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** What survives an ATAK restart for findings T4-2, T4-5 and T4-7. */
@RunWith(RobolectricTestRunner.class)
public class SharedMarkerStateStoreTest {

    private Context context;
    private SharedMarkerStateStore store;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("sartak_shared_markers",
                Context.MODE_PRIVATE).edit().clear().commit();
        store = new SharedMarkerStateStore(context);
    }

    private static Set<String> set(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    @Test
    public void importsAreKeptPerOperationAcrossInstances() {
        assertTrue(store.addImported("op-a", "m1"));
        assertFalse(store.addImported("op-a", "m1"));
        store.addImported("op-b", "m2");

        SharedMarkerStateStore reloaded = new SharedMarkerStateStore(context);
        assertEquals(set("m1"), reloaded.getImported("op-a"));
        assertEquals(set("m2"), reloaded.getImported("op-b"));
        assertEquals(2, reloaded.getImportedByOperation().size());
    }

    @Test
    public void severalImportsAreAddedTogether() {
        store.addImported("op-a", "m1");
        store.addImported("op-a", Arrays.asList("m1", "m2"));

        assertEquals(set("m1", "m2"), store.getImported("op-a"));
    }

    @Test
    public void otherOperationsImportsAreForeignUnlessAlsoCurrent() {
        Map<String, Set<String>> imported = new HashMap<>();
        imported.put("op-a", set("m1", "shared"));
        imported.put("op-b", set("m2", "shared"));

        Map<String, Set<String>> foreign = SharedMarkerStateStore
                .foreignImports(imported, "op-b");

        assertEquals(Collections.singletonMap("op-a", set("m1")), foreign);
    }

    @Test
    public void leavingMakesEveryImportForeign() {
        Map<String, Set<String>> imported = new HashMap<>();
        imported.put("op-a", set("m1"));
        imported.put("op-b", set("m2"));

        assertEquals(imported,
                SharedMarkerStateStore.foreignImports(imported, ""));
    }

    @Test
    public void removedImportsAreNoLongerPresentButStillImported() {
        store.addImported("op-a", "m1");
        store.savePresent("op-a", set("m1", "own"));

        store.forgetPresent("op-a", Collections.singletonList("m1"));

        assertEquals(set("own"), store.getPresent("op-a"));
        assertEquals(set("m1"), store.getImported("op-a"));
    }

    @Test
    public void joinTimeIsRecordedOnce() {
        assertEquals(100L, store.getOrRecordJoinedAt("op-a", 100L));
        assertEquals(100L, store.getOrRecordJoinedAt("op-a", 900L));
        assertEquals(900L, store.getOrRecordJoinedAt("op-b", 900L));
    }

    @Test
    public void theExistingMarkersAnswerIsRememberedPerOperation() {
        assertEquals(SharedMapMarkerReconciler.ExistingMarkers.ASK,
                store.getExistingMarkers("op-a"));

        store.setExistingMarkers("op-a",
                SharedMapMarkerReconciler.ExistingMarkers.KEEP_PRIVATE);

        assertEquals(SharedMapMarkerReconciler.ExistingMarkers.KEEP_PRIVATE,
                new SharedMarkerStateStore(context).getExistingMarkers("op-a"));
        assertEquals(SharedMapMarkerReconciler.ExistingMarkers.ASK,
                store.getExistingMarkers("op-b"));
    }
}
