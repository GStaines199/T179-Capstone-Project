package com.atakmap.android.plugintemplate.grid;

import com.atakmap.coremap.maps.coords.GeoPoint;

public class SearchLineMemberStatus {

    private final SearchTeamMember member;
    private final double distanceFromLineMeters;
    private final double distanceFromReturnMarkMeters;
    private final double paceMetersPerMinute;
    private final GeoPoint returnMark;
    private final boolean positionKnown;

    public SearchLineMemberStatus(SearchTeamMember member,
            double distanceFromLineMeters, double distanceFromReturnMarkMeters,
            double paceMetersPerMinute, GeoPoint returnMark,
            boolean positionKnown) {
        this.member = member;
        this.distanceFromLineMeters = distanceFromLineMeters;
        this.distanceFromReturnMarkMeters = distanceFromReturnMarkMeters;
        this.paceMetersPerMinute = paceMetersPerMinute;
        this.returnMark = returnMark;
        this.positionKnown = positionKnown;
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

    public double getDistanceFromLineMeters() {
        return distanceFromLineMeters;
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
}
