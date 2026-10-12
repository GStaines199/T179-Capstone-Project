package com.atakmap.android.plugintemplate.grid;

import com.atakmap.android.plugintemplate.runtime.SearchTeamCotMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * The teams HQ can assign a search area to.
 *
 * <p>Teams are found from advertisements, and a device never sees its own.
 * So an HQ device that also leads a team (a lead that switched its ATAK role
 * to HQ) got an empty list and could not assign that team an area.
 */
public final class AssignableTeams {

    private AssignableTeams() {
    }

    /**
     * @param advertised teams other devices advertise
     * @param ownTeam    this device's own team, or null when it has none
     * @return the advertised teams, plus the own team first when it isn't
     *         already among them
     */
    public static List<SearchTeamCotMessage> withOwnTeam(
            List<SearchTeamCotMessage> advertised,
            SearchTeamCotMessage ownTeam) {
        List<SearchTeamCotMessage> teams = new ArrayList<>();
        boolean ownListed = false;
        String ownId = ownTeam == null ? "" : safe(ownTeam.getTeamId());
        if (advertised != null) {
            for (SearchTeamCotMessage team : advertised) {
                if (team == null)
                    continue;
                if (ownId.length() > 0 && ownId.equals(safe(team.getTeamId())))
                    ownListed = true;
                teams.add(team);
            }
        }
        if (ownId.length() > 0 && !ownListed)
            teams.add(0, ownTeam);
        return teams;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
