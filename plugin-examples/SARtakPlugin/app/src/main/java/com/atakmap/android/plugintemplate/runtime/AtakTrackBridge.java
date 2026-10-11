package com.atakmap.android.plugintemplate.runtime;

import com.atakmap.android.plugintemplate.grid.SearchTrackManager;

import java.util.Locale;

/**
 * Controls and reports the track ATAK draws for this device.
 *
 * <p>This used to bridge to the crumb trail ATAK attaches to its own self
 * marker. That trail is fed from ATAK's fused location engine, so whenever it
 * was showing anything it was showing the processed source the raw capture
 * requirement moves away from -- and because a self-marker trail exists on a
 * stock install whether or not it holds any crumbs, it also suppressed
 * SARtak's own display outright.
 *
 * <p>It now drives {@link RawGnssTrackTrail} instead, so the same native crumb
 * rendering, visibility control and track database are fed from unmodified
 * device fixes.
 */
public class AtakTrackBridge {

    private final RawGnssTrackTrail rawTrail;

    public AtakTrackBridge(RawGnssTrackTrail rawTrail) {
        this.rawTrail = rawTrail;
    }

    /** True once raw GNSS crumbs are actually drawn, not merely enabled. */
    public boolean hasAtakTrackTrail() {
        return rawTrail.isReady() && rawTrail.getCrumbCount() > 0;
    }

    public void setVisible(boolean visible) {
        rawTrail.setVisible(visible);
    }

    public void setTracking(boolean tracking) {
        rawTrail.setRecording(tracking);
    }

    public void clearVisibleTrack() {
        rawTrail.clear();
    }

    public String getStatusSummary(SearchTrackManager trackManager) {
        if (!rawTrail.isReady())
            return "ATAK track history: waiting for first raw GNSS fix\n"
                    + trackManager.getStatusSummary();

        return String.format(Locale.US,
                "ATAK track history from raw GNSS\n%s - %d crumbs drawn",
                rawTrail.isRecording() ? "Recording" : "Paused",
                rawTrail.getCrumbCount());
    }

    public String getDetailsSummary(SearchTrackManager trackManager) {
        StringBuilder builder = new StringBuilder("ATAK track history");
        if (!rawTrail.isReady()) {
            builder.append(": no raw GNSS fix received yet");
        } else {
            builder.append(String.format(Locale.US,
                    "\nCrumbs drawn: %d\nTrail: %s",
                    rawTrail.getCrumbCount(),
                    rawTrail.isVisible() ? "Shown on map"
                            : "Hidden from map"));
            builder.append("\nCrumbs are thinned by ATAK's distance "
                    + "threshold; the log below holds every fix.");
        }
        builder.append("\n\nSARtak raw GNSS log\n");
        builder.append(trackManager.getDetailsSummary());
        return builder.toString();
    }
}
