package com.atakmap.android.plugintemplate.runtime;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;
import java.util.UUID;

/**
 * Supplies this install's own UID and callsign for the case ATAK has no
 * identity to give.
 * <p>
 * The UID is a random UUID minted once and kept in SharedPreferences rather
 * than being derived from the Android ID. Two searchers sharing a UID is not a
 * cosmetic problem: their CoT events overwrite each other's marker, and their
 * position reports land in one another's track session. The Android ID invites
 * exactly that - it is identical on every emulator cloned from the same
 * snapshot, was a fixed constant on a well known batch of devices, and is null
 * on some hardware. A random UUID makes a collision negligible no matter how
 * the devices were provisioned, and persisting it keeps the UID stable across
 * plugin, ATAK and device restarts.
 *
 * @see IdentityManager for where this sits in the identity precedence order
 */
public class DeviceIdentityStore {

    static final String PREFS_NAME = "sartak_device_identity";
    static final String KEY_UID = "device.uid";

    /** Namespaces our UIDs so they read distinctly from ATAK's own. */
    static final String UID_PREFIX = "SARTAK-";

    /** Callsign used when the device model is unavailable. */
    static final String UNKNOWN_MODEL_CALLSIGN = "Searcher";

    /** How many of the UID's hex digits are appended to a fallback callsign. */
    static final int CALLSIGN_SUFFIX_LENGTH = 4;

    private final SharedPreferences preferences;

    public DeviceIdentityStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME,
                Context.MODE_PRIVATE);
    }

    /**
     * The UID for this install: minted on the first call, unchanged after
     * that.
     */
    public String getOrCreateUid() {
        String existing = preferences.getString(KEY_UID, null);
        if (!isBlank(existing))
            return existing;

        String uid = generateUid();
        preferences.edit().putString(KEY_UID, uid).apply();
        return uid;
    }

    /**
     * The callsign for this install. The model on its own repeats across a
     * team issued identical handsets - three searchers all called "Pixel 7" -
     * so the UID's leading hex digits are appended to tell them apart.
     */
    public String getFallbackCallsign(String deviceModel) {
        return buildFallbackCallsign(deviceModel, getOrCreateUid());
    }

    /** Kept package private and static so it can be tested without a Context. */
    static String generateUid() {
        return UID_PREFIX + UUID.randomUUID().toString().toUpperCase(Locale.US);
    }

    static String buildFallbackCallsign(String deviceModel, String uid) {
        String base = isBlank(deviceModel) ? UNKNOWN_MODEL_CALLSIGN
                : deviceModel.trim();
        String suffix = uidSuffix(uid);
        return suffix.length() == 0 ? base : base + "-" + suffix;
    }

    /**
     * The first few hex digits of the UID's random body. The prefix is skipped
     * first - "SARTAK" is itself partly made of hex digits, and those are the
     * same on every device.
     */
    static String uidSuffix(String uid) {
        if (uid == null)
            return "";

        String body = uid.startsWith(UID_PREFIX)
                ? uid.substring(UID_PREFIX.length())
                : uid;

        StringBuilder suffix = new StringBuilder();
        for (int i = 0; i < body.length()
                && suffix.length() < CALLSIGN_SUFFIX_LENGTH; i++) {
            char c = Character.toUpperCase(body.charAt(i));
            if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F'))
                suffix.append(c);
        }
        return suffix.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }
}
