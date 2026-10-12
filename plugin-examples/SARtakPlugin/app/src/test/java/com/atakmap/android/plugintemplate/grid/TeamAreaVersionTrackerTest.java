package com.atakmap.android.plugintemplate.grid;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The lead's team area swapping between two plans (Task 3 finding T3-1).
 *
 * <p>Replays the refresh sequence that caused it: the lead publishes its
 * plan, and on the next refresh the observer cache still holds the older
 * document for the same id.
 */
public class TeamAreaVersionTrackerTest {

    private static final String DOC = "area-op-1-TEAM-lead";
    private static final String BOX = "BOX|56J|20km";
    private static final String CIRCLE = "CIRCLE|56J|1km";

    private TeamAreaVersionTracker tracker;

    @Before
    public void setUp() {
        tracker = new TeamAreaVersionTracker();
    }

    @Test
    public void olderCachedDocumentAfterAPublishIsNotApplied() {
        // The box was applied earlier; the lead now publishes its circle.
        tracker.recordApplied(DOC, 1_000L);
        tracker.recordPublished(DOC, 5_000L);

        // Next refresh: the cache still returns the box document.
        assertEquals(TeamAreaVersionTracker.Decision.SKIP,
                tracker.decide(DOC, 1_000L, BOX, CIRCLE));
        // Then the cache catches up with the lead's own write.
        assertEquals(TeamAreaVersionTracker.Decision.SKIP,
                tracker.decide(DOC, 5_000L, CIRCLE, CIRCLE));
    }

    @Test
    public void planStaysPutOverManyRefreshes() {
        tracker.recordPublished(DOC, 5_000L);
        String plan = CIRCLE;
        long[] cachedVersions = {1_000L, 1_000L, 5_000L, 1_000L, 5_000L};
        String[] cachedGeometry = {BOX, BOX, CIRCLE, BOX, CIRCLE};

        for (int i = 0; i < cachedVersions.length; i++) {
            TeamAreaVersionTracker.Decision decision = tracker.decide(DOC,
                    cachedVersions[i], cachedGeometry[i], plan);
            if (decision == TeamAreaVersionTracker.Decision.APPLY)
                plan = cachedGeometry[i];
        }

        assertEquals(CIRCLE, plan);
    }

    @Test
    public void newerAssignmentFromHqIsApplied() {
        tracker.recordPublished(DOC, 5_000L);

        assertEquals(TeamAreaVersionTracker.Decision.APPLY,
                tracker.decide(DOC, 9_000L, BOX, CIRCLE));
        tracker.recordApplied(DOC, 9_000L);
        assertEquals(TeamAreaVersionTracker.Decision.SKIP,
                tracker.decide(DOC, 9_000L, BOX, BOX));
    }

    @Test
    public void newerDocumentWithTheSameGeometryIsOnlyRecorded() {
        tracker.recordApplied(DOC, 1_000L);

        assertEquals(TeamAreaVersionTracker.Decision.RECORD_ONLY,
                tracker.decide(DOC, 2_000L, BOX, BOX));
    }

    @Test
    public void firstDocumentAfterStartIsAppliedWhenItDiffers() {
        assertEquals(TeamAreaVersionTracker.Decision.APPLY,
                tracker.decide(DOC, 1_000L, BOX, ""));
    }

    @Test
    public void anotherOperationsDocumentIsNotComparedByTime() {
        tracker.recordPublished(DOC, 9_000L);

        assertEquals("A different team or operation has its own history",
                TeamAreaVersionTracker.Decision.APPLY,
                tracker.decide("area-op-2-TEAM-lead", 1_000L, BOX, CIRCLE));
    }
}
