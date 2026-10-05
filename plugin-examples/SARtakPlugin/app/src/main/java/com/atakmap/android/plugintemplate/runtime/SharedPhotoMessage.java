package com.atakmap.android.plugintemplate.runtime;

/**
 * One photo attached to a shared marker, as published to the operation.
 *
 * <p>The image bytes travel as a Ditto attachment on the same document; this
 * class only carries the metadata a receiver needs to decide whether it
 * already has the file and where to put it.
 */
public class SharedPhotoMessage {

    private final String documentId;
    private final String operationId;
    private final String markerUid;
    private final String fileName;
    private final long sizeBytes;
    private final String senderUid;
    private final String senderCallsign;
    private final String teamId;
    private final long capturedAt;
    private final long updatedAt;

    public SharedPhotoMessage(String documentId, String operationId,
            String markerUid, String fileName, long sizeBytes,
            String senderUid, String senderCallsign, String teamId,
            long capturedAt, long updatedAt) {
        this.documentId = safe(documentId);
        this.operationId = safe(operationId);
        this.markerUid = safe(markerUid);
        this.fileName = safe(fileName);
        this.sizeBytes = sizeBytes;
        this.senderUid = safe(senderUid);
        this.senderCallsign = safe(senderCallsign);
        this.teamId = safe(teamId);
        this.capturedAt = capturedAt;
        this.updatedAt = updatedAt;
    }

    public String getDocumentId() { return documentId; }
    public String getOperationId() { return operationId; }
    public String getMarkerUid() { return markerUid; }
    public String getFileName() { return fileName; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSenderUid() { return senderUid; }
    public String getSenderCallsign() { return senderCallsign; }
    public String getTeamId() { return teamId; }
    public long getCapturedAt() { return capturedAt; }
    public long getUpdatedAt() { return updatedAt; }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
