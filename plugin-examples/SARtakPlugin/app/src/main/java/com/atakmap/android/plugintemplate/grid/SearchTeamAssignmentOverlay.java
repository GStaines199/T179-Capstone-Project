package com.atakmap.android.plugintemplate.grid;

import android.graphics.Color;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.plugintemplate.runtime.SearchRoutePlan;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.ArrayList;
import java.util.List;

public class SearchTeamAssignmentOverlay {

    private static final String GROUP_NAME = "SARtak Team Assignments";
    private static final int MAX_ASSIGNMENT_BLOCKS = 600;

    private final MapView mapView;
    private final GridCoordinateConverter converter;
    private final SearchGridManager gridManager;
    private final OverlayItemSync itemSync = new OverlayItemSync();
    private MapGroup assignmentGroup;
    private boolean visible;
    private String lastRenderKey = "";

    public SearchTeamAssignmentOverlay(MapView mapView,
            GridCoordinateConverter converter, SearchGridManager gridManager) {
        this.mapView = mapView;
        this.converter = converter;
        this.gridManager = gridManager;
    }

    public boolean isVisible() {
        return visible;
    }

    public MapGroup getMapGroup() {
        return assignmentGroup;
    }

    public boolean toggleVisible() {
        setVisible(!visible);
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        ensureGroup();
        if (!visible) {
            itemSync.clear(assignmentGroup);
            assignmentGroup.setVisible(false);
            lastRenderKey = "";
        } else {
            assignmentGroup.setVisible(true);
        }
    }

    /**
     * Shades each team's assigned cells. Adjacent cells are merged into
     * rectangles so a team block of hundreds of cells costs a few map items.
     */
    public void render(List<SearchRoutePlan> routePlans) {
        if (!visible)
            return;
        ensureGroup();
        String renderKey = routePlans == null ? ""
                : buildRenderKey(routePlans);
        if (renderKey.equals(lastRenderKey))
            return;
        lastRenderKey = renderKey;

        List<OverlayItemSync.Spec> specs = new ArrayList<>();
        if (routePlans != null) {
            for (SearchRoutePlan plan : routePlans) {
                if (specs.size() >= MAX_ASSIGNMENT_BLOCKS)
                    break;
                addPlan(specs, plan);
            }
        }
        itemSync.apply(assignmentGroup, specs, false);
    }

    private void addPlan(List<OverlayItemSync.Spec> specs,
            SearchRoutePlan plan) {
        int color = plan.getTeamColorArgb() == 0
                ? Color.rgb(138, 143, 152)
                : plan.getTeamColorArgb();
        List<GridCellRuns.Cell> cells = new ArrayList<>();
        double size = GridCoordinateConverter.BASE_CELL_SIZE_METERS;
        for (SearchGridCell cell : gridManager.cellsForIds(plan
                .getCellIds())) {
            cells.add(new GridCellRuns.Cell(
                    (int) Math.floor(cell.getWest() / size + 1e-6),
                    (int) Math.floor(cell.getSouth() / size + 1e-6),
                    cell.getZoneDescriptor()));
        }
        for (GridCellRuns.Rect block : GridCellRuns.merge(cells)) {
            if (specs.size() >= MAX_ASSIGNMENT_BLOCKS)
                return;
            String zone = block.getKey();
            double west = block.getFirstColumn() * size;
            double south = block.getFirstRow() * size;
            double east = (block.getLastColumn() + 1) * size;
            double north = (block.getLastRow() + 1) * size;
            specs.add(new OverlayItemSync.Spec("sartak-assignment-"
                    + plan.getPlanId() + "-" + zone + "-"
                    + block.getFirstColumn() + "-" + block.getFirstRow() + "-"
                    + block.getLastColumn() + "-" + block.getLastRow(),
                    plan.getTeamName() + " assignment", "team-assignment-cell",
                    new GeoPoint[] {
                            converter.toGeoPoint(zone, west, south),
                            converter.toGeoPoint(zone, east, south),
                            converter.toGeoPoint(zone, east, north),
                            converter.toGeoPoint(zone, west, north)
                    }, true, withAlpha(color, 45), withAlpha(color, 220), 2.0)
                            .meta("sartak.teamId", plan.getTeamId())
                            .meta("sartak.teamName", plan.getTeamName()));
        }
    }

    private String buildRenderKey(List<SearchRoutePlan> routePlans) {
        StringBuilder builder = new StringBuilder();
        for (SearchRoutePlan plan : routePlans) {
            builder.append(plan.getPlanId()).append('|')
                    .append(plan.getUpdatedAt()).append('|')
                    .append(plan.getTeamColorArgb()).append('|')
                    .append(plan.getCellIds().size()).append('|');
        }
        return builder.toString();
    }

    private void ensureGroup() {
        if (assignmentGroup != null)
            return;
        assignmentGroup = mapView.getRootGroup().findMapGroup(GROUP_NAME);
        if (assignmentGroup == null)
            assignmentGroup = mapView.getRootGroup().addGroup(GROUP_NAME);
        assignmentGroup.setMetaBoolean("addToObjList", true);
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color),
                Color.blue(color));
    }
}
