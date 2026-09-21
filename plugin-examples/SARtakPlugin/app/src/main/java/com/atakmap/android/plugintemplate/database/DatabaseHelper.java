package com.atakmap.android.plugintemplate.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "sar_database.db";
    private static final int DATABASE_VERSION = 2;

    private static volatile DatabaseHelper INSTANCE;

    public static DatabaseHelper getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (DatabaseHelper.class) {
                if (INSTANCE == null) {
                    Context safeContext = context.getApplicationContext() != null
                            ? context.getApplicationContext()
                            : context;
                    INSTANCE = new DatabaseHelper(safeContext);
                }
            }
        }
        return INSTANCE;
    }

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(LocationRepository.CREATE_TABLE);
        db.execSQL(SearcherRepository.CREATE_TABLE);
        db.execSQL(TrackSessionRepository.CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE location_points ADD COLUMN provider TEXT");
            db.execSQL("ALTER TABLE location_points ADD COLUMN vertical_accuracy_meters REAL");
            db.execSQL("ALTER TABLE location_points ADD COLUMN bearing_accuracy_degrees REAL");
            db.execSQL("ALTER TABLE location_points ADD COLUMN speed_accuracy_mps REAL");
        }
    }
}
