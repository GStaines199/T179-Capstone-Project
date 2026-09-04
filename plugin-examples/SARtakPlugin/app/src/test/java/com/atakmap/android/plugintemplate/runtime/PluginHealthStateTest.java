package com.atakmap.android.plugintemplate.runtime;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers the health state machine and the staleness boundary that separates
 * ACTIVE from GPS_LOST. Lives in the runtime package so it can drive the
 * package-private {@link PluginHealthManager.Clock} seam; no ATAK or Android
 * types are involved, so these run on a plain JVM.
 */
public class PluginHealthStateTest {

    /** Clock the test moves by hand, so the stale window is exact. */
    private static class TestClock implements PluginHealthManager.Clock {
        private long now = 1_700_000_000_000L;

        @Override
        public long now() {
            return now;
        }

        void advance(long millis) {
            now += millis;
        }
    }

    private final TestClock clock = new TestClock();

    // --- INACTIVE ---------------------------------------------------------

    @Test
    public void getState_whenNeverStarted_isInactive() {
        PluginHealthManager manager = new PluginHealthManager(clock);

        assertEquals(PluginHealthState.INACTIVE, manager.getState());
    }

    @Test
    public void getState_whenStorageNotReady_isInactive() {
        PluginHealthManager manager = readyManager();
        manager.setStorageReady(false, "Storage unavailable");

        assertEquals(PluginHealthState.INACTIVE, manager.getState());
    }

    @Test
    public void getState_whenStoppedMidSession_fallsBackToInactive() {
        PluginHealthManager manager = readyManager();
        manager.recordLocationSuccess(clock.now(), 6.0);
        assertEquals(PluginHealthState.ACTIVE, manager.getState());

        manager.stop();

        assertEquals(PluginHealthState.INACTIVE, manager.getState());
    }

    // --- DEGRADED ---------------------------------------------------------

    @Test
    public void getState_whenIdentityUnresolved_isDegraded() {
        PluginHealthManager manager = readyManager();
        manager.recordLocationSuccess(clock.now(), 6.0);
        manager.setIdentityResolved(false, "Identity unavailable");

        assertEquals(PluginHealthState.DEGRADED, manager.getState());
    }

    @Test
    public void getState_whenTrackRecordingStopped_isDegraded() {
        PluginHealthManager manager = readyManager();
        manager.recordLocationSuccess(clock.now(), 6.0);
        manager.setTrackingActive(false);

        assertEquals(PluginHealthState.DEGRADED, manager.getState());
    }

    @Test
    public void getState_identityFailureOutranksMissingFix() {
        // The capture loop clears the last fix when identity fails, so a naive
        // ordering would report GPS_LOST for a problem the receiver did not
        // cause. Setup faults must win.
        PluginHealthManager manager = readyManager();
        manager.setIdentityResolved(false, "Identity unavailable");
        manager.recordLocationFailure("Identity unavailable; tracking degraded");

        assertEquals(PluginHealthState.DEGRADED, manager.getState());
    }

    // --- GPS_LOST ---------------------------------------------------------

    @Test
    public void getState_whenSetUpButNoFixYet_isGpsLost() {
        PluginHealthManager manager = readyManager();

        assertEquals(PluginHealthState.GPS_LOST, manager.getState());
    }

    @Test
    public void getState_whenLocationFailureRecorded_isGpsLost() {
        PluginHealthManager manager = readyManager();
        manager.recordLocationSuccess(clock.now(), 6.0);
        manager.recordLocationFailure("No GPS Signal");

        assertEquals(PluginHealthState.GPS_LOST, manager.getState());
    }

    @Test
    public void getState_whenFixIsExactlyAtTheStaleWindow_staysActive() {
        PluginHealthManager manager = readyManager();
        manager.recordLocationSuccess(clock.now(), 6.0);

        clock.advance(PluginHealthManager.LOCATION_STALE_MS);

        assertEquals(PluginHealthState.ACTIVE, manager.getState());
    }

    @Test
    public void getState_whenFixIsOneMillisecondPastTheWindow_isGpsLost() {
        PluginHealthManager manager = readyManager();
        manager.recordLocationSuccess(clock.now(), 6.0);

        clock.advance(PluginHealthManager.LOCATION_STALE_MS + 1L);

        assertEquals(PluginHealthState.GPS_LOST, manager.getState());
    }

    @Test
    public void getState_afterGpsReturns_recoversToActive() {
        PluginHealthManager manager = readyManager();
        manager.recordLocationSuccess(clock.now(), 6.0);
        clock.advance(PluginHealthManager.LOCATION_STALE_MS + 1L);
        assertEquals(PluginHealthState.GPS_LOST, manager.getState());

        manager.recordLocationSuccess(clock.now(), 4.0);

        assertEquals(PluginHealthState.ACTIVE, manager.getState());
    }

    // --- ACTIVE -----------------------------------------------------------

    @Test
    public void getState_whenFullySetUpWithAFreshFix_isActive() {
        PluginHealthManager manager = readyManager();
        manager.recordLocationSuccess(clock.now(), 6.0);

        assertEquals(PluginHealthState.ACTIVE, manager.getState());
    }

    // --- reported detail --------------------------------------------------

    @Test
    public void isLocationActive_goesFalseOnceTheFixGoesStale() {
        PluginHealthManager manager = readyManager();
        manager.recordLocationSuccess(clock.now(), 6.0);
        assertTrue(manager.isLocationActive());

        clock.advance(PluginHealthManager.LOCATION_STALE_MS + 1L);

        assertFalse(manager.isLocationActive());
    }

    @Test
    public void isLocationActive_whenStorageIsNotReady_isFalseDespiteAFreshFix() {
        PluginHealthManager manager = readyManager();
        manager.recordLocationSuccess(clock.now(), 6.0);
        assertTrue(manager.isLocationActive());

        manager.setStorageReady(false, "Storage unavailable: read-only");

        // The plugin is INACTIVE, so nothing is being logged. Reporting the GPS
        // as fine here would tell the operator the one thing they must not be
        // told: that the search is being recorded when it is not.
        assertFalse(manager.isLocationActive());
    }

    @Test
    public void isLocationActive_whenStopped_isFalseDespiteAFreshFix() {
        PluginHealthManager manager = readyManager();
        manager.recordLocationSuccess(clock.now(), 6.0);

        manager.stop();

        assertFalse(manager.isLocationActive());
    }

    @Test
    public void isLocationActive_whenRecordingIsPaused_staysTrue() {
        PluginHealthManager manager = readyManager();
        manager.recordLocationSuccess(clock.now(), 6.0);

        // A paused recording reads as DEGRADED, but the receiver is working and
        // the message beside the indicator still says so. This pins the
        // divergence from the audit's "isLocationActive should be
        // getState() == ACTIVE": that would paint the GPS field red while the
        // text next to it read "GPS active".
        manager.setTrackingActive(false);

        assertEquals(PluginHealthState.DEGRADED, manager.getState());
        assertTrue(manager.isLocationActive());
        assertTrue(manager.getLocationMessage().startsWith("GPS active"));
    }

    @Test
    public void isLocationActive_whenIdentityIsUnresolved_isFalse() {
        PluginHealthManager manager = readyManager();
        manager.recordLocationSuccess(clock.now(), 6.0);

        // The other half of DEGRADED. The capture loop clears the fix when it
        // cannot resolve who we are, so there is genuinely nothing to report.
        manager.setIdentityResolved(false, "Identity unavailable");
        manager.recordLocationFailure("Identity unavailable; tracking degraded");

        assertEquals(PluginHealthState.DEGRADED, manager.getState());
        assertFalse(manager.isLocationActive());
    }

    @Test
    public void isLocationActive_isNotDecidedByTheWordingOfTheMessage() {
        PluginHealthManager manager = readyManager();

        // A failure whose prose happens to open with the old magic prefix. The
        // indicator must follow the model, not the sentence: this used to be
        // decided by startsWith("GPS active") on exactly this string.
        manager.recordLocationFailure("GPS active earlier; no fix since 09:51");

        assertFalse(manager.isLocationActive());
    }

    @Test
    public void getSummary_namesTheStateInOperatorWording() {
        PluginHealthManager manager = readyManager();

        assertTrue(manager.getSummary().startsWith("SARtak GPS Lost"));
    }

    @Test
    public void getLocationMessage_keepsTheReasonTheFixWasRejected() {
        PluginHealthManager manager = readyManager();
        manager.recordLocationFailure("GPS stale; ATAK fix is 45 seconds old");

        assertEquals("GPS stale; ATAK fix is 45 seconds old",
                manager.getLocationMessage());
    }

    private PluginHealthManager readyManager() {
        PluginHealthManager manager = new PluginHealthManager(clock);
        manager.start();
        manager.setStorageReady(true, "Local storage ready");
        manager.setIdentityResolved(true, "Identity: RESCUE-1");
        manager.setTrackingActive(true);
        return manager;
    }
}
