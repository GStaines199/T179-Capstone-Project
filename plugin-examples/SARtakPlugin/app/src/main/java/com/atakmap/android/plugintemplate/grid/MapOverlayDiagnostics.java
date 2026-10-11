package com.atakmap.android.plugintemplate.grid;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Shape;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;

/**
 * Debug-only counters used to profile SARtak overlays on a device. They answer
 * "how many map items does this overlay hold, and how many of them are not
 * even on screen", which is the number that makes large search areas slow.
 */
public final class MapOverlayDiagnostics {

    private MapOverlayDiagnostics() {
    }

    public static String describe(String label, MapGroup group,
            GeoBounds view) {
        if (group == null)
            return label + "=none";
        int total = 0;
        int offscreen = 0;
        MutableGeoBounds itemBounds = new MutableGeoBounds();
        for (MapItem item : group.getItems()) {
            total++;
            if (view != null && item instanceof Shape) {
                ((Shape) item).getBounds(itemBounds);
                if (!view.intersects(itemBounds))
                    offscreen++;
            }
        }
        return label + "=" + total + "(off=" + offscreen + ")";
    }
}
