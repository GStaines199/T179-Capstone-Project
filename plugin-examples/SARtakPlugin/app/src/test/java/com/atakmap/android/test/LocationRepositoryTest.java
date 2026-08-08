package com.atakmap.android.test;

import android.content.Context;

import android.location.Location;

import com.atakmap.android.plugintemplate.database.DatabaseHelper;
import com.atakmap.android.plugintemplate.database.LocationRepository;
import com.atakmap.android.plugintemplate.runtime.RawGnssCapture;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, manifest = Config.NONE)
public class LocationRepositoryTest {

    private LocationRepository repo;

    private DatabaseHelper dbHelper;


    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        dbHelper = new DatabaseHelper(context);
        repo = new LocationRepository(dbHelper);
    }

    @After
    public void tearDown() {
        dbHelper.close();
    }

    @Test
    public void insert_andQueryBySession_returnsInsertedPoint() {
        repo.insert("uid1", "Alice", -27.47, 153.02, 10.0f,
                5.0f, 90.0f, 1.5f, 1000L, "session1");

        List<double[]> points = repo.getPointsForSession("session1");
        assertEquals(1, points.size());
        assertEquals(-27.47, points.get(0)[0], 0.0001);
        assertEquals(153.02, points.get(0)[1], 0.0001);
    }

    @Test
    public void insert_multiplePoints_returnsInChronologicalOrder() {
        repo.insert("uid1", "Alice", -27.47, 153.02, 10.0f,
                5.0f, 0f, 0f, 3000L, "session2");
        repo.insert("uid1", "Alice", -27.48, 153.03, 10.0f,
                5.0f, 0f, 0f, 1000L, "session2");
        repo.insert("uid1", "Alice", -27.49, 153.04, 10.0f,
                5.0f, 0f, 0f, 2000L, "session2");

        List<double[]> points = repo.getPointsForSession("session2");
        assertEquals(3, points.size());
        // Should be ordered by timestamp ascending
        assertEquals(1000L, (long) points.get(0)[2]);
        assertEquals(2000L, (long) points.get(1)[2]);
        assertEquals(3000L, (long) points.get(2)[2]);
    }

    @Test
    public void countPoints_returnsCorrectCount() {
        repo.insert("uid1", "Alice", -27.47, 153.02, 10.0f,
                5.0f, 0f, 0f, 1000L, "session3");
        repo.insert("uid1", "Alice", -27.48, 153.03, 10.0f,
                5.0f, 0f, 0f, 2000L, "session3");

        assertEquals(2, repo.countPointsInSession("session3"));
    }

    @Test
    public void deleteSession_removesAllPoints() {
        repo.insert("uid1", "Alice", -27.47, 153.02, 10.0f,
                5.0f, 0f, 0f, 1000L, "session4");
        repo.insert("uid1", "Alice", -27.48, 153.03, 10.0f,
                5.0f, 0f, 0f, 2000L, "session4");

        repo.deleteSession("session4");
        assertEquals(0, repo.countPointsInSession("session4"));
    }

    @Test
    public void querySession_withNoPoints_returnsEmptyList() {
        List<double[]> points = repo.getPointsForSession("nonexistent_session");
        assertNotNull(points);
        assertEquals(0, points.size());
    }

    @Test
    public void insert_preservesAccuracyValue() {
        repo.insert("uid1", "Alice", -27.47, 153.02, 10.0f,
                12.5f, 0f, 0f, 1000L, "session5");

        List<double[]> points = repo.getPointsForSession("session5");
        assertEquals(12.5, points.get(0)[3], 0.001);
    }

    @Test
    public void pointsFromDifferentSessions_areIsolated() {
        repo.insert("uid1", "Alice", -27.47, 153.02, 10.0f,
                5.0f, 0f, 0f, 1000L, "sessionA");
        repo.insert("uid1", "Alice", -27.48, 153.03, 10.0f,
                5.0f, 0f, 0f, 2000L, "sessionB");

        assertEquals(1, repo.countPointsInSession("sessionA"));
        assertEquals(1, repo.countPointsInSession("sessionB"));
    }

    @Test
    public void insertRaw_storesAccuracyMetadataUnmodified() {
        Location location = new Location("gps");
        location.setLatitude(-27.47);
        location.setLongitude(153.02);
        location.setAltitude(10.0);
        location.setAccuracy(5.0f);
        location.setBearing(90.0f);
        location.setSpeed(1.5f);
        location.setTime(1000L);
        location.setVerticalAccuracyMeters(2.5f);
        location.setBearingAccuracyDegrees(3.5f);
        location.setSpeedAccuracyMetersPerSecond(0.5f);

        repo.insertRaw("uid1", "Alice", "sessionRaw1", RawGnssCapture.from(location));

        SQLiteCursorRow row = queryRawColumns("sessionRaw1");
        assertEquals("gps", row.provider);
        assertEquals(2.5, row.verticalAccuracy, 0.0001);
        assertEquals(3.5, row.bearingAccuracy, 0.0001);
        assertEquals(0.5, row.speedAccuracy, 0.0001);
    }

    @Test
    public void insertRaw_withUnavailableAccuracyExtras_storesNull() {
        Location location = new Location("network");
        location.setLatitude(-27.47);
        location.setLongitude(153.02);
        location.setTime(1000L);
        // No vertical/bearing/speed accuracy set on this fix.

        repo.insertRaw("uid1", "Alice", "sessionRaw2", RawGnssCapture.from(location));

        SQLiteCursorRow row = queryRawColumns("sessionRaw2");
        assertEquals("network", row.provider);
        assertNull(row.verticalAccuracy);
        assertNull(row.bearingAccuracy);
        assertNull(row.speedAccuracy);
    }

    private static class SQLiteCursorRow {
        String provider;
        Double verticalAccuracy;
        Double bearingAccuracy;
        Double speedAccuracy;
    }

    private SQLiteCursorRow queryRawColumns(String sessionId) {
        android.database.Cursor cursor = dbHelper.getReadableDatabase().query(
                "location_points",
                new String[]{"provider", "vertical_accuracy_meters",
                        "bearing_accuracy_degrees", "speed_accuracy_mps"},
                "session_id = ?", new String[]{sessionId},
                null, null, null);
        assertTrue(cursor.moveToFirst());
        SQLiteCursorRow row = new SQLiteCursorRow();
        row.provider = cursor.getString(0);
        row.verticalAccuracy = cursor.isNull(1) ? null : cursor.getDouble(1);
        row.bearingAccuracy = cursor.isNull(2) ? null : cursor.getDouble(2);
        row.speedAccuracy = cursor.isNull(3) ? null : cursor.getDouble(3);
        cursor.close();
        return row;
    }

}