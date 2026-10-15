package com.atakmap.android.plugintemplate.runtime;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * What this device remembers about shared markers, per operation, across
 * ATAK restarts:
 * <ul>
 * <li>which markers it imported from other devices, so they can be taken
 * off the map when the user is in a different operation (finding T4-2).
 * ATAK doesn't save SARtak's own operation tag on a marker, so after a
 * restart this list is the only way to tell;</li>
 * <li>which shared markers were on the map at the last scan, so one deleted
 * while SARtak wasn't running is still deleted for everyone (T4-5);</li>
 * <li>when the device first joined, and whether the user said to share
 * their markers from before that (T4-7).</li>
 * </ul>
 */
public class SharedMarkerStateStore {

    private static final String PREFS_NAME = "sartak_shared_markers";
    private static final String IMPORTED = "imported.";
    private static final String PRESENT = "present.";
    private static final String JOINED_AT = "joinedAt.";
    private static final String EXISTING = "existing.";

    private final SharedPreferences preferences;

    public SharedMarkerStateStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME,
                Context.MODE_PRIVATE);
    }

    public Set<String> getImported(String operationId) {
        return read(IMPORTED + safe(operationId));
    }

    /** Records an import; returns false if it was already recorded. */
    public boolean addImported(String operationId, String markerUid) {
        String key = IMPORTED + safe(operationId);
        Set<String> imported = read(key);
        if (safe(operationId).length() == 0 || !imported.add(safe(markerUid)))
            return false;
        preferences.edit().putStringSet(key, imported).apply();
        return true;
    }

    /** Records several imports, writing only if any is new. */
    public void addImported(String operationId,
            Collection<String> markerUids) {
        String key = IMPORTED + safe(operationId);
        Set<String> imported = read(key);
        if (safe(operationId).length() == 0 || !imported.addAll(markerUids))
            return;
        preferences.edit().putStringSet(key, imported).apply();
    }

    /** Every other operation's imported markers, by operation. */
    public Map<String, Set<String>> getImportedByOperation() {
        Map<String, Set<String>> byOperation = new java.util.HashMap<>();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            if (entry.getKey().startsWith(IMPORTED))
                byOperation.put(entry.getKey().substring(IMPORTED.length()),
                        read(entry.getKey()));
        }
        return byOperation;
    }

    /**
     * Forgets that imports taken off the map were present. They come back
     * from Ditto if the user returns to that operation, and must not count
     * as deleted meanwhile. They stay on the imported list, so ATAK
     * reloading one later still gets it removed again.
     */
    public void forgetPresent(String operationId,
            Collection<String> markerUids) {
        String key = PRESENT + safe(operationId);
        Set<String> remaining = read(key);
        if (!remaining.removeAll(markerUids))
            return;
        preferences.edit().putStringSet(key, remaining).apply();
    }

    public Set<String> getPresent(String operationId) {
        return read(PRESENT + safe(operationId));
    }

    public void savePresent(String operationId, Set<String> markerUids) {
        if (safe(operationId).length() == 0)
            return;
        String key = PRESENT + safe(operationId);
        if (read(key).equals(markerUids))
            return;
        preferences.edit().putStringSet(key, new HashSet<>(markerUids))
                .apply();
    }

    /** When this device first joined the operation; recorded on first call. */
    public long getOrRecordJoinedAt(String operationId, long now) {
        String key = JOINED_AT + safe(operationId);
        long joinedAt = preferences.getLong(key, 0L);
        if (joinedAt > 0L)
            return joinedAt;
        preferences.edit().putLong(key, now).apply();
        return now;
    }

    public SharedMapMarkerReconciler.ExistingMarkers getExistingMarkers(
            String operationId) {
        String value = preferences.getString(EXISTING + safe(operationId), "");
        try {
            return value.length() == 0
                    ? SharedMapMarkerReconciler.ExistingMarkers.ASK
                    : SharedMapMarkerReconciler.ExistingMarkers.valueOf(value);
        } catch (IllegalArgumentException unknown) {
            return SharedMapMarkerReconciler.ExistingMarkers.ASK;
        }
    }

    public void setExistingMarkers(String operationId,
            SharedMapMarkerReconciler.ExistingMarkers answer) {
        preferences.edit().putString(EXISTING + safe(operationId),
                answer.name()).apply();
    }

    /**
     * Markers imported for other operations that should not be on the map
     * in {@code currentOperationId}. One also imported for the current
     * operation stays.
     */
    static Map<String, Set<String>> foreignImports(
            Map<String, Set<String>> importedByOperation,
            String currentOperationId) {
        String current = safe(currentOperationId);
        Set<String> keep = importedByOperation.get(current);
        Map<String, Set<String>> foreign = new java.util.HashMap<>();
        for (Map.Entry<String, Set<String>> entry
                : importedByOperation.entrySet()) {
            if (entry.getKey().equals(current))
                continue;
            Set<String> uids = new HashSet<>(entry.getValue());
            if (keep != null)
                uids.removeAll(keep);
            if (!uids.isEmpty())
                foreign.put(entry.getKey(), uids);
        }
        return foreign;
    }

    private Set<String> read(String key) {
        // SharedPreferences' own set must not be modified, so copy it.
        Set<String> stored = preferences.getStringSet(key, null);
        return stored == null ? new HashSet<String>()
                : new HashSet<>(stored);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
