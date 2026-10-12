package com.atakmap.android.plugintemplate.runtime;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** The Devices tab's "SARtak map group" for a peer (Task 2 finding T2-5). */
public class DeviceMapGroupTest {

    @Test
    public void peerWithNoAtakGroupShowsItsSartakTeam() {
        assertEquals("Green SARtak team", DeviceConnectivitySnapshot
                .preferredMapGroup("Unassigned", "Green SARtak team"));
        assertEquals("Green SARtak team", DeviceConnectivitySnapshot
                .preferredMapGroup("", "Green SARtak team"));
    }

    @Test
    public void realAtakGroupIsKept() {
        assertEquals("Cyan Team", DeviceConnectivitySnapshot
                .preferredMapGroup("Cyan Team", "Green SARtak team"));
    }

    @Test
    public void unassignedEverywhereStaysUnassigned() {
        assertEquals("Unassigned", DeviceConnectivitySnapshot
                .preferredMapGroup("Unassigned", "Unassigned"));
        assertEquals("Unassigned", DeviceConnectivitySnapshot
                .preferredMapGroup("Unassigned", ""));
        assertEquals("", DeviceConnectivitySnapshot
                .preferredMapGroup(null, null));
    }
}
