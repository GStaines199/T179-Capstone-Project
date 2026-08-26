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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

    // -------------------------------------------------------------------------
    // Recording / visibility toggles
    // -------------------------------------------------------------------------

    @Test
    public void toggleRecording_flipsRecordingFlag() {
        assertTrue(manager.isRecording());

        assertFalse(manager.toggleRecording());
        assertFalse(manager.isRecording());

        assertTrue(manager.toggleRecording());
        assertTrue(manager.isRecording());
    }

    @Test
    public void toggleVisible_flipsVisibilityFlag() {
        assertTrue(manager.isVisible());

        assertFalse(manager.toggleVisible());
        assertFalse(manager.isVisible());

        assertTrue(manager.toggleVisible());
        assertTrue(manager.isVisible());
    }

    // -------------------------------------------------------------------------
    // Status summaries
    // -------------------------------------------------------------------------

    @Test
    public void getStatusSummary_whenNoSession_returnsWaitingMessage() {
        assertEquals("Waiting for first GPS fix", manager.getStatusSummary());
    }

    @Test
    public void getStatusSummary_whenRecording_returnsRecordingMessage() {
        manager.startOrResume("uid1", "Alpha");

        assertEquals("Recording - local track active",
                manager.getStatusSummary());
    }

    @Test
    public void getStatusSummary_whenPaused_returnsPausedMessage() {
        manager.startOrResume("uid1", "Alpha");
        manager.toggleRecording();

        assertEquals("Paused - local track retained",
                manager.getStatusSummary());
    }

    @Test
    public void getDetailsSummary_whenNoSession_returnsPendingDetails() {
        String summary = manager.getDetailsSummary();

        assertTrue(summary.contains("Distance: 0.00 km"));
        assertTrue(summary.contains("Duration: 0 min 00 sec"));
        assertTrue(summary.contains("Track points: 0"));
        assertTrue(summary.contains("Visibility: Shown on map"));
        assertTrue(summary.contains("Session: pending"));
    }

    @Test
    public void getDetailsSummary_withRecordedPoints_reportsCountAndVisibility() {
        manager.startOrResume("uid1", "Alpha");
        manager.recordLocation("uid1", "Alpha", -27.4705, 153.0260,
                42.0, 7.5, 90.0, 1.2, 1000L);

        String summary = manager.getDetailsSummary();

        assertTrue(summary.contains("Track points: 1"));
        assertTrue(summary.contains("Session: track-uid1-"));
        assertTrue(summary.contains("Visibility: Shown on map"));
    }

    @Test
    public void getDetailsSummary_whenHidden_reportsHiddenVisibility() {
        manager.startOrResume("uid1", "Alpha");
        manager.toggleVisible();

        assertTrue(manager.getDetailsSummary()
                .contains("Visibility: Hidden from map"));
    }

    // -------------------------------------------------------------------------
    // Session lifecycle
    // -------------------------------------------------------------------------

    @Test
    public void stopSession_closesActiveSession() {
        manager.startOrResume("uid1", "Alpha");
        assertNotNull(manager.getActiveSessionId());

        manager.stopSession();

        assertNull(manager.getActiveSessionId());
    }

    @Test
    public void clearCurrentTrack_withNoActiveSession_noException() {
        manager.clearCurrentTrack();

        assertNull(manager.getActiveSessionId());
    }

    // -------------------------------------------------------------------------
    // Haversine distance
    // -------------------------------------------------------------------------

    @Test
    public void haversineDistance_samePoint_returnsZero() {
        assertEquals(0.0,
                SearchTrackManager.haversineDistance(-27.47, 153.02,
                        -27.47, 153.02),
                0.0001);
    }

    @Test
    public void haversineDistance_oneDegreeLatitude_isAbout111km() {
        double distance = SearchTrackManager.haversineDistance(0.0, 0.0,
                1.0, 0.0);

        // 1 degree of latitude ≈ 111.19 km at the equator.
        assertEquals(111_190.0, distance, 2_000.0);
    }

    @Test
    public void haversineDistance_antipodal_isHalfCircumference() {
        double distance = SearchTrackManager.haversineDistance(0.0, 0.0,
                0.0, 180.0);

        // Antipodal points are half the Earth's circumference apart, π·R.
        assertEquals(Math.PI * 6_371_000.0, distance, 50.0);
    }
}
