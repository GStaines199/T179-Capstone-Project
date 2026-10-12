package com.atakmap.android.plugintemplate.grid;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Whether leaving a team is a member walking out or the team's own lead.
 *
 * <p>Leaving used to be decided by the current ATAK role, so a lead that had
 * switched to HQ told its own team that "a member left".
 */
public class SearchTeamOwnershipTest {

    private static final String SELF_UID = "ANDROID-self";
    private static final String OWN_TEAM_ID = "TEAM-" + SELF_UID;

    private SearchPartyAssignmentManager manager;

    @Before
    public void setUp() {
        manager = new SearchPartyAssignmentManager(null);
        manager.setSelfIdentity(SELF_UID, "QA-LEAD");
    }

    @Test
    public void teamThisDeviceCreatedIsOwn() {
        manager.createTeam("Bravo", OWN_TEAM_ID);

        assertTrue(manager.isOwnTeam(OWN_TEAM_ID));
    }

    @Test
    public void ownTeamStaysOwnAfterTheRoleChanges() {
        manager.createTeam("Bravo", OWN_TEAM_ID);
        // What the controller does on each refresh once ATAK's role is HQ.
        manager.setSelfRole(false);

        assertTrue(manager.isOwnTeam(OWN_TEAM_ID));
    }

    @Test
    public void joinedTeamIsNotOwnEvenWhenTheRoleIsLead() {
        manager.joinTeam("Alpha", "TEAM-ANDROID-other");
        manager.setSelfRole(true);

        assertFalse(manager.isOwnTeam(OWN_TEAM_ID));
    }

    @Test
    public void noTeamOrNoIdIsNeverOwn() {
        assertFalse(manager.isOwnTeam(OWN_TEAM_ID));

        manager.createTeam("Bravo", OWN_TEAM_ID);
        assertFalse(manager.isOwnTeam(""));
        assertFalse(manager.isOwnTeam(null));

        manager.clearTeam();
        assertFalse(manager.isOwnTeam(OWN_TEAM_ID));
    }
}
