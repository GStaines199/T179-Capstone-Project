package com.atakmap.android.plugintemplate.grid;

import android.graphics.Color;

import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.UUID;

public class SearchTrackOverlay {

    private static final String GROUP_NAME = "SARtak Track Overlay";

    private final MapView mapView;
    private MapGroup trackGroup;
    private boolean visible = true;

    public SearchTrackOverlay(MapView mapView) {
        this.mapView = mapView;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        ensureTrackGroup();
        if (!visible) {
            trackGroup.clearItems();
            trackGroup.setVisible(false);
        } else {
            trackGroup.setVisible(true);
            render();
        }
    }

    public void render() {
        if (!visible)
            return;

        ensureTrackGroup();
        trackGroup.clearItems();
        DrawingShape track = new DrawingShape(mapView,
                "sartak-track-" + UUID.randomUUID());
        track.setTitle("SARtak My Track");
        track.setPoints(new GeoPoint[] {
                new GeoPoint(-27.4710, 153.0254),
                new GeoPoint(-27.4708, 153.0256),
                new GeoPoint(-27.4706, 153.0258),
                new GeoPoint(-27.4705, 153.0260),
                new GeoPoint(-27.4704, 153.0262),
                new GeoPoint(-27.4702, 153.0264)
        });
        track.setClosed(false);
        track.setStrokeColor(Color.argb(220, 66, 195, 106));
        track.setStrokeWeight(4.0);
        configureMapItem(track);
        trackGroup.addItem(track);
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
