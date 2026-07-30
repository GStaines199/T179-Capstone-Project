package com.atakmap.android.plugintemplate.runtime;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the GNSS capture decision made by
 * AtakLocationStatus#evaluate: whether ATAK is reporting a usable GPS fix,
 * which fix timestamp and location source win, and the message shown when the
 * fix is rejected.
 * <p>
 * The ATAK plumbing in AtakLocationStatus#from(MapView) is not covered here -
 * ATAK SDK classes (MapView, MapData, GeoPoint) fail JVM bytecode verification
 * when loaded outside Android, so they cannot appear in a unit test. Everything
 * below drives the ATAK map data through the MetaSource seam instead.
 * <p>
 * Dependencies (build.gradle):
 *   testImplementation 'junit:junit:4.13.2'
 */
public class GnssFixEvaluationTest {

    /** A plausible "now": 2023-11-14T22:13:20Z. */
    private static final long NOW = 1700000000000L;

    private static final long STALE_MS = PluginHealthManager.LOCATION_STALE_MS;

    /** Any timestamp at or below 2000-01-01 is treated as implausible. */
    private static final long IMPLAUSIBLE_FIX_MS = 946684800000L;

    private static final double GOOD_ACCURACY_M = 5.0;

    private static final String MARKER_SOURCE = "GPS";

    private final FakeMetaSource data = new FakeMetaSource();

    // -------------------------------------------------------------------------
    // Baseline
    // -------------------------------------------------------------------------

    @Test
    public void evaluate_withNoLocationKeys_acceptsFixUsingMarkerSource() {
        AtakLocationStatus.Evaluation result = evaluate();

        assertTrue(result.isAvailable());
        assertEquals("", result.getMessage());
        assertEquals(MARKER_SOURCE, result.getSource());
        assertEquals(NOW, result.getTimestamp());
    }

    @Test
    public void empty_publishesNoKeys() {
        assertFalse(AtakLocationStatus.EMPTY.containsKey("LocationAvailable"));
        assertTrue(AtakLocationStatus.EMPTY.getBoolean("LocationAvailable",
                true));
        assertEquals(7L, AtakLocationStatus.EMPTY.getLong("LocationTime", 7L));
        assertEquals("fallback",
                AtakLocationStatus.EMPTY.getString("LocationSrc", "fallback"));
    }

    // -------------------------------------------------------------------------
    // device.gps.issue
    // -------------------------------------------------------------------------

    @Test
    public void evaluate_whenGpsIssueFlagSet_rejectsFix() {
        data.put("device.gps.issue", true);

        assertRejected("No GPS Signal", evaluate());
    }

    @Test
    public void evaluate_whenGpsIssueHeldAsString_stillRejectsFix() {
        // ATAK has published this key as a String; getBoolean then throws and
        // the raw value has to be parsed instead.
        data.put("device.gps.issue", "true");

        assertRejected("No GPS Signal", evaluate());
    }

    @Test
    public void evaluate_whenGpsIssueStringIsFalse_acceptsFix() {
        data.put("device.gps.issue", "false");

        assertTrue(evaluate().isAvailable());
    }

    @Test
    public void evaluate_whenGpsIssueFlagClear_acceptsFix() {
        data.put("device.gps.issue", false);

        assertTrue(evaluate().isAvailable());
    }

    @Test
    public void evaluate_gpsIssueOutranksAnOtherwisePerfectFix() {
        data.put("device.gps.issue", true);
        data.put("LocationAvailable", true);
        data.put("LocationTime", NOW);
        data.put("LocationSrc", "GPS");

        assertRejected("No GPS Signal", evaluate());
    }

    // -------------------------------------------------------------------------
    // Availability flags
    // -------------------------------------------------------------------------

    @Test
    public void evaluate_whenLocationAvailableTrue_acceptsFix() {
        data.put("LocationAvailable", true);

        assertTrue(evaluate().isAvailable());
    }

    @Test
    public void evaluate_whenLocationAvailableFalse_rejectsFix() {
        data.put("LocationAvailable", false);

        assertRejected("No GPS Signal", evaluate());
    }

    @Test
    public void evaluate_globalUnavailableOutranksStalePrefixedFlag() {
        // GPS switched off after a fix: the source-specific flag can lag, so
        // the explicit global "not available" has to win.
        data.put("locationSourceEffectivePrefix", "gps.");
        data.put("gps.LocationAvailable", true);
        data.put("LocationAvailable", false);

        assertRejected("No GPS Signal", evaluate());
    }

    @Test
    public void evaluate_whenInternalLocationUnavailable_rejectsFix() {
        data.put("internalLocationAvailable", false);

        assertRejected("No GPS Signal", evaluate());
    }

    @Test
    public void evaluate_whenFineLocationUnavailable_rejectsFix() {
        data.put("fineLocationAvailable", false);

        assertRejected("No GPS Signal", evaluate());
    }

    @Test
    public void evaluate_whenInternalAndFineLocationAvailable_acceptsFix() {
        data.put("internalLocationAvailable", true);
        data.put("fineLocationAvailable", true);

        assertTrue(evaluate().isAvailable());
    }

    @Test
    public void evaluate_whenPrefixedAvailabilityFalse_rejectsFix() {
        data.put("locationSourceEffectivePrefix", "gps.");
        data.put("gps.LocationAvailable", false);

        assertRejected("No GPS Signal", evaluate());
    }

    @Test
    public void evaluate_fallsBackToLocationSourcePrefix() {
        data.put("locationSourcePrefix", "gps.");
        data.put("gps.LocationAvailable", false);

        assertRejected("No GPS Signal", evaluate());
    }

    @Test
    public void evaluate_effectivePrefixOutranksLocationSourcePrefix() {
        data.put("locationSourceEffectivePrefix", "external.");
        data.put("locationSourcePrefix", "internal.");
        data.put("external.LocationAvailable", true);
        data.put("internal.LocationAvailable", false);

        assertTrue(evaluate().isAvailable());
    }

    // -------------------------------------------------------------------------
    // Fix timestamp and staleness
    // -------------------------------------------------------------------------

    @Test
    public void evaluate_keepsFreshFixTimestamp() {
        long fix = NOW - 5000L;
        data.put("LocationTime", fix);

        AtakLocationStatus.Evaluation result = evaluate();

        assertTrue(result.isAvailable());
        assertEquals(fix, result.getTimestamp());
    }

    @Test
    public void evaluate_whenFixOlderThanStaleWindow_rejectsFixWithAge() {
        data.put("LocationTime", NOW - (STALE_MS + 1000L));

        assertRejected("GPS stale; ATAK fix is "
                + ((STALE_MS + 1000L) / 1000L) + " seconds old", evaluate());
    }

    @Test
    public void evaluate_whenFixExactlyAtStaleWindow_acceptsFix() {
        long fix = NOW - STALE_MS;
        data.put("LocationTime", fix);

        AtakLocationStatus.Evaluation result = evaluate();

        assertTrue(result.isAvailable());
        assertEquals(fix, result.getTimestamp());
    }

    @Test
    public void evaluate_stalenessOutranksAccuracyAndSourceChecks() {
        data.put("LocationTime", NOW - (STALE_MS + 1000L));
        data.put("LocationSrc", "???");

        assertRejected("GPS stale; ATAK fix is "
                + ((STALE_MS + 1000L) / 1000L) + " seconds old",
                evaluate(Double.NaN));
    }

    @Test
    public void evaluate_whenFixTimestampImplausible_substitutesNow() {
        data.put("LocationTime", IMPLAUSIBLE_FIX_MS);

        AtakLocationStatus.Evaluation result = evaluate();

        assertTrue(result.isAvailable());
        assertEquals(NOW, result.getTimestamp());
    }

    @Test
    public void evaluate_whenFixTimestampMissing_substitutesNow() {
        data.put("LocationAvailable", true);

        AtakLocationStatus.Evaluation result = evaluate();

        assertTrue(result.isAvailable());
        assertEquals(NOW, result.getTimestamp());
    }

    @Test
    public void evaluate_prefixedFixTimestampOutranksGlobal() {
        long prefixed = NOW - 1000L;
        data.put("locationSourceEffectivePrefix", "gps.");
        data.put("gps.LocationTime", prefixed);
        data.put("LocationTime", NOW - 20000L);

        assertEquals(prefixed, evaluate().getTimestamp());
    }

    @Test
    public void evaluate_usesFineLocationTimeWhenLocationTimeMissing() {
        long fine = NOW - 2000L;
        data.put("fineLocationTime", fine);

        assertEquals(fine, evaluate().getTimestamp());
    }

    @Test
    public void evaluate_usesInternalLocationTimeWhenFineTimeUnset() {
        long internal = NOW - 3000L;
        data.put("fineLocationTime", 0L);
        data.put("internalLocationTime", internal);

        assertEquals(internal, evaluate().getTimestamp());
    }

    @Test
    public void evaluate_fineLocationTimeOutranksInternalLocationTime() {
        long fine = NOW - 2000L;
        data.put("fineLocationTime", fine);
        data.put("internalLocationTime", NOW - 3000L);

        assertEquals(fine, evaluate().getTimestamp());
    }

    // -------------------------------------------------------------------------
    // Location source
    // -------------------------------------------------------------------------

    @Test
    public void evaluate_prefixedSourceOutranksGlobalSource() {
        data.put("locationSourceEffectivePrefix", "external.");
        data.put("external.LocationSrc", "EXTERNAL GPS");
        data.put("LocationSrc", "GPS");

        assertEquals("EXTERNAL GPS", evaluate().getSource());
    }

    @Test
    public void evaluate_globalSourceOutranksMarkerSource() {
        data.put("LocationSrc", "GPS RTK");

        assertEquals("GPS RTK", evaluate().getSource());
    }

    @Test
    public void evaluate_whenGlobalSourceEmpty_fallsBackToMarkerSource() {
        data.put("LocationSrc", "");

        assertEquals(MARKER_SOURCE, evaluate().getSource());
    }

    @Test
    public void evaluate_whenSourceIsQuestionMarks_rejectsFix() {
        data.put("LocationSrc", "???");

        assertRejected("No GPS Signal", evaluate());
    }

    @Test
    public void evaluate_whenSourceIsUnknown_rejectsFixIgnoringCase() {
        data.put("LocationSrc", "UNKNOWN");

        assertRejected("No GPS Signal", evaluate());
    }

    @Test
    public void evaluate_whenSourceIsAtakPlaceholder_rejectsFix() {
        // "ATAK" is the placeholder used when nothing reported a real source.
        data.put("LocationSrc", "atak");

        assertRejected("No GPS Signal", evaluate());
    }

    @Test
    public void evaluate_whenSourceIsWhitespace_rejectsFix() {
        data.put("LocationSrc", "   ");

        assertRejected("No GPS Signal", evaluate());
    }

    @Test
    public void evaluate_whenNoSourceAnywhere_rejectsFix() {
        assertRejected("No GPS Signal",
                AtakLocationStatus.evaluate(data, GOOD_ACCURACY_M, null, NOW));
    }

    @Test
    public void evaluate_whenMarkerSourceUnknown_rejectsFix() {
        assertRejected("No GPS Signal",
                AtakLocationStatus.evaluate(data, GOOD_ACCURACY_M, "???", NOW));
    }

    // -------------------------------------------------------------------------
    // Accuracy
    // -------------------------------------------------------------------------

    @Test
    public void evaluate_whenAccuracyNotANumber_rejectsFix() {
        assertRejected("No GPS Signal", evaluate(Double.NaN));
    }

    @Test
    public void evaluate_whenAccuracyInfinite_rejectsFix() {
        assertRejected("No GPS Signal",
                evaluate(Double.POSITIVE_INFINITY));
    }

    @Test
    public void evaluate_whenAccuracyZero_rejectsFix() {
        assertRejected("No GPS Signal", evaluate(0.0));
    }

    @Test
    public void evaluate_whenAccuracyNegative_rejectsFix() {
        assertRejected("No GPS Signal", evaluate(-1.0));
    }

    @Test
    public void evaluate_whenAccuracyTightlyPositive_acceptsFix() {
        assertTrue(evaluate(0.5).isAvailable());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private AtakLocationStatus.Evaluation evaluate() {
        return evaluate(GOOD_ACCURACY_M);
    }

    private AtakLocationStatus.Evaluation evaluate(double accuracy) {
        return AtakLocationStatus.evaluate(data, accuracy, MARKER_SOURCE, NOW);
    }

    private void assertRejected(String expectedMessage,
            AtakLocationStatus.Evaluation result) {
        assertFalse(result.isAvailable());
        assertEquals(expectedMessage, result.getMessage());
        assertEquals(0L, result.getTimestamp());
        assertEquals("", result.getSource());
    }

    /**
     * Stands in for ATAK's MapData. Type mismatches throw, the way the real
     * typed getters do, so the lenient device.gps.issue path is exercised.
     */
    private static class FakeMetaSource implements AtakLocationStatus.MetaSource {

        private final Map<String, Object> values = new HashMap<>();

        void put(String key, Object value) {
            values.put(key, value);
        }

        @Override
        public boolean containsKey(String key) {
            return values.containsKey(key);
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            Object value = values.get(key);
            if (value == null)
                return defaultValue;
            return (Boolean) value;
        }

        @Override
        public long getLong(String key, long defaultValue) {
            Object value = values.get(key);
            if (value == null)
                return defaultValue;
            return ((Number) value).longValue();
        }

        @Override
        public String getString(String key, String defaultValue) {
            Object value = values.get(key);
            if (value == null)
                return defaultValue;
            return (String) value;
        }

        @Override
        public Object getRaw(String key) {
            return values.get(key);
        }
    }
}
