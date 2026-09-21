package com.atakmap.android.test;

import android.location.Location;

import com.atakmap.android.plugintemplate.runtime.RawGnssCapture;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for RawGnssCapture#from(Location): the raw fields (lat/lon/alt/
 * bearing/speed/provider/time) and the API-26+ accuracy metadata
 * (vertical/bearing/speed accuracy) must be preserved unmodified from the
 * source Location, with unavailable accuracy extras left null rather than
 * defaulted to zero.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, manifest = Config.NONE)
public class RawGnssCaptureTest {

    @Test
    public void from_preservesCoreFieldsUnmodified() {
        Location location = new Location("gps");
        location.setLatitude(-27.4698);
        location.setLongitude(153.0251);
        location.setAltitude(42.0);
        location.setAccuracy(5.0f);
        location.setBearing(180.0f);
        location.setSpeed(2.5f);
        location.setTime(1700000000000L);

        RawGnssCapture capture = RawGnssCapture.from(location);

        assertEquals(-27.4698, capture.getLatitude(), 0.0001);
        assertEquals(153.0251, capture.getLongitude(), 0.0001);
        assertEquals(42.0, capture.getAltitude(), 0.0001);
        assertEquals(5.0f, capture.getAccuracyMeters(), 0.0001);
        assertEquals(180.0f, capture.getBearingDegrees(), 0.0001);
        assertEquals(2.5f, capture.getSpeedMps(), 0.0001);
        assertEquals("gps", capture.getProvider());
        assertEquals(1700000000000L, capture.getTimestamp());
    }

    @Test
    public void from_withAccuracyExtrasPresent_preservesThemUnmodified() {
        Location location = new Location("fused");
        location.setLatitude(0.0);
        location.setLongitude(0.0);
        location.setVerticalAccuracyMeters(3.2f);
        location.setBearingAccuracyDegrees(4.1f);
        location.setSpeedAccuracyMetersPerSecond(0.75f);

        RawGnssCapture capture = RawGnssCapture.from(location);

        assertEquals(3.2, capture.getVerticalAccuracyMeters(), 0.0001);
        assertEquals(4.1, capture.getBearingAccuracyDegrees(), 0.0001);
        assertEquals(0.75, capture.getSpeedAccuracyMps(), 0.0001);
    }

    @Test
    public void from_withAccuracyExtrasAbsent_leavesThemNull() {
        Location location = new Location("network");
        location.setLatitude(0.0);
        location.setLongitude(0.0);
        // hasVerticalAccuracy()/hasBearingAccuracy()/hasSpeedAccuracy() are all
        // false when the extras are never set on this fix.

        RawGnssCapture capture = RawGnssCapture.from(location);

        assertNull(capture.getVerticalAccuracyMeters());
        assertNull(capture.getBearingAccuracyDegrees());
        assertNull(capture.getSpeedAccuracyMps());
    }

    /**
     * The counterpart to the metadata test above, and the one that matters
     * most: these four are the fields a reader is most likely to trust.
     * {@code Location} returns 0 from each getter when its {@code has*()} flag
     * is false, and 0 m accuracy, 0 m altitude, a due-north bearing and a
     * stationary speed are all values a real fix can legitimately have -- so a
     * substituted zero here is indistinguishable from a measurement for every
     * later reader, and cannot be recovered once stored.
     */
    @Test
    public void from_withoutCoreMeasurements_reportsThemAsNotReported() {
        Location location = new Location("gps");
        location.setLatitude(1.0);
        location.setLongitude(1.0);
        // hasAltitude()/hasAccuracy()/hasBearing()/hasSpeed() are all false
        // when the values are never set on this fix.

        RawGnssCapture capture = RawGnssCapture.from(location);

        assertNull("no altitude was reported", capture.getAltitude());
        assertNull("no accuracy was reported", capture.getAccuracyMeters());
        assertNull("no bearing was reported", capture.getBearingDegrees());
        assertNull("no speed was reported", capture.getSpeedMps());
    }

    /** A reported measurement still arrives intact. */
    @Test
    public void from_withCoreMeasurements_keepsThemExactly() {
        Location location = new Location("gps");
        location.setLatitude(1.0);
        location.setLongitude(1.0);
        location.setAltitude(42.0);
        location.setAccuracy(5.0f);
        location.setBearing(180.0f);
        location.setSpeed(2.5f);

        RawGnssCapture capture = RawGnssCapture.from(location);

        assertEquals(42.0, capture.getAltitude(), 0.0001);
        assertEquals(5.0, capture.getAccuracyMeters(), 0.0001);
        assertEquals(180.0, capture.getBearingDegrees(), 0.0001);
        assertEquals(2.5, capture.getSpeedMps(), 0.0001);
    }

    /**
     * A real zero is a measurement, not an absence. A stationary searcher
     * facing north reports 0 bearing and 0 speed, and that must still store as
     * 0 rather than collapsing into "not reported".
     */
    @Test
    public void from_withZeroMeasurements_keepsThemAsMeasurements() {
        Location location = new Location("gps");
        location.setLatitude(1.0);
        location.setLongitude(1.0);
        location.setAltitude(0.0);
        location.setAccuracy(0.0f);
        location.setBearing(0.0f);
        location.setSpeed(0.0f);

        RawGnssCapture capture = RawGnssCapture.from(location);

        assertEquals(Double.valueOf(0.0), capture.getAltitude());
        assertEquals(Double.valueOf(0.0), capture.getAccuracyMeters());
        assertEquals(Double.valueOf(0.0), capture.getBearingDegrees());
        assertEquals(Double.valueOf(0.0), capture.getSpeedMps());
    }
}
