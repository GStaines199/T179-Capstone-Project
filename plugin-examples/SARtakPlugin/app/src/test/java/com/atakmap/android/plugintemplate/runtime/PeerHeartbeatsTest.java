package com.atakmap.android.plugintemplate.runtime;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Which peers count as heard from, for the Ditto status and readiness. */
public class PeerHeartbeatsTest {

    private static final long NOW = 1_791_000_000_000L;

    @Test
    public void documentAlreadyInTheStore_isNotHeard() {
        PeerHeartbeats heartbeats = new PeerHeartbeats();

        heartbeats.observe("lead", NOW - 120_000L, NOW);

        assertEquals(0, heartbeats.freshCount(NOW));
        assertEquals(0L, heartbeats.lastHeard());
    }

    @Test
    public void advancingState_isHeard() {
        PeerHeartbeats heartbeats = new PeerHeartbeats();

        heartbeats.observe("lead", NOW - 5_000L, NOW - 5_000L);
        heartbeats.observe("lead", NOW, NOW);

        assertEquals(1, heartbeats.freshCount(NOW));
        assertEquals(NOW, heartbeats.lastHeard());
    }

    @Test
    public void redeliveredUnchangedState_doesNotRefresh() {
        PeerHeartbeats heartbeats = new PeerHeartbeats();
        heartbeats.observe("lead", NOW - 5_000L, NOW - 5_000L);
        heartbeats.observe("lead", NOW, NOW);

        // Our own writes keep re-delivering the lead's last document.
        for (long t = NOW + 1_000L; t <= NOW + 60_000L; t += 1_000L)
            heartbeats.observe("lead", NOW, t);

        assertEquals(0, heartbeats.freshCount(NOW + 60_000L));
        assertEquals(NOW, heartbeats.lastHeard());
    }

    @Test
    public void peerGoesStaleAfterThirtySeconds() {
        PeerHeartbeats heartbeats = new PeerHeartbeats();
        heartbeats.observe("lead", 1L, NOW);
        heartbeats.observe("lead", 2L, NOW);

        assertEquals(1, heartbeats.freshCount(NOW + PeerHeartbeats.FRESH_MS));
        assertEquals(0, heartbeats.freshCount(
                NOW + PeerHeartbeats.FRESH_MS + 1L));
    }

    @Test
    public void countsEachFreshPeerOnce_andClearForgetsThem() {
        PeerHeartbeats heartbeats = new PeerHeartbeats();
        for (String uid : new String[] {"a", "b", "c"}) {
            heartbeats.observe(uid, 1L, NOW);
            heartbeats.observe(uid, 2L, NOW);
            heartbeats.observe(uid, 3L, NOW);
        }

        assertEquals(3, heartbeats.freshCount(NOW));

        heartbeats.clear();
        heartbeats.observe("a", 4L, NOW);
        assertEquals(0, heartbeats.freshCount(NOW));
    }
}
