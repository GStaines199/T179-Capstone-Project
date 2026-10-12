package com.atakmap.android.plugintemplate.runtime;

import java.util.HashMap;
import java.util.Map;

/**
 * When each other device's published state last moved forward, by this
 * device's clock.
 *
 * <p>Every device republishes its state every few seconds, so a peer whose
 * {@code updatedAt} keeps advancing is one we are actually hearing from. A
 * document that is merely still in the local store is not: Ditto re-delivers
 * the whole collection whenever anything changes, including this device's own
 * writes, which kept "received 2 sec ago" and a ready Ditto line on screen
 * after the network had been gone for a minute.
 */
final class PeerHeartbeats {

    /** A peer not heard from for this long no longer counts as connected. */
    static final long FRESH_MS = 30000L;

    private final Map<String, Long> lastUpdatedAt = new HashMap<>();
    private final Map<String, Long> heardAt = new HashMap<>();
    private long lastHeard;

    /**
     * Records one delivered state document. The first sighting of a peer only
     * sets its baseline, since it may be an old document from the store.
     */
    synchronized void observe(String uid, long updatedAt, long now) {
        Long previous = lastUpdatedAt.put(uid, updatedAt);
        if (previous != null && updatedAt > previous) {
            heardAt.put(uid, now);
            lastHeard = now;
        }
    }

    synchronized int freshCount(long now) {
        int count = 0;
        for (long heard : heardAt.values()) {
            if (now - heard <= FRESH_MS)
                count++;
        }
        return count;
    }

    /** Local time a peer was last heard from, or 0 if none has been. */
    synchronized long lastHeard() {
        return lastHeard;
    }

    synchronized void clear() {
        lastUpdatedAt.clear();
        heardAt.clear();
        lastHeard = 0L;
    }
}
