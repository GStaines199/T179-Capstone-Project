package com.atakmap.android.plugintemplate.grid;

import java.util.ArrayList;
import java.util.List;

public class SearchPartyAssignmentManager {

    private final List<SearchTeamMember> members = new ArrayList<>();
    private int mockMemberCounter = 5;

    public SearchPartyAssignmentManager() {
        members.add(new SearchTeamMember("TL-A-001", "Alpha Lead", true,
                "White", SearchTeamMember.MembershipStatus.ACTIVE_MEMBER,
                SearchTeamMember.ConnectionStatus.CONNECTED,
                "-27.4705, 153.0260", "42 m", "Current", "8 sec ago"));
        members.add(new SearchTeamMember("A-S01", "Searcher 01", false,
                "Blue", SearchTeamMember.MembershipStatus.ACTIVE_MEMBER,
                SearchTeamMember.ConnectionStatus.CONNECTED,
                "-27.4706, 153.0258", "41 m", "Lane cell", "12 sec ago"));
        members.add(new SearchTeamMember("A-S02", "Searcher 02", false,
                "Green", SearchTeamMember.MembershipStatus.ACTIVE_MEMBER,
                SearchTeamMember.ConnectionStatus.CONNECTED,
                "-27.4704, 153.0261", "43 m", "Lane cell", "14 sec ago"));
        members.add(new SearchTeamMember("A-S03", "Searcher 03", false,
                "Yellow", SearchTeamMember.MembershipStatus.ACTIVE_MEMBER,
                SearchTeamMember.ConnectionStatus.RECONNECTING,
                "-27.4707, 153.0262", "40 m", "Lane cell", "3 min ago"));
        members.add(new SearchTeamMember("A-S04", "Searcher 04", false,
                "Red", SearchTeamMember.MembershipStatus.ACTIVE_MEMBER,
                SearchTeamMember.ConnectionStatus.CONNECTED,
                "-27.4703, 153.0259", "44 m", "Lane cell", "10 sec ago"));
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
        members.add(new SearchTeamMember("A-S" + number,
                "Searcher " + number, false, "Purple",
                SearchTeamMember.MembershipStatus.ACTIVE_MEMBER,
                SearchTeamMember.ConnectionStatus.CONNECTED,
                "Pending GPS", "Pending", "Unassigned", "new"));
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
        builder.append("Team Alpha\n");
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
