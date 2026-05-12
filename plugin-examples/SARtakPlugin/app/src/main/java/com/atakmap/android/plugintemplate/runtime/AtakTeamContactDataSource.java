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
 * Reads team/contact-like map markers already known to ATAK.
 *
 * This does not create a separate SARtak network. ATAK remains responsible for
 * receiving CoT/peer data. SARtak only scans the map model and copies matching
 * contact positions into the SARtak team panel and optional SAR overlay marker.
 */
public class AtakTeamContactDataSource {

    public static class ContactSnapshot {
        private final String uid;
        private final String callsign;
        private final GeoPoint point;
        private final double headingDegrees;
        private final long timestamp;
        private final String role;

        ContactSnapshot(String uid, String callsign, GeoPoint point,
                double headingDegrees, long timestamp, String role) {
            this.uid = uid;
            this.callsign = callsign;
            this.point = point;
            this.headingDegrees = headingDegrees;
            this.timestamp = timestamp;
            this.role = role;
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

        public long getTimestamp() {
            return timestamp;
        }

        public String getRole() {
            return role;
        }

        public boolean isTeamLead() {
            String normalized = role == null ? "" : role.toLowerCase();
            return normalized.contains("lead")
                    || normalized.contains("leader");
        }

        public String getDisplayLabel() {
            return callsign + " - " + role + "\n" + uid;
        }
    }

    private final MapView mapView;
    private int lastCandidateCount;
    private int lastIgnoredMarkerCount;

    public AtakTeamContactDataSource(MapView mapView) {
        this.mapView = mapView;
    }

    public List<ContactSnapshot> getContacts() {
        Map<String, ContactSnapshot> contacts = new LinkedHashMap<>();
        lastCandidateCount = 0;
        lastIgnoredMarkerCount = 0;
        collectAtakContacts(contacts);
        collectMapContactMarkers(contacts);
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
                if (looksLikeGeneratedId(callsign))
                    continue;

                MapItem mapItem = individual.getMapItem();
                if (!(mapItem instanceof Marker))
                    continue;
                Marker marker = (Marker) mapItem;
                if (isSartakItem(marker))
                    continue;
                GeoPoint point = marker.getPoint();
                if (point == null || !point.isValid())
                    continue;

                String role = safe(marker.getMetaString("atakRoleType",
                        marker.getMetaString("teamRole", "ATAK contact")));
                lastCandidateCount++;
                contacts.put(uid, new ContactSnapshot(uid, callsign, point,
                        getHeading(marker), getTimestamp(marker), role));
            }
        } catch (Exception ignored) {
            // ATAK contacts are the preferred source, but some startup states do
            // not expose the contact list yet. Marker scanning below remains a
            // conservative fallback.
        }
    }

    private void collectMapContactMarkers(Map<String, ContactSnapshot> contacts) {
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
            if (uid.length() == 0 || uid.equals(selfUid))
                continue;

            GeoPoint point = marker.getPoint();
            if (point == null || !point.isValid())
                continue;

            String callsign = safe(marker.getMetaString("callsign",
                    marker.getTitle()));
            if (callsign.length() == 0)
                callsign = uid;
            String role = safe(marker.getMetaString("atakRoleType",
                    marker.getMetaString("teamRole", "ATAK contact")));

            if (!looksLikeContact(marker, callsign))
                continue;

            lastCandidateCount++;
            if (!contacts.containsKey(uid))
                contacts.put(uid, new ContactSnapshot(uid, callsign, point,
                        getHeading(marker), getTimestamp(marker), role));
        }
    }

    public String describeLastScan(int matchedRosterMembers) {
        if (lastCandidateCount == 0)
            return lastIgnoredMarkerCount == 0
                    ? "No ATAK peer/contact markers visible yet"
                    : "No ATAK peer/contact markers visible yet (ignored "
                            + lastIgnoredMarkerCount
                            + " non-contact map markers)";
        return lastCandidateCount + " ATAK contact marker(s) visible, "
                + matchedRosterMembers + " matched to this SARtak team";
    }

    private boolean isSartakItem(MapItem item) {
        String uid = safe(item.getUID());
        return uid.startsWith("sartak-")
                || "sartak".equals(item.getMetaString("entry", ""));
    }

    private boolean looksLikeContact(Marker marker, String callsign) {
        String type = safe(marker.getType());
        boolean hasAtakRole = marker.hasMetaValue("atakRoleType")
                || safe(marker.getMetaString("atakRoleType", "")).length() > 0;
        boolean hasContactUpdate = marker.hasMetaValue("lastUpdateTime")
                || marker.hasMetaValue("locationTime")
                || marker.hasMetaValue("trackHeading")
                || marker.hasMetaValue("course");
        boolean isAtakContactType = type.startsWith("a-");

        // ATAK's own contact/user list sample keys off atakRoleType. Keep this
        // as the strongest signal, but some server/emulator contact markers do
        // not expose that metadata to plugins. In that case, accept ATAK user
        // CoT types only when the title/callsign looks human-readable.
        if (callsign.length() > 0 && hasAtakRole
                && (isAtakContactType || hasContactUpdate))
            return true;
        if (callsign.length() > 0 && isAtakContactType
                && !looksLikeGeneratedId(callsign)
                && !callsign.equals(marker.getUID()))
            return true;

        lastIgnoredMarkerCount++;
        return false;
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

    private double getHeading(Marker marker) {
        double heading = marker.getMetaDouble("trackHeading", Double.NaN);
        if (Double.isNaN(heading))
            heading = marker.getMetaDouble("course", Double.NaN);
        if (Double.isNaN(heading))
            heading = marker.getMetaDouble("heading", 0.0);
        return heading;
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
}
