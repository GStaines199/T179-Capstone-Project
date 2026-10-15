package com.atakmap.android.plugintemplate.runtime;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;

/**
 * Formats a crash report as plain text: when it happened, which thread, the
 * device/plugin it happened on, and the full stack trace. Kept free of
 * Android types (device/version/app-version are passed in as plain values)
 * so the format itself is unit-testable - see SartakUncaughtExceptionHandler
 * for the Thread.UncaughtExceptionHandler wiring that calls this.
 */
final class CrashReportFormatter {

    private CrashReportFormatter() {
    }

    static String format(long timestampMillis, String threadName,
            Throwable throwable, String deviceModel, int sdkInt,
            String appVersion) {
        StringWriter out = new StringWriter();
        PrintWriter writer = new PrintWriter(out);
        writer.println("SARtak crash report");
        writer.println("Time: " + new Date(timestampMillis));
        writer.println("Thread: " + safe(threadName));
        writer.println("Device: " + safe(deviceModel) + " (Android SDK "
                + sdkInt + ")");
        writer.println("Plugin version: " + safe(appVersion));
        writer.println();
        if (throwable == null)
            writer.println("(no throwable supplied)");
        else
            throwable.printStackTrace(writer);
        return out.toString();
    }

    private static String safe(String value) {
        return value == null || value.length() == 0 ? "unknown" : value;
    }
}
