package com.atakmap.android.plugintemplate.runtime;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A photo (e.g. Quick Pic) attached to a shared marker, as stored in the
 * {@code sartak_shared_marker_images} collection. The image bytes live in a
 * Ditto attachment; the document only carries its token and description.
 */
public class SharedMarkerImage {

    public static final String ATTACHMENT_FIELD = "image";
    /** Larger files are not shared (a phone photo is usually 2-6 MB). */
    public static final long MAX_BYTES = 12L * 1024L * 1024L;

    private final String operationId;
    private final String markerUid;
    private final String fileName;
    private final long size;
    private final String senderUid;
    private final long createdAt;
    private final Map<String, Object> token;

    public SharedMarkerImage(String operationId, String markerUid,
            String fileName, long size, String senderUid, long createdAt,
            Map<String, Object> token) {
        this.operationId = safe(operationId);
        this.markerUid = safe(markerUid);
        this.fileName = safeFileName(fileName);
        this.size = size;
        this.senderUid = safe(senderUid);
        this.createdAt = createdAt;
        this.token = token;
    }

    public static String documentId(String operationId, String markerUid,
            String fileName) {
        return "marker-image-" + safe(operationId) + "-" + safe(markerUid)
                + "-" + safeFileName(fileName);
    }

    /** Only plain image files: no paths, no hidden files. */
    public static boolean isShareableImageName(String name) {
        String lower = name == null ? "" : name.toLowerCase(
                java.util.Locale.US);
        return !lower.startsWith(".") && (lower.endsWith(".jpg")
                || lower.endsWith(".jpeg") || lower.endsWith(".png"));
    }

    public static String safeFileName(String name) {
        String value = name == null ? "" : name.trim();
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        if (slash >= 0)
            value = value.substring(slash + 1);
        value = value.replaceAll("[^A-Za-z0-9._-]", "_");
        while (value.startsWith("."))
            value = value.substring(1);
        return value.length() > 120 ? value.substring(value.length() - 120)
                : value;
    }

    public String getOperationId() { return operationId; }
    public String getMarkerUid() { return markerUid; }
    public String getFileName() { return fileName; }
    public long getSize() { return size; }
    public String getSenderUid() { return senderUid; }
    public long getCreatedAt() { return createdAt; }
    public Map<String, Object> getToken() { return token; }

    public String key() {
        return markerUid + "/" + fileName + "/" + size;
    }

    /** Document fields apart from the attachment itself. */
    public Map<String, Object> toDocument() {
        Map<String, Object> document = new HashMap<>();
        document.put("_id", documentId(operationId, markerUid, fileName));
        document.put("operationId", operationId);
        document.put("markerUid", markerUid);
        document.put("fileName", fileName);
        document.put("size", size);
        document.put("senderUid", senderUid);
        document.put("createdAt", createdAt);
        return document;
    }

    public static SharedMarkerImage fromJson(JSONObject object) {
        Object attachment = object.opt(ATTACHMENT_FIELD);
        Map<String, Object> token = attachment instanceof JSONObject
                ? toMap((JSONObject) attachment) : null;
        return new SharedMarkerImage(object.optString("operationId", ""),
                object.optString("markerUid", ""),
                object.optString("fileName", ""),
                object.optLong("size", 0L),
                object.optString("senderUid", ""),
                object.optLong("createdAt", 0L), token);
    }

    static Map<String, Object> toMap(JSONObject object) {
        Map<String, Object> map = new HashMap<>();
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            map.put(key, plain(object.opt(key)));
        }
        return map;
    }

    private static Object plain(Object value) {
        if (value instanceof JSONObject)
            return toMap((JSONObject) value);
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < array.length(); i++)
                list.add(plain(array.opt(i)));
            return list;
        }
        if (value == JSONObject.NULL)
            return null;
        if (value instanceof Integer)
            return ((Integer) value).longValue();
        return value;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
