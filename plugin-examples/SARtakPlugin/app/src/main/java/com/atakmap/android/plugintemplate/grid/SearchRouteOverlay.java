package com.atakmap.android.plugintemplate.grid;

import android.graphics.Color;

import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.android.plugintemplate.runtime.SearchRoutePlan;

import java.util.List;
import java.util.UUID;

public class SearchRouteOverlay {

    private static final String GROUP_NAME = "SARtak Search Route Overlay";
    private static final double DASHES_PER_LEG = 8.0;
    private static final int MAX_RENDERED_LEGS = 600;

    private final MapView mapView;
    private MapGroup routeGroup;
    private boolean visible = true;
    private String lastRenderKey = "";public SearchRouteOverlay(MapView mapView) {
        this.mapView = mapView;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        ensureRouteGroup();
        if (!visible) {
            routeGroup.clearItems();
            routeGroup.setVisible(false);
            lastRenderKey = "";
        } else {
            routeGroup.setVisible(true);
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean toggleVisible() {
        setVisible(!visible);
        return visible;
    }

    public void render(SearchRoutePlan plan, List<SearchGridCell> routeCells,
            GridCoordinateConverter converter) {
        if (!visible)
            return;
        ensureRouteGroup();
        if (routeCells == null || routeCells.size() < 2) {
            if (lastRenderKey.length() > 0) {
                routeGroup.clearItems();
                lastRenderKey = "";
            }
            return;
        }
        int color = plan == null || plan.getTeamColorArgb() == 0
                ? Color.argb(230, 255, 255, 255)
                : withAlpha(plan.getTeamColorArgb(), 235);
        String renderKey = buildRenderKey(plan, routeCells, color);
        if (renderKey.equals(lastRenderKey))
            return;
        routeGroup.clearItems();
        lastRenderKey = renderKey;
        int legs = Math.min(routeCells.size() - 1, MAX_RENDERED_LEGS);
        for (int i = 0; i < legs; i++) {
            GeoPoint start = centerOf(routeCells.get(i), converter);
            GeoPoint end = centerOf(routeCells.get(i + 1), converter);
            renderDottedLeg(start, end, i, color);
        }
    }

    private String buildRenderKey(SearchRoutePlan plan,
            List<SearchGridCell> routeCells, int color) {
        StringBuilder builder = new StringBuilder();
        builder.append(plan == null ? "" : plan.getPlanId()).append('|')
                .append(plan == null ? 0L : plan.getUpdatedAt()).append('|')
                .append(color).append('|')
                .append(routeCells.size()).append('|');
        for (SearchGridCell cell : routeCells)
            builder.append(cell.getId()).append(',');
        return builder.toString();
    }
    private void renderDottedLeg(GeoPoint start, GeoPoint end, int legIndex,
            int color) {
        for (int i = 0; i < DASHES_PER_LEG; i += 2) {
            double first = i / DASHES_PER_LEG;
            double second = Math.min(1.0, (i + 1) / DASHES_PER_LEG);
            DrawingShape dash = createLine("Search route " + legIndex + "-"
                    + i, new GeoPoint[] {
                            interpolate(start, end, first),
                            interpolate(start, end, second)
                    }, color, 3.0);
            dash.setMetaString("sartak.kind", "search-route-guide");
            routeGroup.addItem(dash);
        }
    }

    private GeoPoint centerOf(SearchGridCell cell,
            GridCoordinateConverter converter) {
        return converter.toGeoPoint(cell.getZoneDescriptor(),
                (cell.getWest() + cell.getEast()) / 2.0,
                (cell.getSouth() + cell.getNorth()) / 2.0);
    }

    private GeoPoint interpolate(GeoPoint start, GeoPoint end, double t) {
        return new GeoPoint(start.getLatitude()
                + (end.getLatitude() - start.getLatitude()) * t,
                start.getLongitude()
                        + (end.getLongitude() - start.getLongitude()) * t);
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

    private void ensureRouteGroup() {
        if (routeGroup != null)
            return;
        routeGroup = mapView.getRootGroup().findMapGroup(GROUP_NAME);
        if (routeGroup == null)
            routeGroup = mapView.getRootGroup().addGroup(GROUP_NAME);
        routeGroup.setMetaBoolean("addToObjList", true);
    }

    private void configureMapItem(MapItem item) {
        item.setClickable(false);
        item.setMetaBoolean("archive", false);
        item.setMetaBoolean("editable", false);
        item.setMetaBoolean("movable", false);
        item.setMetaBoolean("removable", true);
        item.setMetaString("entry", "sartak");
        item.setMetaString("callsign", "");
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color),
                Color.blue(color));
    }

    private String uid(String title) {
        return "sartak-route-"
                + title.toLowerCase().replaceAll("[^a-z0-9]+", "-") + "-"
                + UUID.randomUUID();
    }
}



