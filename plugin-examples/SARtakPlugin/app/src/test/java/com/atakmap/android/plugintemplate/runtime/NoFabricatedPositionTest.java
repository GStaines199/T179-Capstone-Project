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
 * <p>Drives {@link LocationCaptureManager#captureWith} — the ATAK-free half of
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
        // Track logging is operation-scoped: startOrResume() does nothing
        // until an operation id is set.
        trackManager.setOperationId("op-1");
        trackManager.startOrResume(UID, CALLSIGN);
    }

    @After
    public void tearDown() {
        dbHelper.close();
    }

    // --- nothing is written without a fix ---------------------------------

    @Test
    public void anUnavailableFix_writesNoPoint() {
        capture(LocationCaptureManager.LocationFix.unavailable("No GPS Signal"));

        assertEquals(0, trackManager.getTrackPoints().size());
    }

    @Test
    public void anUnavailableFix_reportsGpsLostAndKeepsTheReason() {
        capture(LocationCaptureManager.LocationFix.unavailable(
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
        captureManager.captureWith(unresolvedIdentity(),
                fixAt(-27.4698, 153.0251, 1000L));

        assertEquals(0, trackManager.getTrackPoints().size());
        assertEquals(PluginHealthState.DEGRADED, healthManager.getState());
    }

    // --- the fused source never reaches the track -------------------------

    /**
     * The regression guard for the duplicate-writer bug. This path reads
     * ATAK's self marker, whose position has already been through ATAK's
     * location fusion, so a point written here would be a second,
     * differently-sourced writer inside a track required to hold raw device
     * fixes. It has been reintroduced once already, by a merge resolved in
     * favour of the branch that still had the write.
     */
    @Test
    public void anAvailableFix_writesNoPointBecauseItIsNotRawGnss() {
        capture(fixAt(-27.4698, 153.0251, 1000L));

        assertEquals(0, trackManager.getTrackPoints().size());
        assertEquals(PluginHealthState.ACTIVE, healthManager.getState());
    }

    // --- losing signal never fills in a position --------------------------

    /**
     * Nothing is written whether the signal is present or lost, so an outage
     * cannot leave a filled-in point behind either. Gap honesty on the raw
     * writer is structural rather than asserted here: it writes only when
     * Android delivers a fix, so no fix means no row. The sequence itself is
     * pinned in {@code RawGnssCaptureManagerTest}.
     */
    @Test
    public void aSignalOutage_leavesTheTrackEmptyThroughout() {
        capture(fixAt(-27.4698, 153.0251, 1000L));
        capture(LocationCaptureManager.LocationFix.unavailable("No GPS Signal"));
        capture(LocationCaptureManager.LocationFix.unavailable("No GPS Signal"));
        capture(fixAt(-27.4800, 153.0400, 40000L));

        assertEquals(0, trackManager.getTrackPoints().size());
        assertEquals(PluginHealthState.ACTIVE, healthManager.getState());
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
        capture(LocationCaptureManager.LocationFix.unavailable("No GPS Signal"));
        captureManager.captureWith(unresolvedIdentity(), null);

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

    private void capture(LocationCaptureManager.LocationFix fix) {
        captureManager.captureWith(resolvedIdentity(), fix);
    }

    private IdentityManager.Identity resolvedIdentity() {
        return new IdentityManager.Identity(UID, CALLSIGN,
                "Identity: " + CALLSIGN, true);
    }

    /**
     * Identity.isResolved() is derived from the uid and callsign, not from the
     * atakIdentity flag, so an unresolved identity has to actually be missing
     * them -- passing a uid with the flag false still reads as resolved.
     */
    private IdentityManager.Identity unresolvedIdentity() {
        return new IdentityManager.Identity(null, null,
                "Identity unavailable", false);
    }

    private LocationCaptureManager.LocationFix fixAt(double latitude,
            double longitude, long timestamp) {
        return LocationCaptureManager.LocationFix.available(latitude, longitude,
                42.0, 5.0, timestamp, "GPS", 90.0, 1.2);
    }
}
