package com.atakmap.android.plugintemplate.grid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.UTMPoint;

public class SearchPartyAssignmentManager {

    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_BLUE = 0xFF4AA3FF;
    private static final int COLOR_GREEN = 0xFF42C36A;
    private static final int COLOR_YELLOW = 0xFFD8B64C;
    private static final int COLOR_RED = 0xFFD8544C;
    private static final int COLOR_PURPLE = 0xFFB47CFF;

    private final String teamId = "TEAM-ALPHA-001";
    private final String teamName = "Team Alpha";
    private final String selfMemberId = "TL-A-001";
    private final List<SearchTeamMember> members = new ArrayList<>();
    private int mockMemberCounter = 5;

    public SearchPartyAssignmentManager() {
        members.add(new SearchTeamMember("TL-A-001", "Alpha Lead",
                SearchTeamMember.TeamRole.TEAM_LEADER, "White", COLOR_WHITE,
                SearchTeamMember.MembershipStatus.ACTIVE_MEMBER,
                SearchTeamMember.ConnectionStatus.CONNECTED, 1,
                -27.4705, 153.0260,
                "-27.4705, 153.0260", "42 m", "Current", "8 sec ago",
                "You", "On line"));
        members.add(new SearchTeamMember("A-S01", "Searcher 01",
                SearchTeamMember.TeamRole.SEARCHER, "Blue", COLOR_BLUE,
                SearchTeamMember.MembershipStatus.ACTIVE_MEMBER,
                SearchTeamMember.ConnectionStatus.CONNECTED, 1,
                -27.4706, 153.0258,
                "-27.4706, 153.0258", "41 m", "Lane cell", "12 sec ago",
                "18 m left", "4 m left"));
        members.add(new SearchTeamMember("A-S02", "Searcher 02",
                SearchTeamMember.TeamRole.SEARCHER, "Green", COLOR_GREEN,
                SearchTeamMember.MembershipStatus.ACTIVE_MEMBER,
                SearchTeamMember.ConnectionStatus.CONNECTED, 2,
                -27.4704, 153.0261,
                "-27.4704, 153.0261", "43 m", "Lane cell", "14 sec ago",
                "22 m right", "2 m right"));
        members.add(new SearchTeamMember("A-S03", "Searcher 03",
                SearchTeamMember.TeamRole.SEARCHER, "Yellow", COLOR_YELLOW,
                SearchTeamMember.MembershipStatus.ACTIVE_MEMBER,
                SearchTeamMember.ConnectionStatus.RECONNECTING, 3,
                -27.4707, 153.0262,
                "-27.4707, 153.0262", "40 m", "Lane cell", "3 min ago",
                "35 m north", "9 m left"));
        members.add(new SearchTeamMember("A-S04", "Searcher 04",
                SearchTeamMember.TeamRole.SEARCHER, "Red", COLOR_RED,
                SearchTeamMember.MembershipStatus.ACTIVE_MEMBER,
                SearchTeamMember.ConnectionStatus.CONNECTED, 4,
                -27.4703, 153.0259,
                "-27.4703, 153.0259", "44 m", "Lane cell", "10 sec ago",
                "28 m south", "1 m right"));
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
        members.add(new SearchTeamMember("A-S" + number, "Searcher " + number,
                SearchTeamMember.TeamRole.SEARCHER, "Purple", COLOR_PURPLE,
                SearchTeamMember.MembershipStatus.ACTIVE_MEMBER,
                SearchTeamMember.ConnectionStatus.CONNECTED,
                getLaneMemberCount() + 1,
                -27.4705 + (mockMemberCounter * 0.0001),
                153.0260 + (mockMemberCounter * 0.0001),
                "Pending GPS", "Pending", "Unassigned", "new", "Pending",
                "Pending"));
        mockMemberCounter++;
    }

    public void removeLastLaneMember() {
        for (int i = members.size() - 1; i >= 0; i--) {
            SearchTeamMember member = members.get(i);
            if (member.contributesLane()) {
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
            leader.updatePosition(leaderPoint.getLatitude(),
                    leaderPoint.getLongitude(), formatGeo(leaderPoint),
                    cell.getId(), "You", "Leader line");
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

            double easting = cell.getWest() + laneWidth * (laneIndex + 0.5);
            double offset = searchLineOffset(variationCursor++);
            double northing = clamp(lineNorthing + offset, cell.getSouth(),
                    cell.getNorth());
            GeoPoint point = converter.toGeoPoint(cell.getZoneDescriptor(),
                    easting, northing);
            member.updatePosition(point.getLatitude(), point.getLongitude(),
                    formatGeo(point), cell.getId(),
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
