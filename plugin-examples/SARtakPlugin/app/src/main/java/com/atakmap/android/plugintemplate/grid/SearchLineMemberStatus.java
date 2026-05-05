package com.atakmap.android.plugintemplate.grid;

import com.atakmap.coremap.maps.coords.GeoPoint;

public class SearchLineMemberStatus {

    private final SearchTeamMember member;
    private final double distanceFromLineMeters;
    private final double distanceFromReturnMarkMeters;
    private final double paceMetersPerMinute;
    private final GeoPoint returnMark;

    public SearchLineMemberStatus(SearchTeamMember member,
            double distanceFromLineMeters, double distanceFromReturnMarkMeters,
            double paceMetersPerMinute, GeoPoint returnMark) {
        this.member = member;
        this.distanceFromLineMeters = distanceFromLineMeters;
        this.distanceFromReturnMarkMeters = distanceFromReturnMarkMeters;
        this.paceMetersPerMinute = paceMetersPerMinute;
        this.returnMark = returnMark;
    }

    public SearchTeamMember getMember() {
        return member;
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

    public boolean isTooFarAhead(double thresholdMeters) {
        return distanceFromLineMeters > thresholdMeters;
    }

    public boolean isOffReturnMark(double toleranceMeters) {
        return distanceFromReturnMarkMeters > toleranceMeters;
    }
}
