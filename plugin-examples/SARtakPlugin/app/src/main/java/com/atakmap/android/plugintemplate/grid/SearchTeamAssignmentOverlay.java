package com.atakmap.android.plugintemplate.grid;

import android.graphics.Color;

import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.plugintemplate.runtime.SearchRoutePlan;

import java.util.List;
import java.util.UUID;

public class SearchTeamAssignmentOverlay {

    private static final String GROUP_NAME = "SARtak Team Assignments";
    private static final int MAX_ASSIGNMENT_CELLS = 2500;

    private final MapView mapView;
    private final GridCoordinateConverter converter;
    private final SearchGridManager gridManager;
    private MapGroup assignmentGroup;
    private boolean visible = true;
    private String lastRenderKey = "";public SearchTeamAssignmentOverlay(MapView mapView,
            GridCoordinateConverter converter, SearchGridManager gridManager) {
        this.mapView = mapView;
        this.converter = converter;
        this.gridManager = gridManager;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean toggleVisible() {
        setVisible(!visible);
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        ensureGroup();
        if (!visible) {
            assignmentGroup.clearItems();
            assignmentGroup.setVisible(false);
            lastRenderKey = "";
        } else {
            assignmentGroup.setVisible(true);
        }
    }

    public void render(List<SearchRoutePlan> routePlans) {
        if (!visible)
            return;
        ensureGroup();
        if (routePlans == null) {
            if (lastRenderKey.length() > 0) {
                assignmentGroup.clearItems();
                lastRenderKey = "";
            }
            return;
        }
        String renderKey = buildRenderKey(routePlans);
        if (renderKey.equals(lastRenderKey))
            return;
        assignmentGroup.clearItems();
        lastRenderKey = renderKey;
        int rendered = 0;
        for (SearchRoutePlan plan : routePlans) {
            int color = plan.getTeamColorArgb() == 0
                    ? Color.rgb(138, 143, 152)
                    : plan.getTeamColorArgb();
            for (SearchGridCell cell : gridManager.cellsForIds(plan
                    .getCellIds())) {
                DrawingShape shape = createCellShape(plan, cell, color);
                assignmentGroup.addItem(shape);
                rendered++;
                if (rendered >= MAX_ASSIGNMENT_CELLS)
                    return;
            }
        }
    }

    private String buildRenderKey(List<SearchRoutePlan> routePlans) {
        StringBuilder builder = new StringBuilder();
        for (SearchRoutePlan plan : routePlans) {
            builder.append(plan.getPlanId()).append('|')
                    .append(plan.getUpdatedAt()).append('|')
                    .append(plan.getTeamColorArgb()).append('|')
                    .append(plan.getCellIds().size()).append('|');
            for (String cellId : plan.getCellIds())
                builder.append(cellId).append(',');
        }
        return builder.toString();
    }
    private DrawingShape createCellShape(SearchRoutePlan plan,
            SearchGridCell cell, int color) {
        DrawingShape shape = new DrawingShape(mapView, "sartak-assignment-"
                + UUID.randomUUID());
        shape.setTitle(plan.getTeamName() + " assignment");
        shape.setPoints(cell.toGeoPoints(converter));
        shape.setClosed(true);
        shape.setFillColor(withAlpha(color, 45));
        shape.setStrokeColor(withAlpha(color, 220));
        shape.setStrokeWeight(2.0);
        shape.setMetaString("sartak.kind", "team-assignment-cell");
        shape.setMetaString("sartak.teamId", plan.getTeamId());
        shape.setMetaString("sartak.teamName", plan.getTeamName());
        configure(shape);
        return shape;
    }

    private void ensureGroup() {
        if (assignmentGroup != null)
            return;
        assignmentGroup = mapView.getRootGroup().findMapGroup(GROUP_NAME);
        if (assignmentGroup == null)
            assignmentGroup = mapView.getRootGroup().addGroup(GROUP_NAME);
        assignmentGroup.setMetaBoolean("addToObjList", true);
    }

    private void configure(MapItem item) {
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
}



