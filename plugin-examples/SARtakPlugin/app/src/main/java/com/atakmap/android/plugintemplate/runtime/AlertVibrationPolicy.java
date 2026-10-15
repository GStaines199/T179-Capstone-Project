package com.atakmap.android.plugintemplate.runtime;

/**
 * Decides whether a newly-shown team alert should vibrate the device, and
 * with what pattern. Kept ATAK/Android-type-free (no Vibrator, no Context)
 * so the decision is unit-testable - the actual Vibrator call is a thin
 * wrapper around this in PluginTemplateDropDownReceiver.
 */
public final class AlertVibrationPolicy {

    private AlertVibrationPolicy() {
    }

    /**
     * Vibration waveform in milliseconds: off, on, off, on, ... An empty
     * array means the alert should not vibrate the device.
     */
    public static long[] patternFor(String alertType) {
        if (SearchAlertMessage.TYPE_EMERGENCY_STOP.equals(alertType))
            return new long[] { 0, 400, 150, 400, 150, 400 };
        if (SearchAlertMessage.requiresHalt(alertType))
            return new long[] { 0, 400 };
        return new long[0];
    }
}
