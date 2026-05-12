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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, manifest = Config.NONE)
public class SearchTrackManagerRuntimeTest {

    private DatabaseHelper dbHelper;
    private SearchTrackManager manager;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        dbHelper = new DatabaseHelper(context);
        manager = new SearchTrackManager(new TrackSessionRepository(dbHelper),
                new LocationRepository(dbHelper));
    }

    @After
    public void tearDown() {
        dbHelper.close();
    }

    @Test
    public void startOrResume_createsActiveSession() {
        manager.startOrResume("uid1", "Alpha");

        assertNotNull(manager.getActiveSessionId());
    }

    @Test
    public void recordLocation_preservesRecordedPoint() {
        manager.startOrResume("uid1", "Alpha");
        manager.recordLocation("uid1", "Alpha", -27.4705, 153.0260,
                42.0, 7.5, 90.0, 1.2, 1000L);

        assertEquals(1, manager.getTrackPoints().size());
        assertEquals(7.5, manager.getTrackPoints().get(0)[3], 0.001);
    }
}
