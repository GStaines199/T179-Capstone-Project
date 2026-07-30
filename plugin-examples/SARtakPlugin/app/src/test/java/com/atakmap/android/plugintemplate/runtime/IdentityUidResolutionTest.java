package com.atakmap.android.plugintemplate.runtime;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the UID/callsign the plugin tracks under, as decided by
 * IdentityManager#resolve: ATAK's device identity first, then the ATAK self
 * marker, then the device fallback (the Android ID and device model).
 * <p>
 * IdentityManager#resolveIdentity() itself is not covered - it reads MapView
 * and the searcher database, and ATAK SDK classes fail JVM bytecode
 * verification outside Android. The fallback values are supplied here through
 * the DeviceFallback seam.
 * <p>
 * Dependencies (build.gradle):
 *   testImplementation 'junit:junit:4.13.2'
 */
public class IdentityUidResolutionTest {

    private static final String ATAK_UID = "ANDROID-1234567890abcdef";
    private static final String ATAK_CALLSIGN = "ALPHA";
    private static final String MARKER_UID = "MARKER-abcdef0123456789";
    private static final String MARKER_CALLSIGN = "BRAVO";
    private static final String DEVICE_ID = "9774d56d682e549c";
    private static final String DEVICE_MODEL = "Pixel 7";

    private final RecordingFallback fallback =
            new RecordingFallback(DEVICE_ID, DEVICE_MODEL);

    // -------------------------------------------------------------------------
    // ATAK identity
    // -------------------------------------------------------------------------

    @Test
    public void resolve_prefersAtakDeviceIdentity() {
        IdentityManager.Identity identity = IdentityManager.resolve(ATAK_UID,
                ATAK_CALLSIGN, MARKER_UID, MARKER_CALLSIGN, fallback);

        assertEquals(ATAK_UID, identity.getUid());
        assertEquals(ATAK_CALLSIGN, identity.getCallsign());
        assertTrue(identity.isAtakIdentity());
        assertTrue(identity.isResolved());
        assertEquals("Identity: " + ATAK_CALLSIGN, identity.getMessage());
    }

    @Test
    public void resolve_whenAtakIdentityPresent_neverTouchesDeviceFallback() {
        IdentityManager.resolve(ATAK_UID, ATAK_CALLSIGN, null, null, fallback);

        assertEquals(0, fallback.deviceIdCalls);
        assertEquals(0, fallback.deviceModelCalls);
    }

    // -------------------------------------------------------------------------
    // Self marker fallback
    // -------------------------------------------------------------------------

    @Test
    public void resolve_whenDeviceUidMissing_usesSelfMarkerUid() {
        IdentityManager.Identity identity = IdentityManager.resolve(null,
                ATAK_CALLSIGN, MARKER_UID, MARKER_CALLSIGN, fallback);

        assertEquals(MARKER_UID, identity.getUid());
        assertEquals(ATAK_CALLSIGN, identity.getCallsign());
        assertTrue(identity.isAtakIdentity());
    }

    @Test
    public void resolve_whenDeviceUidBlank_usesSelfMarkerUid() {
        IdentityManager.Identity identity = IdentityManager.resolve("   ",
                ATAK_CALLSIGN, MARKER_UID, MARKER_CALLSIGN, fallback);

        assertEquals(MARKER_UID, identity.getUid());
    }

    @Test
    public void resolve_whenDeviceCallsignMissing_usesSelfMarkerCallsign() {
        IdentityManager.Identity identity = IdentityManager.resolve(ATAK_UID,
                null, MARKER_UID, MARKER_CALLSIGN, fallback);

        assertEquals(ATAK_UID, identity.getUid());
        assertEquals(MARKER_CALLSIGN, identity.getCallsign());
        assertTrue(identity.isAtakIdentity());
        assertEquals("Identity: " + MARKER_CALLSIGN, identity.getMessage());
    }

    @Test
    public void resolve_whenDeviceCallsignBlank_usesSelfMarkerCallsign() {
        IdentityManager.Identity identity = IdentityManager.resolve(ATAK_UID,
                "\t ", MARKER_UID, MARKER_CALLSIGN, fallback);

        assertEquals(MARKER_CALLSIGN, identity.getCallsign());
    }

    @Test
    public void resolve_withOnlySelfMarkerValues_isStillAnAtakIdentity() {
        IdentityManager.Identity identity = IdentityManager.resolve(null, null,
                MARKER_UID, MARKER_CALLSIGN, fallback);

        assertEquals(MARKER_UID, identity.getUid());
        assertEquals(MARKER_CALLSIGN, identity.getCallsign());
        assertTrue(identity.isAtakIdentity());
        assertEquals(0, fallback.deviceIdCalls);
    }

    // -------------------------------------------------------------------------
    // Device fallback
    // -------------------------------------------------------------------------

    @Test
    public void resolve_withNoAtakIdentity_fallsBackToDeviceValues() {
        IdentityManager.Identity identity = IdentityManager.resolve(null, null,
                null, null, fallback);

        assertEquals(DEVICE_ID, identity.getUid());
        assertEquals(DEVICE_MODEL, identity.getCallsign());
        assertFalse(identity.isAtakIdentity());
        assertTrue(identity.isResolved());
        assertEquals("Using device identity fallback", identity.getMessage());
    }

    @Test
    public void resolve_withUidButNoCallsign_keepsUidAndFallsBackToModel() {
        IdentityManager.Identity identity = IdentityManager.resolve(ATAK_UID,
                null, null, null, fallback);

        assertEquals(ATAK_UID, identity.getUid());
        assertEquals(DEVICE_MODEL, identity.getCallsign());
        assertFalse(identity.isAtakIdentity());
        assertEquals("Using device identity fallback", identity.getMessage());
        assertEquals(0, fallback.deviceIdCalls);
        assertEquals(1, fallback.deviceModelCalls);
    }

    @Test
    public void resolve_withCallsignButNoUid_keepsCallsignAndFallsBackToId() {
        IdentityManager.Identity identity = IdentityManager.resolve(null,
                ATAK_CALLSIGN, null, null, fallback);

        assertEquals(DEVICE_ID, identity.getUid());
        assertEquals(ATAK_CALLSIGN, identity.getCallsign());
        assertFalse(identity.isAtakIdentity());
        assertEquals(1, fallback.deviceIdCalls);
        assertEquals(0, fallback.deviceModelCalls);
    }

    @Test
    public void resolve_whenDeviceIdUnavailable_isUnresolved() {
        IdentityManager.Identity identity = IdentityManager.resolve(null, null,
                null, null, new RecordingFallback(null, DEVICE_MODEL));

        assertNull(identity.getUid());
        assertFalse(identity.isResolved());
        assertFalse(identity.isAtakIdentity());
        assertEquals("Using device identity fallback", identity.getMessage());
    }

    @Test
    public void resolve_whenDeviceIdEmpty_isUnresolved() {
        IdentityManager.Identity identity = IdentityManager.resolve(null, null,
                null, null, new RecordingFallback("", DEVICE_MODEL));

        assertEquals("", identity.getUid());
        assertFalse(identity.isResolved());
    }

    @Test
    public void resolve_whenDeviceModelUnavailable_isUnresolved() {
        IdentityManager.Identity identity = IdentityManager.resolve(null, null,
                null, null, new RecordingFallback(DEVICE_ID, null));

        assertEquals(DEVICE_ID, identity.getUid());
        assertNull(identity.getCallsign());
        assertFalse(identity.isResolved());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Counts lookups so precedence can be asserted, not just the outcome. */
    private static class RecordingFallback
            implements IdentityManager.DeviceFallback {

        private final String deviceId;
        private final String deviceModel;
        int deviceIdCalls;
        int deviceModelCalls;

        RecordingFallback(String deviceId, String deviceModel) {
            this.deviceId = deviceId;
            this.deviceModel = deviceModel;
        }

        @Override
        public String getDeviceId() {
            deviceIdCalls++;
            return deviceId;
        }

        @Override
        public String getDeviceModel() {
            deviceModelCalls++;
            return deviceModel;
        }
    }
}
