package com.atakmap.android.plugintemplate.grid;

/**
 * Decides whether a synced team-area document should replace this device's
 * planned area.
 *
 * <p>There is one area document per team, written both by HQ assignments
 * and by the lead's own plan. The lead used to apply any document whose
 * {@code id@updatedAt} differed from the last one it handled. Right after
 * publishing, the observer cache still held the older document, so the lead
 * applied the old area over its new plan, then republished that, then
 * applied the plan again: the team's area swapped between two plans every
 * refresh or two until ATAK restarted.
 *
 * <p>Now a document is only applied when it is strictly newer than anything
 * this device has published or applied for the same document id, and a
 * document whose geometry already matches the plan is just recorded.
 */
public final class TeamAreaVersionTracker {

    public enum Decision {
        /** Older than, or the same as, what this device already has. */
        SKIP,
        /** Newer, but the geometry is already the current plan. */
        RECORD_ONLY,
        /** Newer and different: replace the planned area. */
        APPLY
    }

    private String documentId = "";
    private long latestUpdatedAt;

    /** This device wrote the area document itself. */
    public void recordPublished(String assignmentId, long updatedAt) {
        record(assignmentId, updatedAt);
    }

    /** The area document was applied, or found to match the plan. */
    public void recordApplied(String assignmentId, long updatedAt) {
        record(assignmentId, updatedAt);
    }

    public Decision decide(String assignmentId, long updatedAt,
            String documentFingerprint, String currentFingerprint) {
        String id = safe(assignmentId);
        if (id.equals(documentId) && updatedAt <= latestUpdatedAt)
            return Decision.SKIP;
        if (safe(documentFingerprint).equals(safe(currentFingerprint)))
            return Decision.RECORD_ONLY;
        return Decision.APPLY;
    }

    private void record(String assignmentId, long updatedAt) {
        String id = safe(assignmentId);
        if (!id.equals(documentId)) {
            documentId = id;
            latestUpdatedAt = updatedAt;
            return;
        }
        latestUpdatedAt = Math.max(latestUpdatedAt, updatedAt);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
