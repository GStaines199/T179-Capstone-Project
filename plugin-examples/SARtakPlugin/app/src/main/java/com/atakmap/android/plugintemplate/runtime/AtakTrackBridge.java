package com.atakmap.android.plugintemplate.runtime;

import com.atakmap.android.maps.CrumbTrail;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.plugintemplate.grid.SearchTrackManager;
import com.atakmap.android.track.crumb.CrumbDatabase;
import com.atakmap.android.track.maps.TrackPolyline;

public class AtakTrackBridge {

    private final MapView mapView;

    public AtakTrackBridge(MapView mapView) {
        this.mapView = mapView;
    }

    public boolean hasAtakTrackTrail() {
        Marker self = getSelf();
        return self != null && self.getCrumbTrail() != null;
    }

    public void setVisible(boolean visible) {
        Marker self = getSelf();
        if (self == null)
            return;

        CrumbTrail crumbTrail = self.getCrumbTrail();
        if (crumbTrail != null)
            crumbTrail.setVisible(visible);

    }

    public void setTracking(boolean tracking) {
        Marker self = getSelf();
        if (self == null)
            return;

        CrumbTrail crumbTrail = self.getCrumbTrail();
        if (crumbTrail != null)
            crumbTrail.setTracking(tracking);
    }

    public void clearVisibleTrack() {
        Marker self = getSelf();
        if (self == null)
            return;

        CrumbTrail crumbTrail = self.getCrumbTrail();
        if (crumbTrail != null)
            crumbTrail.clearAllCrumbs();

    }

    public String getStatusSummary(SearchTrackManager fallbackTrackManager) {
        if (hasAtakTrackTrail()) {
            return "Using ATAK Track History\nSARtak controls mirror the ATAK self trail where available";
        }
        return "ATAK Track History unavailable\n"
                + fallbackTrackManager.getStatusSummary();
    }

    public String getDetailsSummary(SearchTrackManager fallbackTrackManager) {
        StringBuilder builder = new StringBuilder();
        builder.append(getAtakDetails());
        if (!hasAtakTrackTrail()) {
            builder.append("\n\nSARtak local track\n");
            builder.append(fallbackTrackManager.getDetailsSummary());
        }
        return builder.toString();
    }

    private String getAtakDetails() {
        Marker self = getSelf();
        if (self == null)
            return "ATAK Track History: self marker unavailable";

        StringBuilder builder = new StringBuilder("ATAK Track History");
        CrumbTrail crumbTrail = self.getCrumbTrail();

        if (crumbTrail == null) {
            builder.append(": no active ATAK trail found");
        } else {
            builder.append(": trail attached to self marker");
            if (crumbTrail != null)
                builder.append("\nVisible crumbs: ").append(crumbTrail.size);
        }

        try {
            TrackPolyline recentTrack = CrumbDatabase.instance()
                    .getMostRecentTrack(self.getUID());
            if (recentTrack != null) {
                builder.append("\nMost recent ATAK track points: ")
                        .append(recentTrack.getNumPoints());
            }
        } catch (Throwable ignored) {
            builder.append("\nATAK track database summary unavailable");
        }

        return builder.toString();
    }

    private Marker getSelf() {
        return mapView == null ? null : mapView.getSelfMarker();
    }
}
