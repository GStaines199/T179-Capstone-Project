package com.atakmap.android.plugintemplate.runtime;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationManager;

import com.atakmap.android.plugintemplate.database.DatabaseHelper;
import com.atakmap.android.plugintemplate.database.LocationRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for GNSS capture, in the order a fix travels: ATAK decides whether
 * it has a usable fix at all, then the fix is written to
 * {@code location_points} with its accuracy metadata intact.
 * <p>
 * <b>Fix evaluation</b> covers AtakLocationStatus#evaluate - staleness windows,
 * availability flags, which location source and fix timestamp win, and the
 * message shown when a fix is rejected. It needs no Android at all; it runs
 * under Robolectric only because it shares this file with the persistence half.
 * <p>
 * <b>Persistence</b> covers an {@link Location} as {@link LocationManager}
 * hands it over, written to the database and read back unmodified. "Unmodified"
 * is the whole requirement, so those assertions are deliberately exact -
 * {@code delta} is 0 wherever the value should survive bit for bit. A fix is not
 * rounded, clamped, averaged with its neighbours or filtered on the way in;
 * deciding whether a fix is usable at all is the evaluation half's job, not the
 * repository's.
 * <p>
 * The last section covers the one bug this task invites: writing {@code 0.0}
 * where {@code NULL} was meant. {@link Location#getAccuracy()} returns
 * {@code 0.0f} both when the fix is perfect and when the receiver reported no
 * accuracy at all - only {@link Location#hasAccuracy()} tells the two apart, and
 * the same trap applies to bearing and speed, where 0 is a real measurement
 * (due north, stationary). Those tests pin what the {@code location_points}
 * columns do with NULL so the capture code has somewhere to put "not reported".
 * <p>
 * Two things are deliberately not covered. ATAK SDK classes (MapView, MapData,
 * GeoPoint) fail JVM bytecode verification when loaded outside Android, so they
 * appear nowhere below and the plumbing in
 * {@code AtakLocationStatus.from(MapView)} is reached through the MetaSource
 * seam instead. And {@code LocationRepository.insert} takes primitive floats, so
 * it cannot express "not reported" yet - the NULL cases are driven through the
 * table directly. When the nullable insert lands, those tests should be
 * repointed at it; the expected values do not change.
 * <p>
 * Dependencies (build.gradle):
 *   testImplementation 'junit:junit:4.13.2'
 *   testImplementation 'org.robolectric:robolectric:4.9'
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, manifest = Config.NONE)
public class GnssCaptureTest {

    // --- Fix evaluation ------------------------------------------------------

    /** A plausible "now": 2023-11-14T22:13:20Z. */
    private static final long NOW = 1700000000000L;

    private static final long STALE_MS = PluginHealthManager.LOCATION_STALE_MS;

    /** Any timestamp at or below 2000-01-01 is treated as implausible. */
    private static final long IMPLAUSIBLE_FIX_MS = 946684800000L;

    private static final double GOOD_ACCURACY_M = 5.0;

    private static final String MARKER_SOURCE = "GPS";

    // --- Persistence ---------------------------------------------------------

    private static final String UID = "ANDROID-1234567890abcdef";
    private static final String CALLSIGN = "ALPHA";
    private static final String SESSION = "track-ANDROID-1234567890abcdef-fix";

    /** Brisbane, at the full precision a GNSS receiver reports. */
    private static final double LATITUDE = -27.4704558;
    private static final double LONGITUDE = 153.0259784;

    /** 2023-11-14T22:13:20.123Z - milliseconds included on purpose. */
    private static final long FIX_TIME = 1700000000123L;

    private static final String[] COLUMNS = {"uid", "callsign", "latitude",
            "longitude", "altitude", "accuracy_meters", "bearing_degrees",
            "speed_mps", "timestamp", "session_id"};

    private final FakeMetaSource data = new FakeMetaSource();

    private DatabaseHelper dbHelper;
    private LocationRepository repo;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        dbHelper = new DatabaseHelper(context);
        repo = new LocationRepository(dbHelper);
        dbHelper.getWritableDatabase().delete("location_points", null, null);
    }

    @After
    public void tearDown() {
        dbHelper.close();
    }

    // =========================================================================
    // Fix evaluation: baseline
    // =========================================================================

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

    // =========================================================================
    // Fix evaluation: device.gps.issue
    // =========================================================================

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

    // =========================================================================
    // Fix evaluation: availability flags
    // =========================================================================

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

    // =========================================================================
    // Fix evaluation: fix timestamp and staleness
    // =========================================================================

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

    // =========================================================================
    // Fix evaluation: location source
    // =========================================================================

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

    // =========================================================================
    // Fix evaluation: accuracy
    // =========================================================================

    @Test
    public void evaluate_whenAccuracyNotANumber_rejectsFix() {
        assertRejected("No GPS Signal", evaluate(Double.NaN));
    }

    @Test
    public void evaluate_whenAccuracyInfinite_rejectsFix() {
        assertRejected("No GPS Signal", evaluate(Double.POSITIVE_INFINITY));
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

    // =========================================================================
    // Persistence: a LocationManager fix survives the round trip unchanged
    // =========================================================================

    @Test
    public void insert_fromAGnssFix_storesEveryFieldUnmodified() {
        Location fix = gpsFix();
        fix.setAltitude(42.75);
        fix.setAccuracy(3.7f);
        fix.setBearing(87.25f);
        fix.setSpeed(1.35f);

        insert(fix);

        Cursor row = firstRow();
        assertEquals(UID, row.getString(0));
        assertEquals(CALLSIGN, row.getString(1));
        assertEquals(fix.getLatitude(), row.getDouble(2), 0.0);
        assertEquals(fix.getLongitude(), row.getDouble(3), 0.0);
        assertEquals(fix.getAltitude(), row.getDouble(4), 0.0);
        assertEquals(fix.getAccuracy(), row.getFloat(5), 0.0f);
        assertEquals(fix.getBearing(), row.getFloat(6), 0.0f);
        assertEquals(fix.getSpeed(), row.getFloat(7), 0.0f);
        assertEquals(fix.getTime(), row.getLong(8));
        assertEquals(SESSION, row.getString(9));
        row.close();
    }

    @Test
    public void insert_preservesAccuracyExactly_acrossTheReportedRange() {
        // Values a real receiver produces: sub-metre RTK through to a coarse
        // network fix, none of them round numbers.
        float[] accuracies = {0.001f, 0.5f, 3.7f, 12.345f, 65.0f, 9999.99f};

        for (int i = 0; i < accuracies.length; i++) {
            Location fix = gpsFix();
            fix.setAccuracy(accuracies[i]);
            fix.setTime(FIX_TIME + i);
            insert(fix);
        }

        Cursor rows = allRows();
        for (int i = 0; i < accuracies.length; i++) {
            assertTrue(rows.moveToNext());
            assertEquals("accuracy " + accuracies[i] + " was modified",
                    accuracies[i], rows.getFloat(5), 0.0f);
        }
        rows.close();
    }

    @Test
    public void insert_doesNotRoundAccuracyToWholeMetres() {
        Location fix = gpsFix();
        fix.setAccuracy(12.345f);

        insert(fix);

        Cursor row = firstRow();
        float stored = row.getFloat(5);
        assertNotEquals(12.0f, stored, 0.0f);
        assertNotEquals(13.0f, stored, 0.0f);
        assertEquals(12.345f, stored, 0.0f);
        row.close();
    }

    @Test
    public void insert_doesNotClampAnImplausiblyLargeAccuracy() {
        // A 5 km circle of error is useless for searching, but rejecting it is
        // the validation layer's call - the repository stores what it was given.
        Location fix = gpsFix();
        fix.setAccuracy(5000.0f);

        insert(fix);

        Cursor row = firstRow();
        assertEquals(5000.0f, row.getFloat(5), 0.0f);
        row.close();
    }

    @Test
    public void insert_storesAccuracyAtFloatPrecision() {
        // Location reports accuracy as a float; the column is REAL, so the
        // widened value has to be exactly the float, not a rounded decimal.
        Location fix = gpsFix();
        fix.setAccuracy(12.345f);

        insert(fix);

        Cursor row = firstRow();
        assertEquals((double) 12.345f, row.getDouble(5), 0.0);
        row.close();
    }

    @Test
    public void insert_preservesFullLatitudeLongitudePrecision() {
        // Seven decimal places is roughly 1 cm; truncating to five would move
        // the point by about a metre and quietly widen every track.
        insert(gpsFix());

        Cursor row = firstRow();
        assertEquals(LATITUDE, row.getDouble(2), 0.0);
        assertEquals(LONGITUDE, row.getDouble(3), 0.0);
        row.close();
    }

    @Test
    public void insert_preservesAltitudeAtDoublePrecision() {
        Location fix = gpsFix();
        fix.setAltitude(-3.14159);

        insert(fix);

        Cursor row = firstRow();
        assertEquals(-3.14159, row.getDouble(4), 0.0);
        row.close();
    }

    @Test
    public void insert_preservesTheFixTimestampToTheMillisecond() {
        // Truncating to whole seconds would collapse fixes that arrive in the
        // same second and break the timestamp ordering the track relies on.
        insert(gpsFix());

        Cursor row = firstRow();
        assertEquals(FIX_TIME, row.getLong(8));
        row.close();
    }

    @Test
    public void insert_acrossConsecutiveFixes_keepsEachAccuracyDistinct() {
        // Walking under tree cover: accuracy degrades then recovers. Nothing may
        // smooth, average or de-duplicate that sequence.
        float[] accuracies = {3.0f, 25.0f, 8.0f};
        for (int i = 0; i < accuracies.length; i++) {
            Location fix = gpsFix();
            fix.setAccuracy(accuracies[i]);
            fix.setTime(FIX_TIME + (i * 1000L));
            insert(fix);
        }

        assertEquals(3, repo.countPointsInSession(SESSION));

        Cursor rows = allRows();
        for (float accuracy : accuracies) {
            assertTrue(rows.moveToNext());
            assertEquals(accuracy, rows.getFloat(5), 0.0f);
        }
        rows.close();
    }

    @Test
    public void getPointsForSession_returnsAccuracyAtFloatPrecision() {
        Location fix = gpsFix();
        fix.setAccuracy(12.345f);

        insert(fix);

        List<double[]> points = repo.getPointsForSession(SESSION);
        assertEquals(1, points.size());
        assertEquals((double) 12.345f, points.get(0)[3], 0.0);
    }

    // =========================================================================
    // Persistence: "not reported" is not zero
    // =========================================================================

    @Test
    public void aFixWithNoAccuracy_reportsZeroMetresAnyway() {
        // The trap: getAccuracy() alone cannot tell a perfect fix from a fix
        // that carried no accuracy at all, so capture has to branch on
        // hasAccuracy() and write NULL for the second case.
        Location fix = gpsFix();

        assertFalse(fix.hasAccuracy());
        assertEquals(0.0f, fix.getAccuracy(), 0.0f);
    }

    @Test
    public void aFixWithNoBearingOrSpeed_reportsZeroForBoth() {
        // Zero is a real measurement here - due north, and stationary - which is
        // exactly why "not reported" has to be stored as something else.
        Location fix = gpsFix();

        assertFalse(fix.hasBearing());
        assertEquals(0.0f, fix.getBearing(), 0.0f);
        assertFalse(fix.hasSpeed());
        assertEquals(0.0f, fix.getSpeed(), 0.0f);
    }

    @Test
    public void aFixWithNoAltitude_reportsZeroMetres() {
        Location fix = gpsFix();

        assertFalse(fix.hasAltitude());
        assertEquals(0.0, fix.getAltitude(), 0.0);
    }

    @Test
    public void accuracyColumn_acceptsNull() {
        insertRawWithNulls("accuracy_meters");

        Cursor row = firstRow();
        assertTrue("accuracy_meters must be able to hold \"not reported\"",
                row.isNull(5));
        row.close();
    }

    @Test
    public void bearingAndSpeedColumns_acceptNull() {
        insertRawWithNulls("bearing_degrees", "speed_mps");

        Cursor row = firstRow();
        assertTrue(row.isNull(6));
        assertTrue(row.isNull(7));
        row.close();
    }

    @Test
    public void altitudeColumn_acceptsNull() {
        insertRawWithNulls("altitude");

        Cursor row = firstRow();
        assertTrue(row.isNull(4));
        row.close();
    }

    @Test
    public void nullAccuracy_isIndistinguishableFromZeroThroughGetFloat() {
        // Why every reader has to check isNull first: a NULL column reads back
        // as 0.0f, so a stored NULL silently becomes "accurate to 0 m".
        insertRawWithNulls("accuracy_meters");

        Cursor row = firstRow();
        assertTrue(row.isNull(5));
        assertEquals(0.0f, row.getFloat(5), 0.0f);
        row.close();
    }

    @Test
    public void getPointsForSession_collapsesNullAccuracyToZero() {
        // Pins the current reader: it returns a double[] with no room for
        // "unknown", so a NULL accuracy arrives at the caller as 0 m. Whatever
        // consumes accuracy has to gain a way to see the difference.
        insertRawWithNulls("accuracy_meters");

        List<double[]> points = repo.getPointsForSession(SESSION);
        assertEquals(1, points.size());
        assertEquals(0.0, points.get(0)[3], 0.0);
    }

    @Test
    public void insert_withZeroAccuracy_storesZeroRatherThanNull() {
        // The primitive-float insert can only ever write a value, so a fix that
        // reported no accuracy would land as a genuine 0 m if it were passed
        // straight through.
        Location fix = gpsFix();
        fix.setAccuracy(0.0f);

        insert(fix);

        Cursor row = firstRow();
        assertFalse(row.isNull(5));
        assertEquals(0.0f, row.getFloat(5), 0.0f);
        row.close();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

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

    /** A fix as LocationManager delivers it: position and time only. */
    private static Location gpsFix() {
        Location fix = new Location(LocationManager.GPS_PROVIDER);
        fix.setLatitude(LATITUDE);
        fix.setLongitude(LONGITUDE);
        fix.setTime(FIX_TIME);
        return fix;
    }

    private void insert(Location fix) {
        repo.insert(UID, CALLSIGN, fix.getLatitude(), fix.getLongitude(),
                fix.getAltitude(), fix.getAccuracy(), fix.getBearing(),
                fix.getSpeed(), fix.getTime(), SESSION);
    }

    /**
     * Writes a row with the named columns left NULL. Stands in for the capture
     * code until its insert can express "not reported".
     */
    private void insertRawWithNulls(String... nullColumns) {
        ContentValues values = new ContentValues();
        values.put("uid", UID);
        values.put("callsign", CALLSIGN);
        values.put("latitude", LATITUDE);
        values.put("longitude", LONGITUDE);
        values.put("altitude", 42.75);
        values.put("accuracy_meters", 3.7f);
        values.put("bearing_degrees", 87.25f);
        values.put("speed_mps", 1.35f);
        values.put("timestamp", FIX_TIME);
        values.put("session_id", SESSION);
        for (String column : nullColumns)
            values.putNull(column);

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        assertNotEquals("insert failed", -1L,
                db.insert("location_points", null, values));
    }

    private Cursor firstRow() {
        Cursor cursor = allRows();
        assertTrue("no row was written", cursor.moveToFirst());
        return cursor;
    }

    private Cursor allRows() {
        return dbHelper.getReadableDatabase().query("location_points", COLUMNS,
                "session_id = ?", new String[]{SESSION}, null, null, "id ASC");
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
