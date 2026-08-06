package com.atakmap.android.plugintemplate.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

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
                    "session_id TEXT)";

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

    /**
     * Inserts a fix, writing NULL for anything the receiver did not report.
     * <p>
     * {@link #insert} takes primitives, so it cannot tell a measurement from a
     * missing one: a fix carrying no accuracy lands as "accurate to 0 m", and a
     * fix carrying no bearing lands as "heading due north". Both are values a
     * real fix can legitimately have, so the difference cannot be recovered
     * afterwards. Callers holding a {@code Location} should branch on its
     * {@code hasAltitude()}, {@code hasAccuracy()}, {@code hasBearing()} and
     * {@code hasSpeed()} flags and pass null for whatever was not reported.
     */
    public void insertFix(String uid, String callsign, double latitude,
                          double longitude, Double altitude, Float accuracy,
                          Float bearing, Float speed, long timestamp,
                          String sessionId) {
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