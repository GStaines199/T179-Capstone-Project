package com.atakmap.android.plugintemplate.runtime;

import android.content.Context;
import android.os.Build;

import com.atakmap.coremap.log.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Writes a plain-text crash report to external storage before handing the
 * crash on to whatever handler ATAK already had installed - this never
 * replaces or suppresses ATAK's own crash handling, it only adds a SARtak
 * -specific report alongside it so a developer can pull reliable
 * reproduction detail (stack trace, device, plugin version, timestamp) off
 * a device after a field test without needing a live logcat session.
 * <p>
 * Reports are written to
 * {@code <external files dir>/sartak_crash_reports/crash_<timestamp>.txt}
 * and pulled with:
 * {@code adb pull /storage/emulated/0/Android/data/<atak package>/files/sartak_crash_reports}
 */
public final class SartakUncaughtExceptionHandler
        implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "SartakUncaughtExceptionHandler";
    private static final String REPORTS_DIR_NAME = "sartak_crash_reports";
    private static final int MAX_RETAINED_REPORTS = 20;

    private final Context context;
    private final Thread.UncaughtExceptionHandler previousHandler;

    private SartakUncaughtExceptionHandler(Context context,
            Thread.UncaughtExceptionHandler previousHandler) {
        this.context = context;
        this.previousHandler = previousHandler;
    }

    /**
     * Installs the handler if it is not already installed. Safe to call more
     * than once (e.g. if the plugin is re-initialised within the same ATAK
     * process) - it will not wrap itself twice.
     */
    public static void install(Context context) {
        if (context == null)
            return;
        Thread.UncaughtExceptionHandler current = Thread
                .getDefaultUncaughtExceptionHandler();
        if (current instanceof SartakUncaughtExceptionHandler)
            return;
        Thread.setDefaultUncaughtExceptionHandler(
                new SartakUncaughtExceptionHandler(
                        context.getApplicationContext() != null
                                ? context.getApplicationContext() : context,
                        current));
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            writeReport(thread, throwable);
        } catch (Throwable reportingFailure) {
            // Crash reporting must never itself crash the crash handler, or
            // make an unrelated crash harder to diagnose.
            Log.w(TAG, "Failed to write SARtak crash report",
                    reportingFailure);
        }
        if (previousHandler != null)
            previousHandler.uncaughtException(thread, throwable);
    }

    private void writeReport(Thread thread, Throwable throwable) {
        String text = CrashReportFormatter.format(System.currentTimeMillis(),
                thread == null ? null : thread.getName(), throwable,
                Build.MODEL, Build.VERSION.SDK_INT, appVersion());

        File dir = reportsDirectory();
        if (dir == null)
            return;
        if (!dir.exists() && !dir.mkdirs())
            return;
        pruneOldReports(dir);

        File report = new File(dir, "crash_" + System.currentTimeMillis()
                + ".txt");
        try (Writer writer = new FileWriter(report)) {
            writer.write(text);
        } catch (IOException ioException) {
            Log.w(TAG, "Failed to write crash report file", ioException);
        }
    }

    private File reportsDirectory() {
        File base = context.getExternalFilesDir(null);
        return base == null ? null : new File(base, REPORTS_DIR_NAME);
    }

    private void pruneOldReports(File dir) {
        File[] files = dir.listFiles();
        if (files == null || files.length < MAX_RETAINED_REPORTS)
            return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        int toDelete = files.length - MAX_RETAINED_REPORTS + 1;
        for (int i = 0; i < toDelete; i++)
            files[i].delete();
    }

    private String appVersion() {
        try {
            return context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "unknown";
        }
    }
}
