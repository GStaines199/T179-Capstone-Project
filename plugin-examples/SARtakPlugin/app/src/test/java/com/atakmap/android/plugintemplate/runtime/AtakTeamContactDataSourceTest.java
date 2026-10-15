package com.atakmap.android.plugintemplate.runtime;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class AtakTeamContactDataSourceTest {

    @Test
    public void dittoEnrichesSparseAtakContactWithLeaderAndTeam() throws Exception {
        AtakTeamContactDataSource.ContactSnapshot atak =
                new AtakTeamContactDataSource.ContactSnapshot(
                        "LEAD-UID", "Architect", null, 0.0, false,
                        0.0, 100L, "ATAK contact", "Unassigned");
        DittoDeviceSnapshot ditto = DittoDeviceSnapshot.fromJson("{"
                + "\"uid\":\"LEAD-UID\","
                + "\"callsign\":\"Architect\","
                + "\"teamId\":\"TEAM-ALPHA\","
                + "\"teamName\":\"Alpha\","
                + "\"teamCreated\":true,"
                + "\"leaderUid\":\"LEAD-UID\","
                + "\"role\":\"Team Lead\","
                + "\"teamColorName\":\"Purple\","
                + "\"updatedAt\":200}");

        AtakTeamContactDataSource.ContactSnapshot merged =
                AtakTeamContactDataSource.ContactSnapshot.mergeWithDitto(
                        atak, ditto);

        assertEquals("Team Lead", merged.getRole());
        assertEquals("Purple SARtak team", merged.getAtakGroupName());
        assertTrue(merged.getDisplayLabel().contains(
                "Team/group: Purple SARtak team"));
    }

    @Test
    public void dittoPreservesHqRoleInsteadOfCallingItTeamMember()
            throws Exception {
        DittoDeviceSnapshot ditto = DittoDeviceSnapshot.fromJson("{"
                + "\"uid\":\"HQ-UID\","
                + "\"callsign\":\"Matisse\","
                + "\"role\":\"HQ\","
                + "\"updatedAt\":200}");

        AtakTeamContactDataSource.ContactSnapshot contact =
                AtakTeamContactDataSource.ContactSnapshot.fromDitto(ditto);

        assertEquals("HQ", contact.getRole());
        assertEquals("Unassigned", contact.getAtakGroupName());
    }
}
