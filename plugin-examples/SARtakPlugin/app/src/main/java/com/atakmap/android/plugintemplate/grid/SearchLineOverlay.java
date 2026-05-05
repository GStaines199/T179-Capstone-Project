package com.atakmap.android.plugintemplate.grid;

import android.graphics.Color;

import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.List;
import java.util.UUID;

public class SearchLineOverlay {

    private static final String GROUP_NAME = "SARtak Search Line Overlay";

    private final MapView mapView;
    private MapGroup lineGroup;
    private boolean visible = true;

    public SearchLineOverlay(MapView mapView) {
        this.mapView = mapView;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        ensureLineGroup();
        if (!visible) {
            lineGroup.clearItems();
            lineGroup.setVisible(false);
        } else {
            lineGroup.setVisible(true);
        }
    }

    public void render(SearchLineManager manager) {
        if (!visible)
            return;
        ensureLineGroup();
        lineGroup.clearItems();
        if (manager.getState() == SearchLineState.NOT_STARTED
                || manager.getLineStart() == null
                || manager.getLineEnd() == null)
            return;

        int lineColor = manager.getColorOption().getArgb();
        DrawingShape line = createLine("SARtak Search Line",
                new GeoPoint[] { manager.getLineStart(),
                        manager.getLineEnd() },
                lineColor, manager.getState() == SearchLineState.PAUSED
                        ? 5.0 : 4.0);
        line.setMetaString("sartak.kind", "search-line");
        lineGroup.addItem(line);

        renderMemberReturnMarks(manager.getMemberStatuses(),
                manager.getState() == SearchLineState.PAUSED);
    }

    private void renderMemberReturnMarks(List<SearchLineMemberStatus> statuses,
            boolean paused) {
        for (SearchLineMemberStatus status : statuses) {
            GeoPoint mark = status.getReturnMark();
            int color = paused ? Color.argb(230, 216, 182, 76)
                    : Color.argb(140, 255, 255, 255);
            double latOffset = 0.000035;
            double lonOffset = 0.000035;
            DrawingShape crossA = createLine(status.getMember().getCallsign()
                    + " return mark A",
                    new GeoPoint[] {
                            new GeoPoint(mark.getLatitude() - latOffset,
                                    mark.getLongitude() - lonOffset),
                            new GeoPoint(mark.getLatitude() + latOffset,
                                    mark.getLongitude() + lonOffset)
                    },
                    color, paused ? 3.0 : 2.0);
            crossA.setMetaString("sartak.kind", "search-line-return-mark");
            lineGroup.addItem(crossA);

            DrawingShape crossB = createLine(status.getMember().getCallsign()
                    + " return mark B",
                    new GeoPoint[] {
                            new GeoPoint(mark.getLatitude() - latOffset,
                                    mark.getLongitude() + lonOffset),
                            new GeoPoint(mark.getLatitude() + latOffset,
                                    mark.getLongitude() - lonOffset)
                    },
                    color, paused ? 3.0 : 2.0);
            crossB.setMetaString("sartak.kind", "search-line-return-mark");
            lineGroup.addItem(crossB);
        }
    }

    private DrawingShape createLine(String title, GeoPoint[] points,
            int strokeColor, double strokeWeight) {
        DrawingShape line = new DrawingShape(mapView, uid(title));
        line.setTitle(title);
        line.setPoints(points);
        line.setClosed(false);
        line.setStrokeColor(strokeColor);
        line.setStrokeWeight(strokeWeight);
        configureMapItem(line);
        return line;
    }

    private void ensureLineGroup() {
        if (lineGroup != null)
            return;
        lineGroup = mapView.getRootGroup().findMapGroup(GROUP_NAME);
        if (lineGroup == null)
            lineGroup = mapView.getRootGroup().addGroup(GROUP_NAME);
        lineGroup.setMetaBoolean("addToObjList", true);
    }

    private void configureMapItem(MapItem item) {
        item.setMetaBoolean("archive", false);
        item.setMetaBoolean("editable", false);
        item.setMetaBoolean("movable", false);
        item.setMetaBoolean("removable", true);
        item.setMetaString("entry", "sartak");
        item.setMetaString("callsign", item.getTitle());
    }

    private String uid(String title) {
        return "sartak-line-" + title.toLowerCase().replace(' ', '-') + "-"
                + UUID.randomUUID();
    }
}
