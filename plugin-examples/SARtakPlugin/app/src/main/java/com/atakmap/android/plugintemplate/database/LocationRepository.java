package com.atakmap.android.plugintemplate.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.atakmap.android.plugintemplate.runtime.RawGnssCapture;

import java.util.ArrayList;
import java.util.List;

public class LocationRepository {

    public static final String CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS location_points (" +
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
                    "session_id TEXT," +
                    "provider TEXT," +
                    "vertical_accuracy_meters REAL," +
                    "bearing_accuracy_degrees REAL," +
                    "speed_accuracy_mps REAL)";

    public static final String[] RAW_GNSS_COLUMNS = {
            "provider",
            "vertical_accuracy_meters",
            "bearing_accuracy_degrees",
            "speed_accuracy_mps"
    };

    private final DatabaseHelper dbHelper;

    public LocationRepository(DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public void insert(String uid, String callsign, double latitude,
                       double longitude, double altitude, float accuracy,
                       float bearing, float speed, long timestamp, String sessionId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("uid", uid);
        values.put("callsign", callsign);
        values.put("latitude", latitude);
        values.put("longitude", longitude);
        values.put("altitude", altitude);
        values.put("accuracy_meters", accuracy);
        values.put("bearing_degrees", bearing);
        values.put("speed_mps", speed);
        values.put("timestamp", timestamp);
        values.put("session_id", sessionId);
        db.insert("location_points", null, values);
    }

    public void insertRaw(String uid, String callsign, String sessionId,
                          RawGnssCapture capture) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("uid", uid);
        values.put("callsign", callsign);
        values.put("latitude", capture.getLatitude());
        values.put("longitude", capture.getLongitude());
        values.put("altitude", capture.getAltitude());
        values.put("accuracy_meters", capture.getAccuracyMeters());
        values.put("bearing_degrees", capture.getBearingDegrees());
        values.put("speed_mps", capture.getSpeedMps());
        values.put("timestamp", capture.getTimestamp());
        values.put("session_id", sessionId);
        values.put("provider", capture.getProvider());
        values.put("vertical_accuracy_meters", capture.getVerticalAccuracyMeters());
        values.put("bearing_accuracy_degrees", capture.getBearingAccuracyDegrees());
        values.put("speed_accuracy_mps", capture.getSpeedAccuracyMps());
        db.insert("location_points", null, values);
    }

    public List<double[]> getPointsForSession(String sessionId) {
        List<double[]> points = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query("location_points",
                new String[]{"latitude", "longitude", "timestamp", "accuracy_meters"},
                "session_id = ?", new String[]{sessionId},
                null, null, "timestamp ASC");
        while (cursor.moveToNext()) {
            points.add(new double[]{
                    cursor.getDouble(0),
                    cursor.getDouble(1),
                    cursor.getLong(2),
                    cursor.getFloat(3)
            });
        }
        cursor.close();
        return points;
    }

    public int countPointsInSession(String sessionId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM location_points WHERE session_id = ?",
                new String[]{sessionId});
        int count = 0;
        if (cursor.moveToFirst()) count = cursor.getInt(0);
        cursor.close();
        return count;
    }

    public void deleteSession(String sessionId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("location_points", "session_id = ?", new String[]{sessionId});
    }
}