package com.atakmap.android.plugintemplate.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class SearcherRepository {

    public static final String CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS searcher_identity (" +
                    "uid TEXT PRIMARY KEY," +
                    "callsign TEXT," +
                    "device_model TEXT," +
                    "first_seen INTEGER," +
                    "last_seen INTEGER," +
                    "is_self INTEGER DEFAULT 0)";

    private final DatabaseHelper dbHelper;

    public SearcherRepository(DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public void insertOrUpdate(String uid, String callsign, String deviceModel,
                               long firstSeen, long lastSeen, boolean isSelf) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("uid", uid);
        values.put("callsign", callsign);
        values.put("device_model", deviceModel);
        values.put("first_seen", firstSeen);
        values.put("last_seen", lastSeen);
        values.put("is_self", isSelf ? 1 : 0);
        db.insertWithOnConflict("searcher_identity", null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String[] getSelfIdentity() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query("searcher_identity",
                new String[]{"uid", "callsign"},
                "is_self = 1", null, null, null, null, "1");
        String[] result = null;
        if (cursor.moveToFirst()) {
            result = new String[]{cursor.getString(0), cursor.getString(1)};
        }
        cursor.close();
        return result; // returns [uid, callsign] or null
    }

    public void updateLastSeen(String uid, long lastSeen) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("last_seen", lastSeen);
        db.update("searcher_identity", values, "uid = ?", new String[]{uid});
    }
}