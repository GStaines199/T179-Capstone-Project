package com.atakmap.android.plugintemplate.grid;

import android.graphics.Color;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Shape;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.android.plugintemplate.runtime.SearchRoutePlan;

import java.util.ArrayList;
import java.util.List;

public class SearchRouteOverlay {

    private static final String GROUP_NAME = "SARtak Search Route Overlay";

    private final MapView mapView;
    private final OverlayItemSync itemSync = new OverlayItemSync();
    private MapGroup routeGroup;
    private boolean visible = true;
    private String lastRenderKey = "";

    public SearchRouteOverlay(MapView mapView) {
        this.mapView = mapView;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        ensureRouteGroup();
        if (!visible) {
            itemSync.clear(routeGroup);
            routeGroup.setVisible(false);
            lastRenderKey = "";
        } else {
            routeGroup.setVisible(true);
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public MapGroup getMapGroup() {
        return routeGroup;
    }

    public boolean toggleVisible() {
        setVisible(!visible);
        return visible;
    }

    /**
     * Draws the route as one dashed polyline through the cell centres. The
     * route's cells are only resolved when the plan has changed.
     */
    public void render(SearchRoutePlan plan, SearchGridManager gridManager,
            GridCoordinateConverter converter) {
        if (!visible)
            return;
        ensureRouteGroup();
        int color = plan == null || plan.getTeamColorArgb() == 0
                ? Color.argb(230, 255, 255, 255)
                : withAlpha(plan.getTeamColorArgb(), 235);
        String renderKey = plan == null ? ""
                : plan.getPlanId() + '|' + plan.getUpdatedAt() + '|' + color
                        + '|' + plan.getCellIds().size();
        if (renderKey.equals(lastRenderKey))
            return;
        lastRenderKey = renderKey;

        List<OverlayItemSync.Spec> specs = new ArrayList<>();
        // Only the ends of each straight run are resolved: resolving all
        // 40,401 cells of a large-area route cost about half a second here.
        List<SearchGridCell> routeCells = plan == null
                ? new ArrayList<SearchGridCell>()
                : routeVertices(gridManager.cellsForIds(
                        plan.getRouteVertexIds()));
        if (routeCells.size() >= 2) {
            // Not capped: the line used to stop after 5000 cells.
            GeoPoint[] points = new GeoPoint[routeCells.size()];
            for (int i = 0; i < points.length; i++)
                points[i] = centerOf(routeCells.get(i), converter);
            specs.add(new OverlayItemSync.Spec("sartak-route-"
                    + plan.getPlanId(), "Search route", "search-route-guide",
                    points, false, 0, color, 3.0,
                    Shape.BASIC_LINE_STYLE_DASHED));
        }
        itemSync.apply(routeGroup, specs, false);
    }

    /**
     * The cells where the route line bends. A cell partway along a straight
     * stretch adds nothing to the drawn line, so a serpentine route over a
     * 20 km area needs about 400 vertices rather than 40,401.
     */
    static List<SearchGridCell> routeVertices(List<SearchGridCell> cells) {
        if (cells.size() <= 2)
            return cells;
        List<SearchGridCell> vertices = new ArrayList<>();
        vertices.add(cells.get(0));
        for (int i = 1; i < cells.size() - 1; i++) {
            if (!isStraightThrough(cells.get(i - 1), cells.get(i),
                    cells.get(i + 1)))
                vertices.add(cells.get(i));
        }
        vertices.add(cells.get(cells.size() - 1));
        return vertices;
    }

    private static boolean isStraightThrough(SearchGridCell previous,
            SearchGridCell cell, SearchGridCell next) {
        if (!previous.getZoneDescriptor().equals(cell.getZoneDescriptor())
                || !cell.getZoneDescriptor().equals(next.getZoneDescriptor()))
            return false;
        double inX = cell.getWest() - previous.getWest();
        double inY = cell.getSouth() - previous.getSouth();
        double outX = next.getWest() - cell.getWest();
        double outY = next.getSouth() - cell.getSouth();
        // Cell corners are whole metres, so these products are exact.
        return inX * outY - inY * outX == 0.0 && inX * outX + inY * outY > 0.0;
    }

    private GeoPoint centerOf(SearchGridCell cell,
            GridCoordinateConverter converter) {
        return converter.toGeoPoint(cell.getZoneDescriptor(),
                (cell.getWest() + cell.getEast()) / 2.0,
                (cell.getSouth() + cell.getNorth()) / 2.0);
    }

    private void ensureRouteGroup() {
        if (routeGroup != null)
            return;
        routeGroup = mapView.getRootGroup().findMapGroup(GROUP_NAME);
        if (routeGroup == null)
            routeGroup = mapView.getRootGroup().addGroup(GROUP_NAME);
        routeGroup.setMetaBoolean("addToObjList", true);
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color),
                Color.blue(color));
    }
}
