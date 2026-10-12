package com.atakmap.android.plugintemplate.grid;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** The lead's chosen direction survives a restart with no line running. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, manifest = Config.NONE)
public class SearchDirectionStoreTest {

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("sartak_search_direction",
                Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void chosenDirectionIsBackAfterARestart() {
        new SearchDirectionStore(context).save("op-1", "TEAM-a",
                SearchLineDirection.EAST);

        assertEquals(SearchLineDirection.EAST, new SearchDirectionStore(
                context).load("op-1", "TEAM-a"));
    }

    @Test
    public void directionIsKeptPerOperationAndTeam() {
        SearchDirectionStore store = new SearchDirectionStore(context);
        store.save("op-1", "TEAM-a", SearchLineDirection.EAST);
        store.save("op-1", "TEAM-b", SearchLineDirection.SOUTH);

        assertEquals(SearchLineDirection.EAST, store.load("op-1", "TEAM-a"));
        assertEquals(SearchLineDirection.SOUTH, store.load("op-1", "TEAM-b"));
        assertNull(store.load("op-2", "TEAM-a"));
    }

    @Test
    public void endingALineDoesNotForgetTheDirection() {
        // The line's own store clears everything when a line ends; the
        // chosen direction lives elsewhere.
        SearchDirectionStore store = new SearchDirectionStore(context);
        store.save("op-1", "TEAM-a", SearchLineDirection.WEST);
        new SearchLineStateStore(context).clear();

        assertEquals(SearchLineDirection.WEST, store.load("op-1", "TEAM-a"));
    }

    @Test
    public void nothingSavedWithoutAnOperationOrTeam() {
        SearchDirectionStore store = new SearchDirectionStore(context);
        store.save("", "TEAM-a", SearchLineDirection.EAST);
        store.save("op-1", null, SearchLineDirection.EAST);

        assertNull(store.load("", "TEAM-a"));
        assertNull(store.load("op-1", null));
        assertNull(store.load("op-1", "TEAM-a"));
    }
}
