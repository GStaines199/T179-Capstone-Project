package com.atakmap.android.plugintemplate.database;

/**
 * Distinguishes a measurement a GPS receiver actually reported from one it did
 * not.
 *
 * <p>ATAK hands back {@code Double.NaN} for a value it has no reading for --
 * altitude, track heading and track speed all do this. A primitive
 * {@code double} cannot carry that distinction any further.
 *
 * <p>SQLite coerces NaN to NULL on the way in, so the stored row is honest
 * even without this class. The damage is done on the way back out, where
 * {@code Cursor.getFloat} turns a NULL into {@code 0.0}: "no heading reported"
 * becomes "heading due north" and "no accuracy reported" becomes "accurate to
 * 0 m". Both are values a real fix can legitimately have, so a reader cannot
 * tell the difference afterwards.
 *
 * <p>A track logged with a fabricated 0 m accuracy is worse than one logged
 * with no accuracy at all, because it looks authoritative. This class is where
 * that judgement is made, once, so every caller makes it the same way.
 */
public final class ReportedMeasurement {

    private ReportedMeasurement() {
    }

    /**
     * The value if the receiver reported one, or null if it did not.
     *
     * <p>NaN means "no reading". Infinity is treated the same way: it is not a
     * measurement either, and it would round-trip through SQLite just as
     * misleadingly.
     */
    public static Double of(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value))
            return null;
        return value;
    }

    /** As {@link #of(double)}, for a value that may already be absent. */
    public static Double of(Double value) {
        if (value == null)
            return null;
        return of(value.doubleValue());
    }

    /**
     * Narrows a reported measurement for the columns stored as REAL floats,
     * preserving "not reported" as null rather than collapsing it to zero.
     */
    public static Float toFloat(Double value) {
        Double reported = of(value);
        if (reported == null)
            return null;
        return Float.valueOf(reported.floatValue());
    }
}
