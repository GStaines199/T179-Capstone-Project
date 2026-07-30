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

        GeoPoint point = self.getPoint();
        Evaluation evaluation = evaluate(wrap(mapView.getMapData()),
                point.getCE(), markerSource(self), System.currentTimeMillis());
        if (!evaluation.isAvailable())
            return Snapshot.unavailable(evaluation.getMessage());
        return Snapshot.available(point, evaluation.getTimestamp(),
                evaluation.getSource());
    }

    /**
     * Decides whether ATAK is reporting a usable GPS fix. Kept free of ATAK
     * types so the decision logic can be unit tested on a plain JVM; the ATAK
     * plumbing lives in {@link #from(MapView)}.
     *
     * @param data          ATAK map data, or {@link #EMPTY} when unavailable
     * @param accuracy      circular error of the self marker, in metres
     * @param markerSource  location source reported by the self marker
     * @param now           current wall clock time
     */
    static Evaluation evaluate(MetaSource data, double accuracy,
            String markerSource, long now) {
        if (hasGpsIssue(data))
            return Evaluation.unavailable("No GPS Signal");

        String prefix = getSourcePrefix(data);
        Availability availability = getAvailability(data, prefix);
        if (availability.known && !availability.available) {
            return Evaluation.unavailable("No GPS Signal");
        }

        long fixTimestamp = getLocationTimestamp(data, prefix);
        if (fixTimestamp > MIN_REASONABLE_EPOCH_MS
                && now - fixTimestamp > PluginHealthManager.LOCATION_STALE_MS) {
            long ageSeconds = Math.max(0L, (now - fixTimestamp) / 1000L);
            return Evaluation.unavailable("GPS stale; ATAK fix is "
                    + ageSeconds + " seconds old");
        }

        String source = getLocationSource(data, prefix, markerSource);
        if (isUnknownLocationSource(source) || !hasUsableAccuracy(accuracy))
            return Evaluation.unavailable("No GPS Signal");
        if (fixTimestamp <= MIN_REASONABLE_EPOCH_MS)
            fixTimestamp = now;

        return Evaluation.available(fixTimestamp, source);
    }

    private static Availability getAvailability(MetaSource data, String prefix) {
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

    private static Availability getAvailabilityIfPresent(MetaSource data,
            String key) {
        if (!data.containsKey(key))
            return new Availability(false, true);
        return new Availability(true, data.getBoolean(key, false));
    }

    private static long getLocationTimestamp(MetaSource data, String prefix) {
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

    private static String getLocationSource(MetaSource data, String prefix,
            String markerSource) {
        String source = null;
        String prefixedKey = prefixed(prefix, KEY_LOCATION_SOURCE);
        if (prefixedKey != null && data.containsKey(prefixedKey))
            source = data.getString(prefixedKey, null);
        if ((source == null || source.length() == 0)
                && data.containsKey(KEY_LOCATION_SOURCE))
            source = data.getString(KEY_LOCATION_SOURCE, null);

        if (source == null || source.length() == 0)
            source = markerSource;
        return source == null || source.length() == 0 ? "ATAK" : source;
    }

    private static String getSourcePrefix(MetaSource data) {
        String prefix = data.getString(KEY_EFFECTIVE_PREFIX, null);
        if (prefix == null || prefix.length() == 0)
            prefix = data.getString(KEY_SOURCE_PREFIX, null);
        return prefix;
    }

    private static long getLongIfPresent(MetaSource data, String key) {
        return data.containsKey(key) ? data.getLong(key, 0L) : 0L;
    }

    private static boolean hasGpsIssue(MetaSource data) {
        if (!data.containsKey(KEY_GPS_ISSUE))
            return false;
        try {
            return data.getBoolean(KEY_GPS_ISSUE, false);
        } catch (Exception ignored) {
            Object value = data.getRaw(KEY_GPS_ISSUE);
            return value != null && Boolean.parseBoolean(value.toString());
        }
    }

    private static String prefixed(String prefix, String suffix) {
        if (prefix == null || prefix.length() == 0)
            return null;
        return prefix + suffix;
    }

    private static String markerSource(Marker self) {
        GeoPointMetaData metaData = self.getGeoPointMetaData();
        return metaData == null ? null : metaData.getGeopointSource();
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

    private static boolean hasUsableAccuracy(double accuracy) {
        return !Double.isNaN(accuracy)
                && !Double.isInfinite(accuracy)
                && accuracy > 0.0;
    }

    /**
     * Read-only view of the key/value pairs ATAK publishes in
     * {@link MapData}, so the GPS checks do not depend on the ATAK runtime.
     */
    interface MetaSource {
        boolean containsKey(String key);

        boolean getBoolean(String key, boolean defaultValue);

        long getLong(String key, long defaultValue);

        String getString(String key, String defaultValue);

        /** Raw value, used when a key holds an unexpected type. */
        Object getRaw(String key);
    }

    /** Behaves like an ATAK map with no location keys published. */
    static final MetaSource EMPTY = new MetaSource() {
        @Override
        public boolean containsKey(String key) {
            return false;
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            return defaultValue;
        }

        @Override
        public long getLong(String key, long defaultValue) {
            return defaultValue;
        }

        @Override
        public String getString(String key, String defaultValue) {
            return defaultValue;
        }

        @Override
        public Object getRaw(String key) {
            return null;
        }
    };

    private static MetaSource wrap(final MapData data) {
        if (data == null)
            return EMPTY;
        return new MetaSource() {
            @Override
            public boolean containsKey(String key) {
                return data.containsKey(key);
            }

            @Override
            public boolean getBoolean(String key, boolean defaultValue) {
                return data.getBoolean(key, defaultValue);
            }

            @Override
            public long getLong(String key, long defaultValue) {
                return data.getLong(key, defaultValue);
            }

            @Override
            public String getString(String key, String defaultValue) {
                return data.getString(key, defaultValue);
            }

            @Override
            public Object getRaw(String key) {
                return data.get(key);
            }
        };
    }

    private static class Availability {
        final boolean known;
        final boolean available;

        Availability(boolean known, boolean available) {
            this.known = known;
            this.available = available;
        }
    }

    /** Outcome of {@link #evaluate}, without any ATAK types attached. */
    static class Evaluation {
        private final boolean available;
        private final long timestamp;
        private final String source;
        private final String message;

        private Evaluation(boolean available, long timestamp, String source,
                String message) {
            this.available = available;
            this.timestamp = timestamp;
            this.source = source;
            this.message = message;
        }

        static Evaluation available(long timestamp, String source) {
            return new Evaluation(true, timestamp, source, "");
        }

        static Evaluation unavailable(String message) {
            return new Evaluation(false, 0L, "", message);
        }

        boolean isAvailable() {
            return available;
        }

        long getTimestamp() {
            return timestamp;
        }

        String getSource() {
            return source;
        }

        String getMessage() {
            return message;
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
