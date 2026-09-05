package com.atakmap.android.test;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.atakmap.android.plugintemplate.database.DatabaseHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, manifest = Config.NONE)
public class DatabaseHelperTest {

    private DatabaseHelper dbHelper;
    private SQLiteDatabase db;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        dbHelper = new DatabaseHelper(context);
        db = dbHelper.getWritableDatabase();
    }

    @After
    public void tearDown() {
        db.close();
        dbHelper.close();
    }

    @Test
    public void onCreate_createsLocationPointsTable() {
        assertTrue(tableExists("location_points"));
    }

    @Test
    public void onCreate_createsSearcherIdentityTable() {
        assertTrue(tableExists("searcher_identity"));
    }

    @Test
    public void onCreate_createsTrackSessionsTable() {
        assertTrue(tableExists("track_sessions"));
    }

    @Test
    public void locationPoints_hasRequiredColumns() {
        assertTrue(columnExists("location_points", "uid"));
        assertTrue(columnExists("location_points", "latitude"));
        assertTrue(columnExists("location_points", "longitude"));
        assertTrue(columnExists("location_points", "timestamp"));
        assertTrue(columnExists("location_points", "accuracy_meters"));
        assertTrue(columnExists("location_points", "session_id"));
    }

    @Test
    public void locationPoints_hasRawGnssColumns() {
        assertTrue(columnExists("location_points", "provider"));
        assertTrue(columnExists("location_points", "vertical_accuracy_meters"));
        assertTrue(columnExists("location_points", "bearing_accuracy_degrees"));
        assertTrue(columnExists("location_points", "speed_accuracy_mps"));
    }

    @Test
    public void onUpgrade_fromVersion1_addsRawGnssColumnsWithoutDroppingExistingData() {
        // Simulate a pre-migration (version 1) schema with one stored row.
        db.execSQL("DROP TABLE IF EXISTS location_points");
        db.execSQL("CREATE TABLE location_points (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "uid TEXT," +
                "callsign TEXT," +
                "latitude REAL," +
                "longitude REAL," +
                "altitude REAL," +
                "accuracy_meters REAL," +
                "bearing_degrees REAL," +
                "speed_mps REAL," +
                "timestamp INTEGER," +
                "session_id TEXT)");
        db.execSQL("INSERT INTO location_points (uid, callsign, latitude, longitude, " +
                "altitude, accuracy_meters, bearing_degrees, speed_mps, timestamp, session_id) " +
                "VALUES ('uid1', 'Alice', -27.47, 153.02, 10.0, 5.0, 90.0, 1.5, 1000, 'sessionX')");

        dbHelper.onUpgrade(db, 1, 2);

        assertTrue(columnExists("location_points", "provider"));
        assertTrue(columnExists("location_points", "vertical_accuracy_meters"));
        assertTrue(columnExists("location_points", "bearing_accuracy_degrees"));
        assertTrue(columnExists("location_points", "speed_accuracy_mps"));

        Cursor cursor = db.rawQuery(
                "SELECT uid, latitude, provider FROM location_points WHERE session_id = ?",
                new String[]{"sessionX"});
        assertTrue(cursor.moveToFirst());
        assertEquals("uid1", cursor.getString(0));
        assertEquals(-27.47, cursor.getDouble(1), 0.0001);
        assertTrue(cursor.isNull(2));
        cursor.close();
    }

    @Test
    public void searcherIdentity_hasRequiredColumns() {
        assertTrue(columnExists("searcher_identity", "uid"));
        assertTrue(columnExists("searcher_identity", "callsign"));
        assertTrue(columnExists("searcher_identity", "is_self"));
    }

    @Test
    public void trackSessions_hasRequiredColumns() {
        assertTrue(columnExists("track_sessions", "session_id"));
        assertTrue(columnExists("track_sessions", "uid"));
        assertTrue(columnExists("track_sessions", "is_active"));
        assertTrue(columnExists("track_sessions", "point_count"));
    }

    private boolean tableExists(String tableName) {
        Cursor cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                new String[]{tableName});
        boolean exists = cursor.getCount() > 0;
        cursor.close();
        return exists;
    }

    private boolean columnExists(String table, String column) {
        Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        int nameIndex = cursor.getColumnIndex("name");
        while (cursor.moveToNext()) {
            if (cursor.getString(nameIndex).equals(column)) {
                cursor.close();
                return true;
            }
        }
        cursor.close();
        return false;
    }
}