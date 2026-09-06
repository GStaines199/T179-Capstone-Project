package com.atakmap.android.plugintemplate.grid;

/**
 * Decides whether a team member's stored coordinates may be treated as a
 * position.
 *
 * <p>A {@link SearchTeamMember} always carries latitude and longitude, because
 * they are plain doubles with no "absent" value. Until ATAK reports a fix those
 * numbers are a placeholder, and the member's labels say "No GPS Signal" -- but
 * the numbers themselves look exactly like coordinates. Anything that measures,
 * draws or navigates must therefore ask this class first rather than reading
 * the coordinates and trusting them.
 *
 * <p>Split out as its own class for the same reason as
 * {@code CotPublishPoint.hasPublishablePosition}: the call sites live in
 * {@code SearchLineManager}, {@code SearchLineOverlay} and
 * {@code SARTakMapController}, all of which touch ATAK types that fail JVM
 * bytecode verification off-device. This class touches none, so the rule itself
 * stays reachable from a unit test even though its callers do not.
 */
public final class MemberPositionPolicy {

    /** Shown wherever a distance would otherwise be printed for a member. */
    public static final String UNKNOWN_POSITION_LABEL = "Position unknown";

    private MemberPositionPolicy() {
    }

    /**
     * Whether this member's coordinates may be used for distance maths, map
     * markers or panning.
     *
     * <p>Deliberately not the same question as
     * {@link SearchTeamMember#contributesLane()}. That one asks whether a
     * member is rostered onto a lane, which stays true while their GPS is
     * down -- they are still assigned to walk lane 3, we simply do not know
     * where they are. Conflating the two would re-divide the search line every
     * time somebody's fix dropped.
     */
    public static boolean hasUsablePosition(SearchTeamMember member) {
        return member != null && member.hasLiveAtakContact();
    }

    /**
     * The text for a member's distance from the search line.
     *
     * <p>Guards a specific trap: a member with no fix used to reach the
     * formatter with a placeholder distance, and {@code Math.round} turns both
     * zero and NaN into 0, which the formatter reported as "On line". A
     * searcher whose position was unknown was therefore shown as being exactly
     * where they should be. Position must be resolved before formatting, never
     * inferred from the number.
     */
    public static String lineDistanceLabel(boolean positionKnown,
            double distanceMeters) {
        if (!positionKnown || Double.isNaN(distanceMeters))
            return UNKNOWN_POSITION_LABEL;
        long rounded = Math.round(Math.abs(distanceMeters));
        if (rounded <= 2)
            return "On line";
        return rounded + " m " + (distanceMeters > 0 ? "ahead" : "behind");
    }

    /**
     * Whether a "Slow/Hold" marker may be drawn for this member. Requires both
     * a real position to draw it at and a real reason to draw it.
     */
    public static boolean mayDrawSlowDownMarker(SearchTeamMember member,
            boolean tooFarAhead) {
        return hasUsablePosition(member) && tooFarAhead;
    }
}
