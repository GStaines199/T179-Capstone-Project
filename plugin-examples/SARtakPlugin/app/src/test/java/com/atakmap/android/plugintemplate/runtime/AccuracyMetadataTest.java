package com.atakmap.android.plugintemplate.runtime;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

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

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Accuracy metadata reaches the database unmodified, including the fact that a
 * measurement was never reported.
 *
 * <p>The plugin used to write every fix through the primitive {@code insert},
 * which takes {@code float} and so cannot express "not reported" at the call
 * site. Verified on device, that did not actually corrupt the stored row:
 * SQLite coerces NaN to NULL on the way in, and rows written by the old build
 * already hold NULL. What these tests pin is that the honesty is now
 * deliberate rather than incidental -- it no longer depends on a coercion that
 * a NOT NULL column or a different binding would silently remove.
 *
 * <p>The fabrication itself lived on the read path, where a NULL came back as
 * 0.0; see {@code LocationRepository.getPointsForSession}, now covered in
 * {@code GnssCaptureTest}. A track logged with a fabricated 0 m accuracy is
 * worse than one logged with none, because it looks authoritative.
 *
 * <p>Every assertion here reads the raw column and asks whether it is NULL,
 * rather than going through {@code getPointsForSession}, so these tests pin
 * the stored row itself and stay independent of how any reader presents it.
 *
 * <p>Drives the real write path -- {@link LocationCaptureManager#applyCapture}
 * through {@link SearchTrackManager} into a real SQLite database.
 * {@code mapView} and {@code identityManager} are null because that path never
 * touches them.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, manifest = Config.NONE)
public class AccuracyMetadataTest {

    private static final String UID = "uid-1";
    private static final String CALLSIGN = "RESCUE-1";
    private static final double LAT = -27.4705;
    private static final double LON = 153.0260;
    private static final double ACCURACY = 5.0;

    private DatabaseHelper dbHelper;
    private SearchTrackManager trackManager;
    private LocationCaptureManager captureManager;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        dbHelper = new DatabaseHelper(context);
        trackManager = new SearchTrackManager(
                new TrackSessionRepository(dbHelper),
                new LocationRepository(dbHelper));
        PluginHealthManager healthManager = new PluginHealthManager();
        healthManager.start();
        healthManager.setStorageReady(true, "Local storage ready");
        healthManager.setTrackingActive(true);
        captureManager = new LocationCaptureManager(null, null, trackManager,
                healthManager, null);
        trackManager.startOrResume(UID, CALLSIGN);
    }

    @After
    public void tearDown() {
        dbHelper.close();
    }

    // ---- helpers ---------------------------------------------------------

    private void capture(Double altitude, Double bearing, Double speed) {
        captureManager.applyCapture(true, "Identity: " + CALLSIGN, UID,
                CALLSIGN, LocationCaptureManager.Fix.available(LAT, LON,
                        altitude, ACCURACY, bearing, speed, 1000L, "GPS"));
    }

    /** True where the column is SQL NULL, in insertion order. */
    private List<Boolean> nullFlags(String column) {
        List<Boolean> flags = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query("location_points", new String[] { column },
                null, null, null, null, "id ASC");
        while (cursor.moveToNext())
            flags.add(cursor.isNull(0));
        cursor.close();
        return flags;
    }

    private List<Double> values(String column) {
        List<Double> out = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query("location_points", new String[] { column },
                null, null, null, null, "id ASC");
        while (cursor.moveToNext())
            out.add(cursor.isNull(0) ? null : cursor.getDouble(0));
        cursor.close();
        return out;
    }

    private void assertStoredNull(String column, String why) {
        List<Boolean> flags = nullFlags(column);
        assertEquals("expected exactly one row", 1, flags.size());
        assertTrue(why, flags.get(0));
    }

    // ---- an unreported measurement is stored as unreported ---------------

    @Test
    public void unreportedBearing_isStoredAsNullNotDueNorth() {
        capture(42.0, null, 1.2);

        assertStoredNull("bearing_degrees",
                "an unreported bearing must not become heading due north");
    }

    @Test
    public void unreportedAltitude_isStoredAsNullNotSeaLevel() {
        capture(null, 90.0, 1.2);

        assertStoredNull("altitude",
                "an unreported altitude must not become sea level");
    }

    @Test
    public void unreportedSpeed_isStoredAsNullNotStationary() {
        capture(42.0, 90.0, null);

        assertStoredNull("speed_mps",
                "an unreported speed must not become stationary");
    }

    @Test
    public void unreportedAccuracy_isStoredAsNullNotZeroMetres() {
        trackManager.recordFix(UID, CALLSIGN, LAT, LON, 42.0, null, 90.0, 1.2,
                1000L);

        assertStoredNull("accuracy_meters",
                "an unreported accuracy must not become accurate to 0 m");
    }

    // ---- but a real zero is still a real zero ---------------------------

    /**
     * The headline distinction. Both rows would previously have been stored
     * identically; a reader could not tell the searcher who was measured at
     * 0 m from the one who was never measured at all.
     */
    @Test
    public void zeroAccuracy_staysDistinguishableFromAnUnreportedOne() {
        trackManager.recordFix(UID, CALLSIGN, LAT, LON, 42.0, 0.0, 90.0, 1.2,
                1000L);
        trackManager.recordFix(UID, CALLSIGN, LAT, LON, 42.0, null, 90.0, 1.2,
                2000L);

        List<Boolean> flags = nullFlags("accuracy_meters");
        assertEquals(2, flags.size());
        assertFalse("a measured 0 m is a reading and must be stored",
                flags.get(0));
        assertTrue("an unreported accuracy must be stored as NULL",
                flags.get(1));
        assertEquals(Double.valueOf(0.0), values("accuracy_meters").get(0));
    }

    @Test
    public void zeroBearing_staysDistinguishableFromAnUnreportedOne() {
        capture(42.0, 0.0, 1.2);
        capture(42.0, null, 1.2);

        List<Boolean> flags = nullFlags("bearing_degrees");
        assertEquals(2, flags.size());
        assertFalse("a genuine due-north heading must be stored",
                flags.get(0));
        assertTrue("an unreported bearing must be stored as NULL",
                flags.get(1));
    }

    // ---- NaN never reaches the database ----------------------------------

    /**
     * The primitive entry point still exists and callers still use it. A NaN
     * arriving that way must be resolved to "not reported" rather than left
     * for SQLite to coerce.
     */
    @Test
    public void aNaNArrivingThroughThePrimitivePath_isStoredAsNotReported() {
        trackManager.recordLocation(UID, CALLSIGN, LAT, LON, 42.0,
                Double.NaN, Double.NaN, Double.NaN, 1000L);

        assertStoredNull("accuracy_meters", "NaN accuracy must not be a value");
        assertStoredNull("bearing_degrees", "NaN bearing must not be a value");
        assertStoredNull("speed_mps", "NaN speed must not be a value");
    }

    @Test
    public void aFixWithNoReadingsAtAll_storesNoFabricatedMeasurements() {
        capture(null, null, null);

        assertStoredNull("altitude", "altitude must be NULL");
        assertStoredNull("bearing_degrees", "bearing must be NULL");
        assertStoredNull("speed_mps", "speed must be NULL");
    }

    // ---- reported values are preserved unmodified ------------------------

    /**
     * The other half of "preserve accuracy metadata without modification": a
     * guard that dropped real readings would be its own kind of data loss.
     */
    @Test
    public void reportedMeasurements_reachTheDatabaseUnmodified() {
        capture(42.5, 91.25, 1.75);

        assertEquals(Double.valueOf(42.5), values("altitude").get(0));
        assertEquals(91.25, values("bearing_degrees").get(0), 0.001);
        assertEquals(1.75, values("speed_mps").get(0), 0.001);
        assertEquals(ACCURACY, values("accuracy_meters").get(0), 0.001);
    }

    @Test
    public void theFixItselfKnowsWhatWasReported() {
        LocationCaptureManager.Fix fix = LocationCaptureManager.Fix.available(
                LAT, LON, null, ACCURACY, 90.0, Double.NaN, 1000L, "GPS");

        assertFalse("no altitude was reported", fix.hasAltitude());
        assertTrue("a bearing was reported", fix.hasBearing());
        assertFalse("NaN speed is not a reported speed", fix.hasSpeed());
    }
}
