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

    /**
     * @deprecated Use {@link #insertFix} instead.
     * <p>
     * This takes primitives, so it cannot express "not reported" at the call
     * site; it depends on SQLite coercing NaN to NULL to avoid storing a
     * fabricated measurement. That coercion does hold today, so this method
     * was not corrupting stored data -- but it leaves the intent implicit and
     * would break under a NOT NULL column or a different binding.
     * {@code insertFix} was written to make it explicit and then went uncalled
     * for three sprints, so the pointer now comes from the compiler rather
     * than from a comment nobody read. Retained only for tests that
     * deliberately exercise the primitive path.
     */
    @Deprecated
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
     * {@link #insert} takes primitives, so it cannot express "not reported" at
     * the call site. In practice a NaN passed to it still lands as NULL,
     * because SQLite coerces NaN on the way in -- verified on device, where
     * {@code typeof(0.0/0.0)} is {@code null}. The corruption happens on the
     * way back out instead: {@code Cursor.getFloat} returns 0.0 for a NULL, so
     * an unreported accuracy becomes "accurate to 0 m" and an unreported
     * bearing becomes "heading due north" at the reader. See
     * {@link #getPointsForSession}, which resolves that to NaN.
     * <p>
     * Prefer this method regardless: relying on the NaN coercion leaves the
     * intent implicit and would break under a NOT NULL column or a different
     * binding. Callers holding a {@code Location} should branch on its
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

    /**
     * Inserts a fix along with the raw GNSS metadata columns.
     * <p>
     * The three metadata accuracies are boxed on {@link RawGnssCapture}, so
     * they land as NULL when the receiver did not report them. Horizontal
     * accuracy, bearing and speed do not: {@code RawGnssCapture.from}
     * substitutes 0f for each one the receiver omitted, so they arrive here
     * already indistinguishable from real zeroes, and
     * {@link #getPointsForSession} will report such a row as "accurate to 0 m"
     * rather than NaN. Closing that gap means making those three fields
     * nullable on {@code RawGnssCapture}; until then, prefer
     * {@link #insertFix} on any path whose accuracy is read back.
     */
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

    /**
     * Points for a session, as {lat, lon, timestamp, accuracy}.
     * <p>
     * Accuracy is {@code Double.NaN} when the receiver never reported one.
     * {@code Cursor.getFloat} returns 0.0 for a NULL column, so reading it
     * straight would turn "no accuracy reported" into "accurate to 0 m" -- a
     * value a real fix can legitimately have, and therefore indistinguishable
     * from a measurement once it reaches a caller. This is where that
     * fabrication actually happened: the write path stores NULL correctly,
     * because SQLite coerces NaN to NULL on the way in.
     * <p>
     * Callers must test {@link Double#isNaN} before treating the accuracy as a
     * measurement. {@link ReportedMeasurement#of(double)} does exactly that.
     */
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
                    cursor.isNull(3) ? Double.NaN : cursor.getFloat(3)
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
