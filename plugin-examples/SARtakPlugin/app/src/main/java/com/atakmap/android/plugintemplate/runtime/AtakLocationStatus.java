package com.atakmap.android.plugintemplate.runtime;

import com.atakmap.android.maps.MapData;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.Locale;

public class AtakLocationStatus {

    private static final String KEY_LOCATION_AVAILABLE = "LocationAvailable";
    private static final String KEY_LOCATION_TIME = "LocationTime";
    private static final String KEY_LOCATION_SOURCE = "LocationSrc";
    private static final String KEY_EFFECTIVE_PREFIX =
            "locationSourceEffectivePrefix";
    private static final String KEY_SOURCE_PREFIX = "locationSourcePrefix";
    private static final String KEY_GPS_ISSUE = "device.gps.issue";
    private static final long MIN_REASONABLE_EPOCH_MS = 946684800000L;

    private AtakLocationStatus() {
    }

    public static Snapshot from(MapView mapView) {
        if (mapView == null)
            return Snapshot.unavailable("GPS unavailable; no ATAK map");

        Marker self = mapView.getSelfMarker();
        if (self == null || self.getPoint() == null
                || !self.getPoint().isValid())
            return Snapshot.unavailable(
                    "GPS unavailable; no valid ATAK self marker");

        MapData data = mapView.getMapData();
        if (hasGpsIssue(data))
            return Snapshot.unavailable("No GPS Signal");

        String prefix = getSourcePrefix(data);
        Availability availability = getAvailability(data, prefix);
        if (availability.known && !availability.available) {
            return Snapshot.unavailable("No GPS Signal");
        }

        long fixTimestamp = getLocationTimestamp(data, prefix);
        long now = System.currentTimeMillis();
        if (fixTimestamp > MIN_REASONABLE_EPOCH_MS
                && now - fixTimestamp > PluginHealthManager.LOCATION_STALE_MS) {
            long ageSeconds = Math.max(0L, (now - fixTimestamp) / 1000L);
            return Snapshot.unavailable("GPS stale; ATAK fix is "
                    + ageSeconds + " seconds old");
        }

        GeoPoint point = self.getPoint();
        String source = getLocationSource(data, prefix, self);
        if (isUnknownLocationSource(source) || !hasUsableAccuracy(point))
            return Snapshot.unavailable("No GPS Signal");
        if (fixTimestamp <= MIN_REASONABLE_EPOCH_MS)
            fixTimestamp = now;

        return Snapshot.available(point, fixTimestamp, source);
    }

    private static Availability getAvailability(MapData data, String prefix) {
        if (data == null)
            return new Availability(false, true);

        // ATAK maintains a global location availability flag as well as
        // source-specific flags. When GPS is disabled after a valid fix, a
        // source-specific value can briefly remain stale, so an explicit global
        // "not available" always wins.
        if (data.containsKey(KEY_LOCATION_AVAILABLE)) {
            boolean available = data.getBoolean(KEY_LOCATION_AVAILABLE, false);
            if (!available)
                return new Availability(true, false);
        }

        Availability internal = getAvailabilityIfPresent(data,
                "internalLocationAvailable");
        Availability fine = getAvailabilityIfPresent(data,
                "fineLocationAvailable");
        if (internal.known && !internal.available)
            return internal;
        if (fine.known && !fine.available)
            return fine;

        String prefixedKey = prefixed(prefix, KEY_LOCATION_AVAILABLE);
        if (prefixedKey != null && data.containsKey(prefixedKey))
            return new Availability(true, data.getBoolean(prefixedKey, false));
        if (data.containsKey(KEY_LOCATION_AVAILABLE))
            return new Availability(true, true);

        return new Availability(false, true);
    }

    private static Availability getAvailabilityIfPresent(MapData data,
            String key) {
        if (!data.containsKey(key))
            return new Availability(false, true);
        return new Availability(true, data.getBoolean(key, false));
    }

    private static long getLocationTimestamp(MapData data, String prefix) {
        if (data == null)
            return 0L;

        String prefixedKey = prefixed(prefix, KEY_LOCATION_TIME);
        if (prefixedKey != null && data.containsKey(prefixedKey))
            return data.getLong(prefixedKey, 0L);
        if (data.containsKey(KEY_LOCATION_TIME))
            return data.getLong(KEY_LOCATION_TIME, 0L);

        long fineTime = getLongIfPresent(data, "fineLocationTime");
        if (fineTime > 0L)
            return fineTime;
        long internalTime = getLongIfPresent(data, "internalLocationTime");
        if (internalTime > 0L)
            return internalTime;
        return 0L;
    }

    private static String getLocationSource(MapData data, String prefix,
            Marker self) {
        String source = null;
        if (data != null) {
            String prefixedKey = prefixed(prefix, KEY_LOCATION_SOURCE);
            if (prefixedKey != null && data.containsKey(prefixedKey))
                source = data.getString(prefixedKey, null);
            if ((source == null || source.length() == 0)
                    && data.containsKey(KEY_LOCATION_SOURCE))
                source = data.getString(KEY_LOCATION_SOURCE, null);
        }

        if (source == null || source.length() == 0) {
            GeoPointMetaData metaData = self.getGeoPointMetaData();
            if (metaData != null)
                source = metaData.getGeopointSource();
        }
        return source == null || source.length() == 0 ? "ATAK" : source;
    }

    private static String getSourcePrefix(MapData data) {
        if (data == null)
            return null;
        String prefix = data.getString(KEY_EFFECTIVE_PREFIX, null);
        if (prefix == null || prefix.length() == 0)
            prefix = data.getString(KEY_SOURCE_PREFIX, null);
        return prefix;
    }

    private static long getLongIfPresent(MapData data, String key) {
        return data.containsKey(key) ? data.getLong(key, 0L) : 0L;
    }

    private static boolean hasGpsIssue(MapData data) {
        if (data == null || !data.containsKey(KEY_GPS_ISSUE))
            return false;
        try {
            return data.getBoolean(KEY_GPS_ISSUE, false);
        } catch (Exception ignored) {
            Object value = data.get(KEY_GPS_ISSUE);
            return value != null && Boolean.parseBoolean(value.toString());
        }
    }

    private static String prefixed(String prefix, String suffix) {
        if (prefix == null || prefix.length() == 0)
            return null;
        return prefix + suffix;
    }

    private static boolean isUnknownLocationSource(String source) {
        if (source == null)
            return true;
        String normalised = source.trim().toLowerCase(Locale.US);
        return normalised.length() == 0
                || "???".equals(normalised)
                || "unknown".equals(normalised)
                || "atak".equals(normalised);
    }

    private static boolean hasUsableAccuracy(GeoPoint point) {
        if (point == null)
            return false;
        double accuracy = point.getCE();
        return !Double.isNaN(accuracy)
                && !Double.isInfinite(accuracy)
                && accuracy > 0.0;
    }

    private static class Availability {
        final boolean known;
        final boolean available;

        Availability(boolean known, boolean available) {
            this.known = known;
            this.available = available;
        }
    }

    public static class Snapshot {
        private final boolean available;
        private final GeoPoint point;
        private final long timestamp;
        private final String source;
        private final String message;

        private Snapshot(boolean available, GeoPoint point, long timestamp,
                String source, String message) {
            this.available = available;
            this.point = point;
            this.timestamp = timestamp;
            this.source = source;
            this.message = message;
        }

        static Snapshot available(GeoPoint point, long timestamp,
                String source) {
            return new Snapshot(true, point, timestamp, source, "");
        }

        static Snapshot unavailable(String message) {
            return new Snapshot(false, null, 0L, "", message);
        }

        public boolean isAvailable() {
            return available;
        }

        public GeoPoint getPoint() {
            return point;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getSource() {
            return source;
        }

        public String getMessage() {
            return message;
        }

        public String describeAccuracy() {
            if (point == null)
                return "unknown";
            return String.format(Locale.US, "%.0f m", point.getCE());
        }
    }
}
