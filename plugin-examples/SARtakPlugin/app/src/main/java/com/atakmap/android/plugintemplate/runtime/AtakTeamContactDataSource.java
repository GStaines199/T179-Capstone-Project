package com.atakmap.android.plugintemplate.runtime;

import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.contact.IndividualContact;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads connected peer/contact records already known to ATAK.
 *
 * This does not create a separate SARtak network. ATAK remains responsible for
 * receiving CoT/peer data through its configured TAK server or peer links.
 * SARtak reads ATAK's Contacts model first, then falls back to only strict ATAK
 * user-position markers when ATAK has drawn a peer on the map but has not
 * exposed it through Contacts yet.
 */
public class AtakTeamContactDataSource {

    public static class ContactSnapshot {
        private final String uid;
        private final String callsign;
        private final GeoPoint point;
        private final double headingDegrees;
        private final boolean headingReliable;
        private final double speedMetersPerSecond;
        private final long timestamp;
        private final String role;
        private final String atakGroupName;

        ContactSnapshot(String uid, String callsign, GeoPoint point,
                double headingDegrees, boolean headingReliable,
                double speedMetersPerSecond, long timestamp, String role,
                String atakGroupName) {
            this.uid = uid;
            this.callsign = callsign;
            this.point = point;
            this.headingDegrees = headingDegrees;
            this.headingReliable = headingReliable;
            this.speedMetersPerSecond = speedMetersPerSecond;
            this.timestamp = timestamp;
            this.role = role;
            this.atakGroupName = atakGroupName;
        }

        public String getUid() {
            return uid;
        }

        public String getCallsign() {
            return callsign;
        }

        public GeoPoint getPoint() {
            return point;
        }

        public double getHeadingDegrees() {
            return headingDegrees;
        }

        public boolean isHeadingReliable() {
            return headingReliable;
        }

        public double getSpeedMetersPerSecond() {
            return speedMetersPerSecond;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getRole() {
            return role;
        }

        public String getAtakGroupName() {
            return atakGroupName;
        }

        public boolean isTeamLead() {
            String normalized = role == null ? "" : role.toLowerCase();
            return normalized.contains("lead")
                    || normalized.contains("leader");
        }

        public String getDisplayLabel() {
            return callsign + " - " + role + "\nATAK group: "
                    + atakGroupName + "\n" + uid;
        }
    }

    private final MapView mapView;
    private int lastCandidateCount;
    private int lastContactModelCount;
    private int lastUserMarkerCount;

    public AtakTeamContactDataSource(MapView mapView) {
        this.mapView = mapView;
    }

    public List<ContactSnapshot> getContacts() {
        Map<String, ContactSnapshot> contacts = new LinkedHashMap<>();
        lastCandidateCount = 0;
        lastContactModelCount = 0;
        lastUserMarkerCount = 0;
        collectAtakContacts(contacts);
        collectAtakUserMarkers(contacts);
        return new ArrayList<>(contacts.values());
    }

    private void collectAtakContacts(Map<String, ContactSnapshot> contacts) {
        try {
            List<Contact> atakContacts = Contacts.getInstance()
                    .getAllContacts();
            if (atakContacts == null)
                return;
            String selfUid = MapView.getDeviceUid();
            for (Contact contact : atakContacts) {
                if (!(contact instanceof IndividualContact))
                    continue;
                IndividualContact individual = (IndividualContact) contact;
                String uid = safe(individual.getUID());
                if (uid.length() == 0 || uid.equals(selfUid))
                    continue;
                String callsign = safe(individual.getName());
                if (callsign.length() == 0)
                    callsign = uid;
                if (looksLikeGeneratedId(callsign)
                        || looksLikePseudoContact(uid, callsign))
                    continue;

                Marker marker = null;
                MapItem mapItem = individual.getMapItem();
                if (mapItem instanceof Marker
                        && !isSartakItem(mapItem)) {
                    marker = (Marker) mapItem;
                }
                GeoPoint point = marker == null ? null : marker.getPoint();
                if (point != null && !point.isValid())
                    point = null;

                String role = marker == null ? "ATAK contact"
                        : safe(firstNonEmpty(
                                safeMetaString(marker, "atakRoleType", ""),
                                safeMetaString(marker, "teamRole",
                                        "ATAK contact")));
                lastCandidateCount++;
                lastContactModelCount++;
                contacts.put(uid, new ContactSnapshot(uid, callsign, point,
                        marker == null ? 0.0 : getHeading(marker),
                        marker != null && hasHeading(marker),
                        marker == null ? 0.0 : getSpeed(marker),
                        marker == null ? 0L : getTimestamp(marker), role,
                        getAtakGroupName(marker)));
            }
        } catch (Exception ignored) {
            // ATAK may not expose its Contacts singleton during early plugin
            // startup. In that case SARtak reports no contacts rather than
            // scanning unrelated map markers and presenting false devices.
        }
    }

    private void collectAtakUserMarkers(Map<String, ContactSnapshot> contacts) {
        if (mapView == null || mapView.getRootGroup() == null)
            return;
        MapGroup rootGroup = mapView.getRootGroup();
        Collection<MapItem> items = rootGroup.getItemsRecursive();
        if (items == null)
            return;

        String selfUid = MapView.getDeviceUid();
        for (MapItem item : items) {
            if (!(item instanceof Marker) || isSartakItem(item))
                continue;
            Marker marker = (Marker) item;
            String uid = safe(marker.getUID());
            if (uid.length() == 0 || uid.equals(selfUid)
                    || contacts.containsKey(uid)
                    || looksLikePseudoContact(uid, marker.getTitle()))
                continue;
            if (!isAtakUserPosition(marker))
                continue;

            GeoPoint point = marker.getPoint();
            if (point == null || !point.isValid())
                continue;
            String callsign = getMarkerCallsign(marker, uid);
            String role = safe(firstNonEmpty(
                    safeMetaString(marker, "atakRoleType", ""),
                    safeMetaString(marker, "teamRole", "ATAK user marker")));
            lastCandidateCount++;
            lastUserMarkerCount++;
            contacts.put(uid, new ContactSnapshot(uid, callsign, point,
                    getHeading(marker), hasHeading(marker), getSpeed(marker),
                    getTimestamp(marker), role,
                    getAtakGroupName(marker)));
        }
    }

    public String describeLastScan(int matchedRosterMembers) {
        if (lastCandidateCount == 0)
            return "No ATAK server/peer contacts visible yet";
        return lastCandidateCount + " ATAK peer(s) visible ("
                + lastContactModelCount + " contact model, "
                + lastUserMarkerCount + " map user marker), "
                + matchedRosterMembers + " matched to this SARtak team";
    }

    private boolean isSartakItem(MapItem item) {
        String uid = safe(item.getUID());
        return uid.startsWith("sartak-")
                || "sartak".equals(safeMetaString(item, "entry", ""));
    }

    private boolean looksLikeGeneratedId(String value) {
        String normalized = safe(value);
        if (normalized.length() >= 32 && normalized.indexOf('-') >= 0)
            return true;
        return normalized.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-"
                + "[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                || normalized.startsWith("sartak-team-")
                || normalized.startsWith("ANDROID-");
    }

    private boolean isAtakUserPosition(Marker marker) {
        String type = safe(marker.getType());
        return type.equals("a-f-G-U-C")
                || type.startsWith("a-f-G-U-C-")
                || type.equals("a-f-G-U-C-I")
                || type.startsWith("a-f-G-U-C-I-");
    }

    private String getMarkerCallsign(Marker marker, String uid) {
        String callsign = safe(safeMetaString(marker, "callsign",
                marker.getTitle()));
        if (callsign.length() == 0 || looksLikeGeneratedId(callsign))
            callsign = uid;
        return callsign;
    }

    private String getAtakGroupName(Marker marker) {
        if (marker == null)
            return "Ungrouped ATAK";
        String group = firstNonEmpty(
                safeMetaString(marker, "__groupName", ""),
                safeMetaString(marker, "team", ""),
                safeMetaString(marker, "atakTeam", ""),
                safeMetaString(marker, "teamColor", ""),
                safeMetaString(marker, "groupName", ""),
                safeMetaString(marker, "locationTeam", ""),
                safeMetaString(marker, "__group", ""));
        if (group.length() == 0)
            return "Ungrouped ATAK";
        return group.toLowerCase().endsWith("team") ? group : group + " Team";
    }

    private boolean looksLikePseudoContact(String uid, String callsign) {
        String normalizedUid = safe(uid).toLowerCase();
        String normalizedCallsign = safe(callsign).toLowerCase();
        return normalizedUid.contains("server")
                || normalizedCallsign.contains("server")
                || normalizedCallsign.contains("chat room")
                || normalizedCallsign.contains("chatroom")
                || normalizedCallsign.contains("all chat")
                || normalizedCallsign.equals("atak contacts")
                || normalizedCallsign.equals("contacts");
    }

    private double getHeading(Marker marker) {
        double heading = marker.getMetaDouble("trackHeading", Double.NaN);
        if (Double.isNaN(heading))
            heading = marker.getMetaDouble("course", Double.NaN);
        if (Double.isNaN(heading))
            heading = marker.getMetaDouble("heading", 0.0);
        return heading;
    }

    private boolean hasHeading(Marker marker) {
        return marker != null
                && (marker.hasMetaValue("trackHeading")
                        || marker.hasMetaValue("course")
                        || marker.hasMetaValue("heading"));
    }

    private double getSpeed(Marker marker) {
        double speed = marker.getMetaDouble("speed", Double.NaN);
        if (Double.isNaN(speed))
            speed = marker.getMetaDouble("groundSpeed", Double.NaN);
        if (Double.isNaN(speed))
            speed = marker.getMetaDouble("trackSpeed", Double.NaN);
        if (Double.isNaN(speed))
            speed = marker.getMetaDouble("est.speed", 0.0);
        return Math.max(0.0, speed);
    }

    private long getTimestamp(Marker marker) {
        long timestamp = marker.getMetaLong("lastUpdateTime", 0L);
        if (timestamp <= 0L)
            timestamp = marker.getMetaLong("locationTime", 0L);
        if (timestamp <= 0L)
            timestamp = marker.getMetaLong("timestamp", 0L);
        return timestamp > 0L ? timestamp : System.currentTimeMillis();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeMetaString(MapItem item, String key, String fallback) {
        if (item == null || key == null)
            return safe(fallback);
        try {
            return safe(item.getMetaString(key, fallback));
        } catch (ClassCastException ignored) {
            // Some ATAK metadata keys, notably teamColor on certain contact
            // markers, are stored as numeric values. MapItem#getMetaString
            // throws instead of converting, so treat that key as unavailable
            // and continue scanning rather than crashing ATAK.
            return safe(fallback);
        } catch (Exception ignored) {
            return safe(fallback);
        }
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            String safe = safe(value);
            if (safe.length() > 0)
                return safe;
        }
        return "";
    }
}
