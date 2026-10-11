package com.atakmap.android.plugintemplate.grid;

import android.graphics.Color;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Shape;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a {@link RouteSelectionPreview} while it waits for Confirm or
 * Cancel. The selection is merged into rectangles, so a 1,200-cell box is
 * one or a few map items rather than one per cell.
 */
public class RouteSelectionPreviewOverlay {

    private static final String GROUP_NAME = "SARtak Route Preview";
    private static final String UID_PREFIX = "sartak-route-preview-";
    private static final int FILL_COLOR = Color.argb(70, 255, 196, 0);
    private static final int STROKE_COLOR = Color.rgb(255, 196, 0);

    private final MapView mapView;
    private final GridCoordinateConverter converter;
    private final OverlayItemSync itemSync = new OverlayItemSync();
    private MapGroup previewGroup;
    private RouteSelectionPreview shown;

    public RouteSelectionPreviewOverlay(MapView mapView,
            GridCoordinateConverter converter) {
        this.mapView = mapView;
        this.converter = converter;
    }

    public MapGroup getMapGroup() {
        return previewGroup;
    }

    /** Shows {@code preview}, or clears the map when it is null or empty. */
    public void show(RouteSelectionPreview preview) {
        if (preview == shown)
            return;
        shown = preview;
        ensureGroup();
        if (preview == null || preview.isEmpty()) {
            itemSync.clear(previewGroup);
            return;
        }
        double size = GridCoordinateConverter.BASE_CELL_SIZE_METERS;
        List<OverlayItemSync.Spec> specs = new ArrayList<>();
        for (GridCellRuns.Rect block : preview.blocks()) {
            String zone = block.getKey();
            double west = block.getFirstColumn() * size;
            double south = block.getFirstRow() * size;
            double east = (block.getLastColumn() + 1) * size;
            double north = (block.getLastRow() + 1) * size;
            specs.add(new OverlayItemSync.Spec(UID_PREFIX + zone + "-"
                    + block.getFirstColumn() + "-" + block.getFirstRow() + "-"
                    + block.getLastColumn() + "-" + block.getLastRow(),
                    preview.getLabel() + " preview", "route-preview",
                    new GeoPoint[] {
                            converter.toGeoPoint(zone, west, south),
                            converter.toGeoPoint(zone, east, south),
                            converter.toGeoPoint(zone, east, north),
                            converter.toGeoPoint(zone, west, north)
                    }, true, FILL_COLOR, STROKE_COLOR, 3.0,
                    Shape.BASIC_LINE_STYLE_DASHED).unlabelled());
        }
        itemSync.apply(previewGroup, specs, false);
    }

    public void clear() {
        show(null);
    }

    private void ensureGroup() {
        if (previewGroup != null)
            return;
        previewGroup = mapView.getRootGroup().findMapGroup(GROUP_NAME);
        if (previewGroup == null)
            previewGroup = mapView.getRootGroup().addGroup(GROUP_NAME);
        previewGroup.setVisible(true);
    }
}
