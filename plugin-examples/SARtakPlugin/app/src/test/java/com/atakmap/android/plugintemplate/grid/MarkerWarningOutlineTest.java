package com.atakmap.android.plugintemplate.grid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The warning outline on a team member's map marker.
 *
 * <p>This decision used to read {@code getDistanceFromLineMeters()} and
 * compare it itself, which is the pattern the rest of this fix removes: it
 * tests a number without first asking whether there is a position behind it.
 * It was safe only by accident -- NaN fails every comparison, and its caller
 * already sat behind a position check -- and accidents are not guarantees.
 *
 * <p>{@code SearchTeamMarkerOverlay} needs a MapView, which a JVM test cannot
 * build, so the decision was made static and package-private to bring it
 * within reach. That is the only reason these tests can exist.
 */
public class MarkerWarningOutlineTest {

    private static final double FAR = 40.0;
    private static final double NEAR = 2.0;

    private SearchTeamMember member(String uid,
            SearchTeamMember.ConnectionStatus connection) {
        return new SearchTeamMember(uid, "Rescue " + uid,
                SearchTeamMember.TeamRole.SEARCHER, "Blue", 0xFF4AA3FF,
                SearchTeamMember.MembershipStatus.ACTIVE_MEMBER,
                connection, 1, 0.0, 0.0, 0.0,
                "No GPS Signal", "No GPS Signal", "No GPS Signal",
                "No GPS Signal", "No GPS Signal", "No GPS Signal");
    }

    private SearchTeamMember connectedMember(String uid) {
        return member(uid, SearchTeamMember.ConnectionStatus.CONNECTED);
    }

    private SearchLineMemberStatus status(SearchTeamMember member,
            double distanceFromLine, boolean positionKnown) {
        return new SearchLineMemberStatus(member, distanceFromLine, 0.0,
                31.0, null, positionKnown);
    }

    private List<SearchLineMemberStatus> statuses(
            SearchLineMemberStatus... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    // ---- The fabrication guard ------------------------------------------

    /**
     * The case the guard exists for: flagged as having no position, but
     * carrying a distance that would clear the threshold if anyone measured
     * it. No outline may be drawn from a position we do not have.
     */
    @Test
    public void noOutline_whenTheMemberHasNoPositionDespiteALargeDistance() {
        SearchTeamMember m = connectedMember("UID-A");

        assertFalse("the position flag must win over the number",
                SearchTeamMarkerOverlay.hasWarningOutline(m,
                        statuses(status(m, FAR, false)), true));
    }

    @Test
    public void noOutline_whenTheDistanceIsNotANumber() {
        SearchTeamMember m = connectedMember("UID-A");

        assertFalse(SearchTeamMarkerOverlay.hasWarningOutline(m,
                statuses(status(m, Double.NaN, false)), true));
    }

    // ---- Real deviations still warn --------------------------------------

    @Test
    public void outline_whenAMeasuredMemberIsAheadOfTheLine() {
        SearchTeamMember m = connectedMember("UID-A");

        assertTrue(SearchTeamMarkerOverlay.hasWarningOutline(m,
                statuses(status(m, FAR, true)), true));
    }

    /** Two-sided: behind the line is off the line too. */
    @Test
    public void outline_whenAMeasuredMemberIsBehindTheLine() {
        SearchTeamMember m = connectedMember("UID-A");

        assertTrue(SearchTeamMarkerOverlay.hasWarningOutline(m,
                statuses(status(m, -FAR, true)), true));
    }

    @Test
    public void noOutline_whenAMeasuredMemberIsOnTheLine() {
        SearchTeamMember m = connectedMember("UID-A");

        assertFalse(SearchTeamMarkerOverlay.hasWarningOutline(m,
                statuses(status(m, NEAR, true)), true));
    }

    // ---- The other reasons for an outline are unaffected -----------------

    /** A disconnected member is outlined whatever their position says. */
    @Test
    public void outline_whenTheMemberNeedsAConnectionAlert() {
        SearchTeamMember m = member("UID-A",
                SearchTeamMember.ConnectionStatus.DISCONNECTED);

        assertTrue(SearchTeamMarkerOverlay.hasWarningOutline(m,
                statuses(status(m, NEAR, true)), true));
    }

    @Test
    public void noOutline_beforeTheLineHasStarted() {
        SearchTeamMember m = connectedMember("UID-A");

        assertFalse("line deviation is meaningless with no line",
                SearchTeamMarkerOverlay.hasWarningOutline(m,
                        statuses(status(m, FAR, true)), false));
    }

    @Test
    public void noOutline_whenTheStatusBelongsToSomebodyElse() {
        SearchTeamMember m = connectedMember("UID-A");
        SearchTeamMember other = connectedMember("UID-B");

        assertFalse(SearchTeamMarkerOverlay.hasWarningOutline(m,
                statuses(status(other, FAR, true)), true));
    }
}
