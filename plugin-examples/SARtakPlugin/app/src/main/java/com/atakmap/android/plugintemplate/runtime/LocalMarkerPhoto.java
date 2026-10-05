package com.atakmap.android.plugintemplate.runtime;

/**
 * A photo file sitting in a marker's ATAK attachment folder on this device.
 */
public class LocalMarkerPhoto {

    private final String markerUid;
    private final String fileName;
    private final String path;
    private final long sizeBytes;
    private final long lastModified;

    public LocalMarkerPhoto(String markerUid, String fileName, String path,
            long sizeBytes, long lastModified) {
        this.markerUid = markerUid == null ? "" : markerUid.trim();
        this.fileName = fileName == null ? "" : fileName;
        this.path = path == null ? "" : path;
        this.sizeBytes = sizeBytes;
        this.lastModified = lastModified;
    }

    public String getMarkerUid() { return markerUid; }
    public String getFileName() { return fileName; }
    public String getPath() { return path; }
    public long getSizeBytes() { return sizeBytes; }
    public long getLastModified() { return lastModified; }
}
