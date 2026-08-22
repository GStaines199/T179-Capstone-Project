package com.atakmap.android.plugintemplate.runtime;

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

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins the rule the plugin is built around: it never writes a position it was
 * not given. Every assertion is made against the real track database, so a
 * fabricated, carried-over or interpolated point would show up as a row.
 *
 * <p>Drives {@link LocationCaptureManager#applyCapture} — the ATAK-free half of
 * a capture cycle. {@code mapView} and {@code identityManager} are null because
 * that path never touches them; the ATAK half is covered on-device.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, manifest = Config.NONE)
public class NoFabricatedPositionTest {

    private static final String UID = "uid-1";
    private static final String CALLSIGN = "RESCUE-1";

    private DatabaseHelper dbHelper;
    private SearchTrackManager trackManager;
    private PluginHealthManager healthManager;
    private LocationCaptureManager captureManager;
    private int listenerCalls;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        dbHelper = new DatabaseHelper(context);
        trackManager = new SearchTrackManager(
                new TrackSessionRepository(dbHelper),
                new LocationRepository(dbHelper));
        healthManager = new PluginHealthManager();
        healthManager.start();
        healthManager.setStorageReady(true, "Local storage ready");
        // A fully initialised plugin, as after SARTakMapController's runtime
        // setup. Without this the tracking-off rule would mask GPS_LOST.
        healthManager.setTrackingActive(true);
        captureManager = new LocationCaptureManager(null, null, trackManager,
                healthManager, new LocationCaptureManager.Listener() {
                    @Override
                    public void onLocationCaptured() {
                        listenerCalls++;
                    }
                });
        trackManager.startOrResume(UID, CALLSIGN);
    }

    @After
    public void tearDown() {
        dbHelper.close();
    }

    // --- nothing is written without a fix ---------------------------------

    @Test
    public void anUnavailableFix_writesNoPoint() {
        capture(LocationCaptureManager.Fix.unavailable("No GPS Signal"));

        assertEquals(0, trackManager.getTrackPoints().size());
    }

    @Test
    public void anUnavailableFix_reportsGpsLostAndKeepsTheReason() {
        capture(LocationCaptureManager.Fix.unavailable(
                "GPS stale; ATAK fix is 45 seconds old"));

        assertEquals(PluginHealthState.GPS_LOST, healthManager.getState());
        assertEquals("GPS stale; ATAK fix is 45 seconds old",
                healthManager.getLocationMessage());
    }

    @Test
    public void aMissingFix_writesNoPoint() {
        capture(null);

        assertEquals(0, trackManager.getTrackPoints().size());
        assertEquals(PluginHealthState.GPS_LOST, healthManager.getState());
    }

    @Test
    public void anUnresolvedIdentity_writesNoPointEvenWithAGoodFix() {
        captureManager.applyCapture(false, "Identity unavailable", UID,
                CALLSIGN, fixAt(-27.4698, 153.0251, 1000L));

        assertEquals(0, trackManager.getTrackPoints().size());
        assertEquals(PluginHealthState.DEGRADED, healthManager.getState());
    }

    // --- a real fix is written exactly as given ---------------------------

    @Test
    public void anAvailableFix_writesThePointItWasGiven() {
        capture(fixAt(-27.4698, 153.0251, 1000L));

        List<double[]> points = trackManager.getTrackPoints();
        assertEquals(1, points.size());
        assertEquals(-27.4698, points.get(0)[0], 0.000001);
        assertEquals(153.0251, points.get(0)[1], 0.000001);
        assertEquals(1000L, (long) points.get(0)[2]);
    }

    // --- losing signal never fills in a position --------------------------

    @Test
    public void losingSignalAfterAFix_addsNoFurtherPoints() {
        capture(fixAt(-27.4698, 153.0251, 1000L));
        assertEquals(1, trackManager.getTrackPoints().size());

        capture(LocationCaptureManager.Fix.unavailable("No GPS Signal"));
        capture(LocationCaptureManager.Fix.unavailable("No GPS Signal"));
        capture(LocationCaptureManager.Fix.unavailable("No GPS Signal"));

        assertEquals(1, trackManager.getTrackPoints().size());
    }

    @Test
    public void losingSignalAfterAFix_leavesTheStoredPointUntouched() {
        capture(fixAt(-27.4698, 153.0251, 1000L));

        capture(LocationCaptureManager.Fix.unavailable("No GPS Signal"));

        List<double[]> points = trackManager.getTrackPoints();
        assertEquals(1, points.size());
        assertEquals(-27.4698, points.get(0)[0], 0.000001);
        assertEquals(153.0251, points.get(0)[1], 0.000001);
    }

    @Test
    public void aGapInSignal_isNotInterpolatedAcrossOnRecovery() {
        // Two real fixes either side of a two-cycle outage. An implementation
        // that filled the gap in would leave more than the two points it was
        // actually given.
        capture(fixAt(-27.4698, 153.0251, 1000L));
        capture(LocationCaptureManager.Fix.unavailable("No GPS Signal"));
        capture(LocationCaptureManager.Fix.unavailable("No GPS Signal"));
        capture(fixAt(-27.4800, 153.0400, 40000L));

        List<double[]> points = trackManager.getTrackPoints();
        assertEquals(2, points.size());
        assertEquals(-27.4698, points.get(0)[0], 0.000001);
        assertEquals(-27.4800, points.get(1)[0], 0.000001);
    }

    @Test
    public void aPausedTrack_writesNoPointEvenWithAGoodFix() {
        trackManager.toggleRecording();
        assertTrue(!trackManager.isRecording());

        capture(fixAt(-27.4698, 153.0251, 1000L));

        assertEquals(0, trackManager.getTrackPoints().size());
    }

    // --- the operator is always told ---------------------------------------

    @Test
    public void everyOutcome_notifiesTheListenerSoTheUiCannotFreezeOnStaleData() {
        capture(fixAt(-27.4698, 153.0251, 1000L));
        capture(LocationCaptureManager.Fix.unavailable("No GPS Signal"));
        captureManager.applyCapture(false, "Identity unavailable", UID,
                CALLSIGN, null);

        assertEquals(3, listenerCalls);
    }

    // --- nothing fabricated is broadcast to the team ----------------------

    @Test
    public void anUnavailableSnapshot_isNotPublishableAsAPosition() {
        assertTrue(!CotPublishPoint.hasPublishablePosition(
                AtakLocationStatus.Snapshot.unavailable("No GPS Signal")));
    }

    @Test
    public void aMissingSnapshot_isNotPublishableAsAPosition() {
        assertTrue(!CotPublishPoint.hasPublishablePosition(null));
    }

    // That an unpublishable snapshot yields CotPoint.ZERO cannot be
    // asserted here: CotPoint fails JVM verification off-device, under
    // Robolectric too. That half is verified on the emulator instead.

    private void capture(LocationCaptureManager.Fix fix) {
        captureManager.applyCapture(true, "Identity: " + CALLSIGN, UID,
                CALLSIGN, fix);
    }

    private LocationCaptureManager.Fix fixAt(double latitude, double longitude,
            long timestamp) {
        return LocationCaptureManager.Fix.available(latitude, longitude, 42.0,
                5.0, 90.0, 1.2, timestamp, "GPS");
    }
}
