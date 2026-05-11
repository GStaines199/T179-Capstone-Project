package com.atakmap.android.plugintemplate.grid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.atakmap.android.plugintemplate.runtime.AtakLocationStatus;
import com.atakmap.android.plugintemplate.runtime.AtakTeamContactDataSource;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.UTMPoint;

public class SearchPartyAssignmentManager {

    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_BLUE = 0xFF4AA3FF;
    private static final int COLOR_GREEN = 0xFF42C36A;
    private static final int COLOR_YELLOW = 0xFFD8B64C;
    private static final int COLOR_RED = 0xFFD8544C;
    private static final int COLOR_PURPLE = 0xFFB47CFF;
    private static final int[] SEARCHER_COLORS = new int[] {
            COLOR_BLUE, COLOR_GREEN, COLOR_YELLOW, COLOR_RED, COLOR_PURPLE
    };
    private static final String[] SEARCHER_COLOR_NAMES = new String[] {
            "Blue", "Green", "Yellow", "Red", "Purple"
    };

    private String teamId = "TEAM-ALPHA-001";
    private String teamName = "Team Alpha";
    private String selfMemberId = "TL-A-001";
    private final List<SearchTeamMember> members = new ArrayList<>();
    private int mockMemberCounter = 1;
    private int lastAtakMatchedMembers;

    public SearchPartyAssignmentManager() {
        members.add(new SearchTeamMember("TL-A-001", "Alpha Lead",
                SearchTeamMember.TeamRole.TEAM_LEADER, "White", COLOR_WHITE,
                SearchTeamMember.MembershipStatus.ACTIVE_MEMBER,
                SearchTeamMember.ConnectionStatus.CONNECTED, 1,
                -27.4705, 153.0260, 0.0,
                "No GPS Signal", "No GPS Signal", "No GPS Signal",
                "No GPS Signal", "No GPS Signal", "No GPS Signal"));
    }

    public String getTeamId() {
        return teamId;
    }

    public String getTeamName() {
        return teamName;
    }

    public String getSelfMemberId() {
        return selfMemberId;
    }

    public int getLastAtakMatchedMembers() {
        return lastAtakMatchedMembers;
    }

    public List<SearchTeamMember> getVisibleMembers() {
        List<SearchTeamMember> visibleMembers = new ArrayList<>();
        for (SearchTeamMember member : members) {
            if (member.getMembershipStatus()
                    != SearchTeamMember.MembershipStatus.REMOVED)
                visibleMembers.add(member);
        }
        return Collections.unmodifiableList(visibleMembers);
    }

    public List<SearchTeamMember> getLaneMembers() {
        List<SearchTeamMember> laneMembers = new ArrayList<>();
        for (SearchTeamMember member : members) {
            if (member.contributesLane())
                laneMembers.add(member);
        }
        return laneMembers;
    }

    public SearchTeamMember findMemberById(String uniqueId) {
        for (SearchTeamMember member : members) {
            if (member.getUniqueId().equals(uniqueId)
                    && member.getMembershipStatus()
                    != SearchTeamMember.MembershipStatus.REMOVED)
                return member;
        }
        return null;
    }

    public int getTeamSize() {
        return getLaneMemberCount();
    }

    public int getLaneMemberCount() {
        return Math.max(1, getLaneMembers().size());
    }

    public void addMockMember() {
        String number = mockMemberCounter < 10 ? "0" + mockMemberCounter
                : String.valueOf(mockMemberCounter);
        addTeamMember("A-S" + number, "Searcher " + number);
        mockMemberCounter++;
    }

    public void setTeamDetails(String teamName, String teamId) {
        if (teamName != null && teamName.trim().length() > 0)
            this.teamName = teamName.trim();
        if (teamId != null && teamId.trim().length() > 0)
            this.teamId = teamId.trim();
    }

    public void setSelfIdentity(String uid, String callsign) {
        if (uid == null || uid.trim().length() == 0)
            return;
        String trimmedUid = uid.trim();
        String trimmedCallsign = callsign == null || callsign.trim().length() == 0
                ? "Team Leader" : callsign.trim();
        if (trimmedUid.equals(selfMemberId)) {
            return;
        }

        SearchTeamMember existingSelf = findMemberById(selfMemberId);
        if (existingSelf != null)
            existingSelf.setMembershipStatus(
                    SearchTeamMember.MembershipStatus.REMOVED);
        selfMemberId = trimmedUid;
        SearchTeamMember self = findAnyMemberById(trimmedUid);
        if (self == null) {
            members.add(new SearchTeamMember(trimmedUid, trimmedCallsign,
                    SearchTeamMember.TeamRole.TEAM_LEADER, "White",
                    COLOR_WHITE,
                    SearchTeamMember.MembershipStatus.ACTIVE_MEMBER,
                    SearchTeamMember.ConnectionStatus.STALE, 1,
                    -27.4705, 153.0260, 0.0,
                    "No GPS Signal", "No GPS Signal", "No GPS Signal",
                    "No GPS Signal", "No GPS Signal", "No GPS Signal"));
        } else {
            self.setMembershipStatus(
                    SearchTeamMember.MembershipStatus.ACTIVE_MEMBER);
        }
    }

    public SearchTeamMember addTeamMember(String uniqueId, String callsign) {
        String uid = uniqueId == null ? "" : uniqueId.trim();
        if (uid.length() == 0)
            return null;

        SearchTeamMember existing = findAnyMemberById(uid);
        if (existing != null) {
            existing.setMembershipStatus(
                    SearchTeamMember.MembershipStatus.ACTIVE_MEMBER);
            if (!existing.isTeamLeader())
                existing.setConnectionStatus(
                        SearchTeamMember.ConnectionStatus.RECONNECTING);
            return existing;
        }

        int colorIndex = Math.max(0, getLaneMemberCount() - 1)
                % SEARCHER_COLORS.length;
        String label = callsign == null || callsign.trim().length() == 0
                ? uid : callsign.trim();
        SearchTeamMember member = new SearchTeamMember(uid, label,
                SearchTeamMember.TeamRole.SEARCHER,
                SEARCHER_COLOR_NAMES[colorIndex], SEARCHER_COLORS[colorIndex],
                SearchTeamMember.MembershipStatus.ACTIVE_MEMBER,
                SearchTeamMember.ConnectionStatus.RECONNECTING,
                getLaneMemberCount() + 1, -27.4705, 153.0260, 0.0,
                "Awaiting ATAK peer", "Awaiting ATAK peer", "Unassigned",
                "Awaiting ATAK peer", "Awaiting ATAK peer",
                "Awaiting ATAK peer");
        members.add(member);
        return member;
    }

    public boolean removeTeamMember(String uniqueId) {
        SearchTeamMember member = findMemberById(uniqueId);
        if (member == null || member.getUniqueId().equals(selfMemberId))
            return false;
        member.setMembershipStatus(SearchTeamMember.MembershipStatus.REMOVED);
        return true;
    }

    public void updateSelfFromAtak(AtakLocationStatus.Snapshot snapshot,
            SearchGridCell selectedCell) {
        SearchTeamMember self = findMemberById(selfMemberId);
        if (self == null)
            return;

        if (snapshot == null || !snapshot.isAvailable()) {
            self.markLocationUnavailable("No GPS Signal");
            self.setConnectionStatus(SearchTeamMember.ConnectionStatus.STALE);
            return;
        }

        GeoPoint point = snapshot.getPoint();
        self.setConnectionStatus(SearchTeamMember.ConnectionStatus.CONNECTED);
        self.updatePosition(point.getLatitude(), point.getLongitude(), 0.0,
                formatGeo(point), formatAltitude(point),
                selectedCell == null ? "No cell selected" : selectedCell.getId(),
                formatLastPing(snapshot.getTimestamp()), "You",
                self.getDistanceFromSearchLine());
    }

    public void updateFromAtakContacts(
            List<AtakTeamContactDataSource.ContactSnapshot> contacts,
            GeoPoint selfPoint, GridCoordinateConverter converter) {
        lastAtakMatchedMembers = 0;
        for (SearchTeamMember member : getVisibleMembers()) {
            if (member.getUniqueId().equals(selfMemberId))
                continue;

            AtakTeamContactDataSource.ContactSnapshot contact = findContact(
                    member, contacts);
            if (contact == null) {
                member.setLiveAtakContact(false);
                if (member.getConnectionStatus()
                        == SearchTeamMember.ConnectionStatus.CONNECTED)
                    member.setConnectionStatus(
                            SearchTeamMember.ConnectionStatus.STALE);
                continue;
            }

            lastAtakMatchedMembers++;
            GeoPoint point = contact.getPoint();
            member.setConnectionStatus(
                    SearchTeamMember.ConnectionStatus.CONNECTED);
            member.updatePosition(point.getLatitude(), point.getLongitude(),
                    contact.getHeadingDegrees(), formatGeo(point),
                    formatAltitude(point), converter.cellIdForPoint(point),
                    formatLastPing(contact.getTimestamp()),
                    formatDistanceFromSelf(selfPoint, point),
                    member.getDistanceFromSearchLine());
        }
    }

    public void removeLastLaneMember() {
        for (int i = members.size() - 1; i >= 0; i--) {
            SearchTeamMember member = members.get(i);
            if (member.contributesLane()
                    && !member.getUniqueId().equals(selfMemberId)) {
                member.setMembershipStatus(
                        SearchTeamMember.MembershipStatus.REMOVED);
                return;
            }
        }
    }

    public void arrangeMembersForCell(SearchGridCell cell,
            GridCoordinateConverter converter, GeoPoint leaderPoint,
            double lineNorthing) {
        if (cell == null || leaderPoint == null || !leaderPoint.isValid())
            return;

        List<SearchTeamMember> laneMembers = getLaneMembers();
        int laneCount = Math.max(1, laneMembers.size());
        double laneWidth = (cell.getEast() - cell.getWest()) / laneCount;
        UTMPoint leaderUtm = UTMPoint.fromGeoPoint(leaderPoint);
        int leaderLaneIndex = clamp((int) Math.floor((leaderUtm.getEasting()
                - cell.getWest()) / laneWidth), 0, laneCount - 1);

        SearchTeamMember leader = findMemberById(selfMemberId);
        if (leader != null && leader.contributesLane()) {
            leader.setLaneNumber(leaderLaneIndex + 1);
            leader.updateMapPosition(leaderPoint.getLatitude(),
                    leaderPoint.getLongitude(), 0.0, cell.getId(), "You",
                    "Leader line");
        }

        List<Integer> availableLaneIndexes = new ArrayList<>();
        for (int i = 0; i < laneCount; i++) {
            if (i != leaderLaneIndex)
                availableLaneIndexes.add(i);
        }

        int laneCursor = 0;
        int variationCursor = 0;
        for (SearchTeamMember member : laneMembers) {
            if (member.getUniqueId().equals(selfMemberId))
                continue;

            int laneIndex = laneCursor < availableLaneIndexes.size()
                    ? availableLaneIndexes.get(laneCursor)
                    : laneCursor;
            laneCursor++;
            member.setLaneNumber(laneIndex + 1);
            if (member.hasLiveAtakContact())
                continue;

            double easting = cell.getWest() + laneWidth * (laneIndex + 0.5);
            double offset = searchLineOffset(variationCursor++);
            double northing = clamp(lineNorthing + offset, cell.getSouth(),
                    cell.getNorth());
            GeoPoint point = converter.toGeoPoint(cell.getZoneDescriptor(),
                    easting, northing);
            member.updateMapPosition(point.getLatitude(), point.getLongitude(),
                    0.0, cell.getId(),
                    formatDistance(distance(leaderUtm, UTMPoint
                            .fromGeoPoint(point))),
                    formatLineOffset(northing - lineNorthing));
        }
    }

    public String describeAssignments(SearchGridCell cell) {
        if (cell == null)
            return "No cell selected";
        return cell.getId() + " split into " + getLaneMemberCount()
                + " assigned lanes";
    }

    public String describeTeamRoster() {
        StringBuilder builder = new StringBuilder();
        builder.append(teamName).append(" | ").append(teamId).append("\n");
        for (SearchTeamMember member : members) {
            if (member.getMembershipStatus()
                    == SearchTeamMember.MembershipStatus.REMOVED)
                continue;
            builder.append(member.isTeamLeader() ? "Leader: " : "- ");
            builder.append(member.getCallsign())
                    .append(" (")
                    .append(member.getColorName())
                    .append(") ")
                    .append(member.getConnectionStatus());
            builder.append(" | ")
                    .append(member.getLaneLabel());
            builder.append("\nGPS: ")
                    .append(member.getGpsCoordinates())
                    .append(" | Alt: ")
                    .append(member.getAltitude())
                    .append("\nGrid: ")
                    .append(member.getCurrentGridCell())
                    .append(" | Last ping: ")
                    .append(member.getLastPing())
                    .append("\nDistance: ")
                    .append(member.getDistanceFromYou())
                    .append(" | Line: ")
                    .append(member.getDistanceFromSearchLine())
                    .append("\n");
        }
        return builder.toString().trim();
    }

    private SearchTeamMember findAnyMemberById(String uniqueId) {
        if (uniqueId == null)
            return null;
        for (SearchTeamMember member : members) {
            if (member.getUniqueId().equals(uniqueId))
                return member;
        }
        return null;
    }

    private AtakTeamContactDataSource.ContactSnapshot findContact(
            SearchTeamMember member,
            List<AtakTeamContactDataSource.ContactSnapshot> contacts) {
        if (contacts == null)
            return null;
        for (AtakTeamContactDataSource.ContactSnapshot contact : contacts) {
            if (member.getUniqueId().equals(contact.getUid())
                    || member.getCallsign().equalsIgnoreCase(
                            contact.getCallsign()))
                return contact;
        }
        return null;
    }

    private String formatDistanceFromSelf(GeoPoint selfPoint,
            GeoPoint contactPoint) {
        if (selfPoint == null || contactPoint == null
                || !selfPoint.isValid() || !contactPoint.isValid())
            return "Self GPS unavailable";
        return formatDistance(distance(UTMPoint.fromGeoPoint(selfPoint),
                UTMPoint.fromGeoPoint(contactPoint)));
    }

    public String describeConnectionAlerts() {
        StringBuilder builder = new StringBuilder();
        for (SearchTeamMember member : members) {
            if (member.needsConnectionAlert()) {
                builder.append("ALERT: ")
                        .append(member.getCallsign())
                        .append(" ")
                        .append(member.getConnectionStatus())
                        .append(". Lane remains assigned. Reconnect pending.")
                        .append("\n");
            }
        }
        if (builder.length() == 0)
            return "No team connection alerts";
        return builder.toString().trim();
    }

    private double searchLineOffset(int index) {
        double[] offsets = new double[] { -6.0, 4.0, 11.0, -3.0, 7.0,
                -9.0, 13.0, 2.0 };
        return offsets[index % offsets.length];
    }

    private String formatGeo(GeoPoint point) {
        return String.format(Locale.US, "%.6f, %.6f", point.getLatitude(),
                point.getLongitude());
    }

    private String formatAltitude(GeoPoint point) {
        if (point == null || !point.isAltitudeValid())
            return "No ATAK altitude";
        return Math.round(point.getAltitude()) + " m";
    }

    private String formatLastPing(long timestamp) {
        if (timestamp <= 0L)
            return "ATAK GPS now";
        long seconds = Math.max(0L, (System.currentTimeMillis() - timestamp)
                / 1000L);
        return seconds <= 1L ? "ATAK GPS now" : seconds + " sec ago";
    }

    private String formatDistance(double meters) {
        return Math.round(meters) + " m";
    }

    private String formatLineOffset(double meters) {
        long rounded = Math.round(Math.abs(meters));
        if (rounded <= 2)
            return "On line";
        return rounded + " m " + (meters > 0 ? "ahead" : "behind");
    }

    private double distance(UTMPoint first, UTMPoint second) {
        double dx = first.getEasting() - second.getEasting();
        double dy = first.getNorthing() - second.getNorthing();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
