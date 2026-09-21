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

        // Try insert first (preserves first_seen on conflict)
        ContentValues insert = new ContentValues();
        insert.put("uid", uid);
        insert.put("callsign", callsign);
        insert.put("device_model", deviceModel);
        insert.put("first_seen", firstSeen);
        insert.put("last_seen", lastSeen);
        insert.put("is_self", isSelf ? 1 : 0);
        long result = db.insertWithOnConflict("searcher_identity", null, insert,
                SQLiteDatabase.CONFLICT_IGNORE);  // IGNORE instead of REPLACE

        if (result == -1) {
            // Row exists — update everything except first_seen
            ContentValues update = new ContentValues();
            update.put("callsign", callsign);
            update.put("device_model", deviceModel);
            update.put("last_seen", lastSeen);
            update.put("is_self", isSelf ? 1 : 0);
            db.update("searcher_identity", update, "uid = ?", new String[]{uid});
        }
    }

    /**
     * Demotes any other row still flagged as self, so exactly one identity is
     * ours. Called when the resolved UID changes - otherwise a stale row keeps
     * its flag and {@link #getSelfIdentity()} can return the wrong searcher.
     */
    public void clearSelfFlagExcept(String uid) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("is_self", 0);
        db.update("searcher_identity", values, "is_self = 1 AND uid != ?",
                new String[]{uid});
    }

    public String[] getSelfIdentity() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query("searcher_identity",
                new String[]{"uid", "callsign"},
                "is_self = 1", null, null, null, "last_seen DESC", "1");
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