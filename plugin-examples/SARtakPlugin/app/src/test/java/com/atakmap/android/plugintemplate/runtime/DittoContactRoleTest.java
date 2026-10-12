package com.atakmap.android.plugintemplate.runtime;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;

/** The role shown for a Ditto peer in the "Add connected member" list. */
@RunWith(RobolectricTestRunner.class)
public class DittoContactRoleTest {

    private static String roleOf(boolean teamCreated, String leaderUid,
            String role) throws Exception {
        JSONObject json = new JSONObject()
                .put("uid", "member-uid")
                .put("callsign", "QA-MEMBER")
                .put("operationId", "op")
                .put("teamCreated", teamCreated)
                .put("leaderUid", leaderUid)
                .put("role", role)
                .put("hasLocation", false)
                .put("updatedAt", 1L);
        return AtakTeamContactDataSource.ContactSnapshot.fromDitto(
                DittoDeviceSnapshot.fromJson(json.toString())).getRole();
    }

    @Test
    public void memberOutsideATeam_isNotLabelledTeamLead() throws Exception {
        // Out of a team, SARtak reports the device as its own leader.
        assertEquals("Team Member", roleOf(false, "member-uid", "Team Member"));
    }

    @Test
    public void leadOutsideATeam_isNotLabelledTeamLead() throws Exception {
        assertEquals("Team Member", roleOf(false, "member-uid", "Team Lead"));
    }

    @Test
    public void leaderOfItsTeam_isTeamLead() throws Exception {
        assertEquals("Team Lead", roleOf(true, "member-uid", "Team Member"));
        assertEquals("Team Lead", roleOf(true, "lead-uid", "Team Lead"));
    }

    @Test
    public void memberOfSomeoneElsesTeam_isTeamMember() throws Exception {
        assertEquals("Team Member", roleOf(true, "lead-uid", "Team Member"));
    }
}
