package com.atakmap.android.plugintemplate.runtime;

import java.util.Locale;

/**
 * Times the consecutive steps of one pass, for a single debug log line such
 * as {@code ms=24.1 connect=0.1 contacts=17.9 arrange=3.2}. Each
 * {@link #mark} closes the step that ran since the previous mark.
 */
public final class StepTimer {

    /** Steps shorter than this are left out to keep the line readable. */
    private static final long MIN_REPORTED_NANOS = 100_000L;

    private final long startedNanos;
    private long lastNanos;
    private final StringBuilder steps = new StringBuilder();

    public StepTimer() {
        this(System.nanoTime());
    }

    StepTimer(long nowNanos) {
        startedNanos = nowNanos;
        lastNanos = nowNanos;
    }

    public void mark(String step) {
        mark(step, System.nanoTime());
    }

    void mark(String step, long nowNanos) {
        long elapsed = nowNanos - lastNanos;
        lastNanos = nowNanos;
        if (elapsed < MIN_REPORTED_NANOS)
            return;
        steps.append(' ').append(step).append('=').append(format(elapsed));
    }

    /** "ms=<total>" followed by every step that took 0.1 ms or more. */
    public String summary() {
        return "ms=" + format(lastNanos - startedNanos) + steps;
    }

    private static String format(long nanos) {
        return String.format(Locale.US, "%.1f", nanos / 1_000_000.0);
    }
}
