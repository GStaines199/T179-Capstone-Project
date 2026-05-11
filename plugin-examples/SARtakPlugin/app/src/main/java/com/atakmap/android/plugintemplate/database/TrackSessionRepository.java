package com.atakmap.android.plugintemplate.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class TrackSessionRepository {

    public static final String CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS track_sessions (" +
                    "session_id TEXT PRIMARY KEY," +
                    "uid TEXT," +
                    "callsign TEXT," +
                    "start_timestamp INTEGER," +
                    "end_timestamp INTEGER DEFAULT 0," +
                    "point_count INTEGER DEFAULT 0," +
                    "is_active INTEGER DEFAULT 1)";

    private final DatabaseHelper dbHelper;

    public TrackSessionRepository(DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public void insert(String sessionId, String uid, String callsign, long startTimestamp) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("session_id", sessionId);
        values.put("uid", uid);
        values.put("callsign", callsign);
        values.put("start_timestamp", startTimestamp);
        values.put("is_active", 1);
        db.insertWithOnConflict("track_sessions", null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String getActiveSessionId(String uid) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query("track_sessions",
                new String[]{"session_id"},
                "uid = ? AND is_active = 1", new String[]{uid},
                null, null, null, "1");
        String sessionId = null;
        if (cursor.moveToFirst()) sessionId = cursor.getString(0);
        cursor.close();
        return sessionId;
    }

    public long getSessionStartTimestamp(String sessionId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query("track_sessions",
                new String[]{"start_timestamp"},
                "session_id = ?", new String[]{sessionId},
                null, null, null, "1");
        long timestamp = 0L;
        if (cursor.moveToFirst()) timestamp = cursor.getLong(0);
        cursor.close();
        return timestamp;
    }

    public void closeSession(String sessionId, long endTimestamp) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("is_active", 0);
        values.put("end_timestamp", endTimestamp);
        db.update("track_sessions", values,
                "session_id = ?", new String[]{sessionId});
    }

    public void deleteSession(String sessionId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("track_sessions", "session_id = ?", new String[]{sessionId});
    }

    public void incrementPointCount(String sessionId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.execSQL(
                "UPDATE track_sessions SET point_count = point_count + 1 WHERE session_id = ?",
                new String[]{sessionId});
    }
}
