package com.atakmap.android.plugintemplate.grid;

import android.graphics.Color;

import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.List;
import java.util.UUID;

public class SearchTrackOverlay {

    private static final String GROUP_NAME = "SARtak Track Overlay";

    private final MapView mapView;
    private MapGroup trackGroup;
    private boolean visible = true;
    private String lastRenderKey = "";public SearchTrackOverlay(MapView mapView) {
        this.mapView = mapView;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        ensureTrackGroup();
        if (!visible) {
            trackGroup.clearItems();
            trackGroup.setVisible(false);
            lastRenderKey = "";
        } else {
            trackGroup.setVisible(true);
        }
    }

    public void render(List<double[]> points) {
        if (!visible)
            return;

        ensureTrackGroup();
        if (points == null || points.size() < 2) {
            if (lastRenderKey.length() > 0) {
                trackGroup.clearItems();
                lastRenderKey = "";
            }
            return;
        }
        String renderKey = buildRenderKey(points);
        if (renderKey.equals(lastRenderKey))
            return;
        trackGroup.clearItems();
        lastRenderKey = renderKey;

        GeoPoint[] trackPoints = new GeoPoint[points.size()];
        for (int i = 0; i < points.size(); i++) {
            double[] point = points.get(i);
            trackPoints[i] = new GeoPoint(point[0], point[1]);
        }

        DrawingShape track = new DrawingShape(mapView,
                "sartak-track-" + UUID.randomUUID());
        track.setTitle("SARtak My Track");
        track.setPoints(trackPoints);
        track.setClosed(false);
        track.setStrokeColor(Color.argb(220, 66, 195, 106));
        track.setStrokeWeight(4.0);
        configureMapItem(track);
        trackGroup.addItem(track);
    }

    private String buildRenderKey(List<double[]> points) {
        if (points == null || points.isEmpty())
            return "";
        double[] first = points.get(0);
        double[] last = points.get(points.size() - 1);
        return points.size() + "|" + rounded(first[0]) + "," + rounded(first[1])
                + "|" + rounded(last[0]) + "," + rounded(last[1]);
    }

    private long rounded(double value) {
        return Math.round(value * 1000000.0);
    }
    private void ensureTrackGroup() {
        if (trackGroup != null)
            return;
        trackGroup = mapView.getRootGroup().findMapGroup(GROUP_NAME);
        if (trackGroup == null)
            trackGroup = mapView.getRootGroup().addGroup(GROUP_NAME);
        trackGroup.setMetaBoolean("addToObjList", true);
    }

    private void configureMapItem(MapItem item) {
        item.setMetaBoolean("archive", false);
        item.setMetaBoolean("editable", false);
        item.setMetaBoolean("movable", false);
        item.setMetaBoolean("removable", true);
        item.setMetaString("entry", "sartak");
        item.setMetaString("callsign", item.getTitle());
        item.setMetaString("sartak.kind", "track-log");
    }
}


