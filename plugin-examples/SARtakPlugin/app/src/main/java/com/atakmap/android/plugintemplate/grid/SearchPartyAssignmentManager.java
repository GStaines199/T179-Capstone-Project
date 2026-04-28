package com.atakmap.android.plugintemplate.grid;

public class SearchPartyAssignmentManager {

    private static final int MIN_TEAM_SIZE = 1;
    private static final int MAX_TEAM_SIZE = 30;

    private int teamSize = 10;

    public int getTeamSize() {
        return teamSize;
    }

    public void increaseTeamSize() {
        if (teamSize < MAX_TEAM_SIZE)
            teamSize++;
    }

    public void decreaseTeamSize() {
        if (teamSize > MIN_TEAM_SIZE)
            teamSize--;
    }

    public String describeAssignments(SearchGridCell cell) {
        if (cell == null)
            return "No cell selected";
        return cell.getId() + " split into " + teamSize + " lanes";
    }
}
