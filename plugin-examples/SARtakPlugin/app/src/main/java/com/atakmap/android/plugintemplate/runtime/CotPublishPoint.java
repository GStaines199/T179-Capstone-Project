package com.atakmap.android.plugintemplate.runtime;

import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.maps.coords.GeoPoint;

/**
 * Chooses the point SARtak's CoT messages are published with.
 *
 * <p>When ATAK has no usable fix this is {@link CotPoint#ZERO} — zeroed
 * coordinates carrying CoT's unknown markers for hae, ce and le — and never
 * the self marker's last position. A stale position would go out carrying a
 * real-looking accuracy, leaving a receiver no way to tell it is not current;
 * the unknown markers say plainly that the sender does not know where it is.
 */
final class CotPublishPoint {

    private CotPublishPoint() {
    }

    static CotPoint forSnapshot(AtakLocationStatus.Snapshot snapshot) {
        if (!hasPublishablePosition(snapshot))
            return CotPoint.ZERO;
        GeoPoint point = snapshot.getPoint();
        if (point == null || !point.isValid())
            return CotPoint.ZERO;
        return new CotPoint(point);
    }

    /**
     * Whether a snapshot may be published as a position. Split out because
     * {@link CotPoint} cannot be loaded off-device, so this is the part of the
     * rule a unit test can reach.
     */
    static boolean hasPublishablePosition(
            AtakLocationStatus.Snapshot snapshot) {
        return snapshot != null && snapshot.isAvailable();
    }
}
