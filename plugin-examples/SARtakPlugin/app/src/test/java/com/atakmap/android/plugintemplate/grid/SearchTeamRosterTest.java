package com.atakmap.android.plugintemplate.grid;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Roster honesty tests for SearchPartyAssignmentManager.
 *
 * The plugin once shipped a seeded demo member -- "TL-A-001 / Alpha Lead" at
 * -27.4705, 153.0260, which is QUT Gardens Point -- created in the manager
 * constructor, and setSelfIdentity() gave every new self member the same
 * coordinates. The labels around those numbers said "No GPS Signal", but code
 * downstream reads the numbers rather than the labels, so a member with no fix
 * could still be placed on a map at a real place in Brisbane.
 *
 * These tests pin the rule that replaced it: the roster starts empty, the
 * plugin claims no identity until ATAK gives it one, and a member without a
 * fix is never handed a position that could be mistaken for a real one.
 *
 * These tests require only JUnit -- no ATAK SDK, no Robolectric.
 *
 * Dependencies (build.gradle):
 *   testImplementation 'junit:junit:4.13.2'
 */
public class SearchTeamRosterTest {

    /** The coordinates the plugin used to seed. Never valid as a default. */
    private static final double DEMO_LATITUDE = -27.4705;
    private static final double DEMO_LONGITUDE = 153.0260;

    private static final String SELF_UID = "ATAK-UID-9f3c";
    private static final String SELF_CALLSIGN = "Rescue 1";

    private SearchPartyAssignmentManager manager;

    @Before
    public void setUp() {
        manager = new SearchPartyAssignmentManager(null);
    }

    // ---- A fresh manager invents nothing --------------------------------

    /**
     * The tripwire. getVisibleMembers() returns an empty list whenever no team
     * exists, so it can hide a seeded member rather than prove there is none.
     * Forcing teamCreated first is what makes this test able to fail.
     */
    @Test
    public void newManager_seedsNoMembers() {
        manager.setTeamCreated(true);

        assertEquals("a new roster must be empty, not seeded",
                0, manager.getVisibleMembers().size());
    }

    @Test
    public void newManager_claimsNoSelfIdentity() {
        assertEquals("", manager.getSelfMemberId());
        assertNull(manager.getSelfMember());
    }

    @Test
    public void newManager_claimsNoLeader() {
        assertEquals("", manager.getLeaderUid());
        assertEquals("", manager.getLeaderCallsign());
    }

    /** The team name shown before real data loads must not name a fake team. */
    @Test
    public void newManager_hasNoTeamName() {
        assertEquals("", manager.getTeamName());
        assertEquals("", manager.getTeamId());
        assertFalse(manager.isTeamCreated());
    }

    // ---- Adopting a real ATAK identity does not fabricate a position -----

    @Test
    public void setSelfIdentity_adoptsTheUidAtakGave() {
        manager.setSelfIdentity(SELF_UID, SELF_CALLSIGN);

        assertEquals(SELF_UID, manager.getSelfMemberId());
        SearchTeamMember self = manager.getSelfMember();
        assertNotNull("self member should exist once identity resolves", self);
        assertEquals(SELF_CALLSIGN, self.getCallsign());
    }

    @Test
    public void setSelfIdentity_createsSelfWithNoLiveAtakContact() {
        manager.setSelfIdentity(SELF_UID, SELF_CALLSIGN);

        assertFalse("a self member with no fix must not claim a live contact",
                manager.getSelfMember().hasLiveAtakContact());
    }

    @Test
    public void setSelfIdentity_doesNotFabricateAPosition() {
        manager.setSelfIdentity(SELF_UID, SELF_CALLSIGN);
        SearchTeamMember self = manager.getSelfMember();

        assertNotEquals("self must not be seeded at the old demo latitude",
                DEMO_LATITUDE, self.getLatitude(), 1e-9);
        assertNotEquals("self must not be seeded at the old demo longitude",
                DEMO_LONGITUDE, self.getLongitude(), 1e-9);
        assertEquals("self must hold the documented no-position placeholder",
                0.0, self.getLatitude(), 1e-9);
        assertEquals("self must hold the documented no-position placeholder",
                0.0, self.getLongitude(), 1e-9);
    }

    /** The labels and the numbers must tell the same story. */
    @Test
    public void setSelfIdentity_reportsEveryPositionFieldAsUnavailable() {
        manager.setSelfIdentity(SELF_UID, SELF_CALLSIGN);
        SearchTeamMember self = manager.getSelfMember();

        assertEquals("No GPS Signal", self.getGpsCoordinates());
        assertEquals("No GPS Signal", self.getAltitude());
        assertEquals("No GPS Signal", self.getCurrentGridCell());
        assertEquals("No GPS Signal", self.getDistanceFromYou());
        assertEquals("No GPS Signal", self.getDistanceFromSearchLine());
    }

    // ---- The demo member stays gone -------------------------------------

    /**
     * Named for the bug rather than for the method, so that re-seeding a demo
     * member in any constructor or setter fails here with an obvious reason.
     *
     * Checked before identity resolves as well as after. That ordering is the
     * whole point: setSelfIdentity() retires the previous self, so a seeded
     * member is invisible by the time identity arrives and a post-identity
     * assertion alone would pass while the seed was still in the constructor.
     */
    @Test
    public void roster_neverContainsTheRemovedDemoMember() {
        manager.setTeamCreated(true);
        assertNoDemoMemberIn(manager.getVisibleMembers());

        manager.setSelfIdentity(SELF_UID, SELF_CALLSIGN);
        assertNoDemoMemberIn(manager.getVisibleMembers());
    }

    private void assertNoDemoMemberIn(List<SearchTeamMember> members) {
        for (SearchTeamMember member : members) {
            assertNotEquals("the seeded demo member is back",
                    "TL-A-001", member.getUniqueId());
            assertNotEquals("the seeded demo member is back",
                    "Alpha Lead", member.getCallsign());
            assertNotEquals("the seeded demo latitude is back",
                    DEMO_LATITUDE, member.getLatitude(), 1e-9);
            assertNotEquals("the seeded demo longitude is back",
                    DEMO_LONGITUDE, member.getLongitude(), 1e-9);
        }
    }

    @Test
    public void roster_holdsOnlyTheSelfMemberAfterIdentityResolves() {
        manager.setTeamCreated(true);
        manager.setSelfIdentity(SELF_UID, SELF_CALLSIGN);

        List<SearchTeamMember> visible = manager.getVisibleMembers();
        assertEquals(1, visible.size());
        assertEquals(SELF_UID, visible.get(0).getUniqueId());
    }

    /** No member may be placed on a map before ATAK has reported a fix. */
    @Test
    public void roster_hasNoMemberClaimingAPositionBeforeAnyFixArrives() {
        manager.setTeamCreated(true);
        manager.setSelfIdentity(SELF_UID, SELF_CALLSIGN);

        for (SearchTeamMember member : manager.getVisibleMembers()) {
            assertFalse("no member may claim a live contact before a fix",
                    member.hasLiveAtakContact());
        }
    }

    // ---- Identity edge cases --------------------------------------------

    @Test
    public void setSelfIdentity_ignoresAnEmptyUidRatherThanInventingOne() {
        manager.setSelfIdentity("   ", SELF_CALLSIGN);

        assertEquals("", manager.getSelfMemberId());
        assertNull(manager.getSelfMember());
    }

    @Test
    public void setSelfIdentity_isIdempotentForTheSameUid() {
        manager.setTeamCreated(true);
        manager.setSelfIdentity(SELF_UID, SELF_CALLSIGN);
        manager.setSelfIdentity(SELF_UID, SELF_CALLSIGN);

        assertEquals("re-resolving the same identity must not duplicate self",
                1, manager.getVisibleMembers().size());
    }

    @Test
    public void setSelfIdentity_retiresThePreviousSelfWhenTheUidChanges() {
        manager.setTeamCreated(true);
        manager.setSelfIdentity(SELF_UID, SELF_CALLSIGN);
        manager.setSelfIdentity("ATAK-UID-other", "Rescue 2");

        assertEquals("ATAK-UID-other", manager.getSelfMemberId());
        for (SearchTeamMember member : manager.getVisibleMembers()) {
            assertNotEquals("the old self should have been retired",
                    SELF_UID, member.getUniqueId());
        }
    }
}
