package com.atakmap.android.plugintemplate.grid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
                SearchTeamMember.ConnectionStatus.CONNECTED, 0,
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
        int count = 0;
        for (SearchTeamMember member : members) {
            if (member.contributesLane())
                count++;
        }
        return Math.max(1, count);
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
            if (!member.isTeamLeader())
                builder.append(" | Lane: ")
                        .append(member.contributesLane() ? "assigned"
                                : "excluded");
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
}
