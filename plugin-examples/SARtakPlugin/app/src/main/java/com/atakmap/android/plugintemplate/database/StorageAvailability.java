package com.atakmap.android.plugintemplate.database;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Answers whether local storage actually works, rather than assuming it does.
 *
 * <p>The plugin used to call {@code setStorageReady(true, "Local storage
 * ready")} at startup without opening anything. That made the storage half of
 * {@code PluginHealthState.INACTIVE} unreachable in production: if SQLite could
 * not open the database, the plugin reported itself healthy and silently logged
 * nothing -- the single worst failure for a track log, because the operator has
 * no reason to doubt it until the search is over.
 *
 * <p>Opening is not enough on its own. A database can open read-only on a full
 * or remounted filesystem, and it can open with tables missing if a create or
 * an upgrade failed part way. Both leave every write throwing while the handle
 * itself looks fine, so all three are checked here.
 *
 * <p>Split out as its own class for the same reason as
 * {@code MemberPositionPolicy}: the only production caller is
 * {@code SARTakMapController}, which needs a {@code MapView} and so cannot be
 * built in a JVM test. This class touches no ATAK types, so the rule itself
 * stays testable even though its caller does not.
 */
public final class StorageAvailability {

    /** Message reported when every check passes. */
    public static final String READY_MESSAGE = "Local storage ready";

    /** Prefix on every failure, so the panel line is scannable. */
    static final String UNAVAILABLE_PREFIX = "Storage unavailable: ";

    /**
     * Tables the plugin cannot function without. Deliberately listed here
     * rather than derived from the CREATE statements: this is the set the
     * runtime needs, and it should fail loudly if a schema change stops
     * creating one of them.
     */
    static final String[] REQUIRED_TABLES = {
            "location_points", "searcher_identity", "track_sessions"
    };

    /**
     * How the probe gets at a database. Exists so a test can make opening fail
     * in ways that are impractical to stage against a real file -- a throwing
     * open, a null return, a handle that is already closed.
     */
    public interface Opener {
        /** Opens for writing, or throws the way {@code SQLiteOpenHelper} does. */
        SQLiteDatabase openForWriting();
    }

    /** Outcome of a probe: whether to log, and what to tell the operator. */
    public static final class Result {

        private final boolean ready;
        private final String message;

        Result(boolean ready, String message) {
            this.ready = ready;
            this.message = message;
        }

        /** True only when the database opened, is writable and is complete. */
        public boolean isReady() {
            return ready;
        }

        /** Operator-facing line for the health panel. */
        public String getMessage() {
            return message;
        }
    }

    private StorageAvailability() {
    }

    /** Probes the real helper the plugin runs on. */
    public static Result probe(final DatabaseHelper helper) {
        if (helper == null)
            return unavailable("no database was created");
        return probe(new Opener() {
            @Override
            public SQLiteDatabase openForWriting() {
                return helper.getWritableDatabase();
            }
        });
    }

    /**
     * Runs the checks in the order a failure would actually happen: the open
     * itself, then the handle, then what the handle can do, then whether the
     * schema behind it is complete.
     *
     * <p>Catches {@code RuntimeException} rather than {@code SQLiteException}
     * alone. The point of this probe is that no storage fault can leave the
     * plugin claiming health, and narrowing the catch would let an unforeseen
     * one through to exactly that outcome.
     *
     * <p>Several of these checks would eventually surface as a thrown exception
     * anyway -- a closed handle throws on the next call, a read-only one throws
     * on the next write. They are tested explicitly regardless, so the operator
     * gets a sentence naming the problem instead of a raw SQLite message, and
     * so the failure is reported at startup rather than at the first fix.
     */
    public static Result probe(Opener opener) {
        if (opener == null)
            return unavailable("no database was created");
        try {
            SQLiteDatabase db = opener.openForWriting();
            if (db == null)
                return unavailable("the database did not open");
            if (!db.isOpen())
                return unavailable("the database closed immediately");
            if (db.isReadOnly())
                return unavailable("storage is read-only, so no track can be "
                        + "logged");
            String missing = firstMissingTable(db);
            if (missing != null)
                return unavailable("the " + missing + " table is missing");
            return new Result(true, READY_MESSAGE);
        } catch (RuntimeException e) {
            return unavailable(describe(e));
        }
    }

    /** The first required table SQLite does not have, or null if all exist. */
    private static String firstMissingTable(SQLiteDatabase db) {
        for (String table : REQUIRED_TABLES) {
            Cursor cursor = db.rawQuery(
                    "SELECT name FROM sqlite_master"
                            + " WHERE type = 'table' AND name = ?",
                    new String[]{table});
            try {
                if (!cursor.moveToFirst())
                    return table;
            } finally {
                cursor.close();
            }
        }
        return null;
    }

    /**
     * Names the failure without assuming SQLite supplied a message. An
     * exception class on its own still tells the operator more than a plugin
     * that claims to be logging.
     */
    private static String describe(RuntimeException e) {
        String detail = e.getMessage();
        String type = e.getClass().getSimpleName();
        if (detail == null || detail.trim().length() == 0)
            return type;
        return type + ": " + detail.trim();
    }

    private static Result unavailable(String reason) {
        return new Result(false, UNAVAILABLE_PREFIX + reason);
    }
}
