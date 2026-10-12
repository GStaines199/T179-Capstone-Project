package com.atakmap.android.plugintemplate.grid;

import com.atakmap.android.plugintemplate.runtime.SearchTeamCotMessage;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/** HQ's Assign Search Area list includes the device's own team. */
public class AssignableTeamsTest {

    private static SearchTeamCotMessage team(String id, String name) {
        return new SearchTeamCotMessage("msg-" + id,
                SearchTeamCotMessage.ACTION_ADVERTISE, id, name, "lead-" + id,
                "Lead " + name, "lead-" + id, "Lead " + name, "", "", 1L);
    }

    @Test
    public void ownTeamIsListedWhenNoOneElseAdvertisesIt() {
        SearchTeamCotMessage bravo = team("TEAM-self", "Bravo");

        List<SearchTeamCotMessage> teams = AssignableTeams.withOwnTeam(
                Collections.<SearchTeamCotMessage>emptyList(), bravo);

        assertEquals(1, teams.size());
        assertSame(bravo, teams.get(0));
    }

    @Test
    public void ownTeamComesFirstBeforeAdvertisedTeams() {
        SearchTeamCotMessage alpha = team("TEAM-a", "Alpha");
        SearchTeamCotMessage bravo = team("TEAM-self", "Bravo");

        List<SearchTeamCotMessage> teams = AssignableTeams.withOwnTeam(
                Arrays.asList(alpha), bravo);

        assertEquals(2, teams.size());
        assertSame(bravo, teams.get(0));
        assertSame(alpha, teams.get(1));
    }

    @Test
    public void ownTeamAlreadyAdvertisedIsNotListedTwice() {
        SearchTeamCotMessage advertised = team("TEAM-self", "Bravo");

        List<SearchTeamCotMessage> teams = AssignableTeams.withOwnTeam(
                Arrays.asList(advertised), team("TEAM-self", "Bravo"));

        assertEquals(1, teams.size());
        assertSame(advertised, teams.get(0));
    }

    @Test
    public void withoutATeamOnlyAdvertisedTeamsAreListed() {
        SearchTeamCotMessage alpha = team("TEAM-a", "Alpha");

        assertEquals(1, AssignableTeams.withOwnTeam(Arrays.asList(alpha),
                null).size());
        assertEquals(0, AssignableTeams.withOwnTeam(null, null).size());
    }
}
