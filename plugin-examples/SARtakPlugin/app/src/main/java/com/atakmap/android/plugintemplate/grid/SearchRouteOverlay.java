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
    private static final int MAX_ROUTE_POINTS = 5000;

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
        List<SearchGridCell> routeCells = plan == null
                ? new ArrayList<SearchGridCell>()
                : gridManager.cellsForIds(plan.getCellIds());
        if (routeCells.size() >= 2) {
            int count = Math.min(routeCells.size(), MAX_ROUTE_POINTS);
            GeoPoint[] points = new GeoPoint[count];
            for (int i = 0; i < count; i++)
                points[i] = centerOf(routeCells.get(i), converter);
            specs.add(new OverlayItemSync.Spec("sartak-route-"
                    + plan.getPlanId(), "Search route", "search-route-guide",
                    points, false, 0, color, 3.0,
                    Shape.BASIC_LINE_STYLE_DASHED));
        }
        itemSync.apply(routeGroup, specs, false);
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
