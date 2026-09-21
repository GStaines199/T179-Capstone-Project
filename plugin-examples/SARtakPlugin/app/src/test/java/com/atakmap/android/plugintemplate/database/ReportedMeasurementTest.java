package com.atakmap.android.plugintemplate.database;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * The rule that separates a measurement from a missing one.
 *
 * <p>ATAK returns NaN for a reading it does not have. Written to SQLite that
 * becomes NULL and reads back as 0.0, so "no bearing reported" turns into
 * "heading due north" and "no accuracy reported" turns into "accurate to 0 m".
 * Both are values a real fix can legitimately carry, which is why the
 * distinction has to be resolved before the write rather than after.
 *
 * <p>These tests require only JUnit -- no ATAK SDK, no Robolectric.
 */
public class ReportedMeasurementTest {

    // ---- NaN is not a measurement ---------------------------------------

    @Test
    public void of_treatsNaNAsNotReported() {
        assertNull(ReportedMeasurement.of(Double.NaN));
    }

    /**
     * Infinity is not a reading either, and it round-trips through SQLite just
     * as misleadingly as NaN.
     */
    @Test
    public void of_treatsInfinityAsNotReported() {
        assertNull(ReportedMeasurement.of(Double.POSITIVE_INFINITY));
        assertNull(ReportedMeasurement.of(Double.NEGATIVE_INFINITY));
    }

    @Test
    public void of_keepsAlreadyAbsentValuesAbsent() {
        assertNull(ReportedMeasurement.of((Double) null));
    }

    // ---- Real readings survive untouched ---------------------------------

    /**
     * The values this fix exists to protect. Zero accuracy and a zero bearing
     * are legitimate readings, and must never be confused with an absent one
     * by a guard that is too eager.
     */
    @Test
    public void of_keepsZeroAsARealReading() {
        assertEquals(Double.valueOf(0.0), ReportedMeasurement.of(0.0));
        assertNotNull("0 m accuracy is a reading, not a missing value",
                ReportedMeasurement.of(0.0));
    }

    @Test
    public void of_keepsOrdinaryReadings() {
        assertEquals(Double.valueOf(7.5), ReportedMeasurement.of(7.5));
        assertEquals(Double.valueOf(-27.4705),
                ReportedMeasurement.of(-27.4705));
    }

    @Test
    public void of_keepsABoxedReading() {
        assertEquals(Double.valueOf(12.0),
                ReportedMeasurement.of(Double.valueOf(12.0)));
    }

    // ---- Narrowing for the REAL columns ----------------------------------

    @Test
    public void toFloat_keepsNotReportedAsNull() {
        assertNull(ReportedMeasurement.toFloat(null));
        assertNull(ReportedMeasurement.toFloat(Double.NaN));
        assertNull(ReportedMeasurement.toFloat(Double.POSITIVE_INFINITY));
    }

    @Test
    public void toFloat_keepsZeroAsARealReading() {
        assertEquals(Float.valueOf(0.0f), ReportedMeasurement.toFloat(0.0));
    }

    @Test
    public void toFloat_narrowsAnOrdinaryReading() {
        assertEquals(7.5f, ReportedMeasurement.toFloat(7.5), 0.0001f);
    }
}
