package com.atakmap.android.plugintemplate.grid;

import com.atakmap.coremap.maps.coords.GeoPoint;

public class SearchLineMemberStatus {

    private final SearchTeamMember member;
    private final double distanceFromLineMeters;
    private final double distanceFromReturnMarkMeters;
    private final double paceMetersPerMinute;
    private final GeoPoint returnMark;
    private final boolean positionKnown;
    private final double bearingDegrees;
    private final boolean bearingReliable;

    /**
     * Retained for callers that do not carry a bearing. Records the bearing as
     * unreported rather than as zero, so such a status can never be mistaken
     * for one facing due north.
     */
    public SearchLineMemberStatus(SearchTeamMember member,
            double distanceFromLineMeters, double distanceFromReturnMarkMeters,
            double paceMetersPerMinute, GeoPoint returnMark,
            boolean positionKnown) {
        this(member, distanceFromLineMeters, distanceFromReturnMarkMeters,
                paceMetersPerMinute, returnMark, positionKnown, Double.NaN,
                false);
    }

    public SearchLineMemberStatus(SearchTeamMember member,
            double distanceFromLineMeters, double distanceFromReturnMarkMeters,
            double paceMetersPerMinute, GeoPoint returnMark,
            boolean positionKnown, double bearingDegrees,
            boolean bearingReliable) {
        this.member = member;
        this.distanceFromLineMeters = distanceFromLineMeters;
        this.distanceFromReturnMarkMeters = distanceFromReturnMarkMeters;
        this.paceMetersPerMinute = paceMetersPerMinute;
        this.returnMark = returnMark;
        this.positionKnown = positionKnown;
        this.bearingDegrees = bearingDegrees;
        this.bearingReliable = bearingReliable;
    }

    public SearchTeamMember getMember() {
        return member;
    }

    /**
     * Whether the distances on this status were measured from a real position.
     *
     * <p>False when ATAK has not reported a fix for the member. The member
     * still appears in the line status -- a leader needs to know they exist and
     * which lane they hold -- but the distances are NaN and must not be shown
     * as measurements. The return mark stays valid either way: it is derived
     * from the lane assignment, not from where the member is.
     */
    public boolean hasKnownPosition() {
        return positionKnown;
    }

    /**
     * Signed distance along the direction of travel: positive ahead of the
     * line, negative behind it, NaN when no position was reported.
     *
     * <p>The sign is relative to the line's {@link SearchLineDirection}, not to
     * north, so "ahead" means the same thing to a south-bound team as to a
     * north-bound one.
     */
    public double getDistanceFromLineMeters() {
        return distanceFromLineMeters;
    }

    /** True bearing of travel; meaningless unless {@link #hasReliableBearing()}. */
    public double getBearingDegrees() {
        return bearingDegrees;
    }

    /**
     * Whether ATAK's heading for this member is trustworthy. False for a
     * stationary device, whose heading is arithmetically valid and physically
     * meaningless.
     */
    public boolean hasReliableBearing() {
        return bearingReliable && !Double.isNaN(bearingDegrees);
    }

    /** Which side of the line this member is on, and by how much it matters. */
    public SearchLineGuidance.Band getGuidanceBand() {
        return SearchLineGuidance.bandFor(positionKnown,
                distanceFromLineMeters);
    }

    /** The guidance sentence shown to this member. */
    public String getGuidanceLabel() {
        return SearchLineGuidance.guidanceLabel(positionKnown,
                distanceFromLineMeters);
    }

    /** This member's pace, or "unavailable" when ATAK reported none. */
    public String getPaceLabel() {
        return SearchLineGuidance.paceLabel(paceMetersPerMinute);
    }

    /** This member's bearing, or "unavailable" when it is not trustworthy. */
    public String getBearingLabel() {
        return SearchLineGuidance.bearingLabel(hasReliableBearing(),
                bearingDegrees);
    }

    public double getDistanceFromReturnMarkMeters() {
        return distanceFromReturnMarkMeters;
    }

    public double getPaceMetersPerMinute() {
        return paceMetersPerMinute;
    }

    public GeoPoint getReturnMark() {
        return returnMark;
    }

    /**
     * A member whose position is unknown is never "too far ahead". Without this
     * guard the placeholder distance would be compared against the threshold
     * and could raise a Slow/Hold warning about a searcher nobody can locate.
     */
    public boolean isTooFarAhead(double thresholdMeters) {
        return positionKnown && distanceFromLineMeters > thresholdMeters;
    }

    public boolean isOffReturnMark(double toleranceMeters) {
        return positionKnown
                && distanceFromReturnMarkMeters > toleranceMeters;
    }

    /**
     * Off the line in either direction, ahead or behind.
     *
     * <p>Separate from {@link #isTooFarAhead(double)}, which is one-sided.
     * Exists so callers that care about both directions have a guarded
     * question to ask instead of reading the raw distance and comparing it
     * themselves -- which is how the marker overlay came to test a distance
     * without first asking whether there was a position behind it.
     */
    public boolean isOffLine(double thresholdMeters) {
        return positionKnown
                && Math.abs(distanceFromLineMeters) > thresholdMeters;
    }

    /**
     * The mirror of {@link #isTooFarAhead(double)}: a searcher who has dropped
     * back far enough to leave unswept ground behind the line.
     *
     * <p>Guarded on position for the same reason as its counterpart. Without
     * the guard the placeholder distance would be compared against the
     * threshold and could raise a "catch up" instruction aimed at a searcher
     * nobody can locate.
     */
    public boolean isTooFarBehind(double thresholdMeters) {
        return positionKnown && distanceFromLineMeters < -thresholdMeters;
    }
}
