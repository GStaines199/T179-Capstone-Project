package com.atakmap.android.plugintemplate.runtime;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the crash report text format.
 * <p>
 * Dependencies (build.gradle):
 *   testImplementation 'junit:junit:4.13.2'
 */
public class CrashReportFormatterTest {

    @Test
    public void format_includesThreadDeviceVersionAndStackTrace() {
        Exception thrown = new IllegalStateException("boom");

        String report = CrashReportFormatter.format(1700000000000L,
                "main", thrown, "Pixel 7", 33, "1.2.3");

        assertTrue(report.contains("Thread: main"));
        assertTrue(report.contains("Pixel 7"));
        assertTrue(report.contains("Android SDK 33"));
        assertTrue(report.contains("1.2.3"));
        assertTrue(report.contains("IllegalStateException"));
        assertTrue(report.contains("boom"));
    }

    @Test
    public void format_withNullFields_fillsInUnknownRatherThanThrowing() {
        String report = CrashReportFormatter.format(1700000000000L, null,
                new RuntimeException("x"), null, 0, null);

        assertTrue(report.contains("Thread: unknown"));
        assertTrue(report.contains("Device: unknown"));
        assertTrue(report.contains("Plugin version: unknown"));
    }

    @Test
    public void format_withNullThrowable_stillProducesAReport() {
        String report = CrashReportFormatter.format(1700000000000L, "main",
                null, "Pixel 7", 33, "1.2.3");

        assertTrue(report.contains("no throwable supplied"));
    }
}
