package com.atakmap.android.plugintemplate.grid;

import android.graphics.Color;

import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.List;

public class SearchGridOverlay {

    private static final String GROUP_NAME = "SARtak Search Grid Overlay";
    private static final double REFERENCE_GRID_MAX_RESOLUTION_METERS = 250.0;

    private final MapView mapView;
    private final GridCoordinateConverter converter;
    private final SearchPartyAssignmentManager assignmentManager;
    private MapGroup overlayGroup;
    private boolean visible;
    private boolean showLabels;
    private String lastRenderKey = "";

    public SearchGridOverlay(MapView mapView, GridCoordinateConverter converter,
            SearchPartyAssignmentManager assignmentManager) {
        this.mapView = mapView;
        this.converter = converter;
        this.assignmentManager = assignmentManager;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        ensureOverlayGroup();
        if (!visible) {
            overlayGroup.clearItems();
            overlayGroup.setVisible(false);
            lastRenderKey = "";
        } else {
            overlayGroup.setVisible(true);
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean toggleLabels() {
        showLabels = !showLabels;
        lastRenderKey = "";
        return showLabels;
    }

    public boolean isShowingLabels() {
        return showLabels;
    }

    public void render(SearchGridManager gridManager) {
        if (!visible)
            return;

        ensureOverlayGroup();
        List<SearchGridCell> cells = gridManager.getSelectedAggregateCells();
        if (cells.isEmpty()) {
            if (lastRenderKey.length() > 0) {
                overlayGroup.clearItems();
                lastRenderKey = "";
            }
            return;
        }

        SearchGridCell selectedCell = gridManager.getSelectedCell();
        boolean show100mReference = shouldRender100mReference();
        String renderKey = buildRenderKey(cells, selectedCell,
                show100mReference);
        if (renderKey.equals(lastRenderKey))
            return;

        overlayGroup.clearItems();
        lastRenderKey = renderKey;
        if (show100mReference) {
            renderReferenceGridLines(cells);
            renderDetailedCells(cells, selectedCell);
            if (selectedCell != null)
                renderSelectedCellLanes(selectedCell);
        } else {
            renderAggregate(cells, gridManager.getAggregateStatus(cells));
        }
    }

    private String buildRenderKey(List<SearchGridCell> cells,
            SearchGridCell selectedCell, boolean show100mReference) {
        StringBuilder builder = new StringBuilder();
        builder.append(show100mReference ? "detail" : "aggregate")
                .append("|labels=").append(showLabels)
                .append("|lanes=").append(assignmentManager
                        .getLaneMemberCount())
                .append("|selected=")
                .append(selectedCell == null ? "" : selectedCell.getId());
        for (SearchGridCell cell : cells) {
            builder.append("|").append(cell.getId()).append(":")
                    .append(cell.getStatus().name());
        }
        return builder.toString();
    }

    private void renderDetailedCells(List<SearchGridCell> cells,
            SearchGridCell selectedCell) {
        for (SearchGridCell cell : cells) {
            boolean selected = selectedCell != null
                    && selectedCell.getId().equals(cell.getId());
            boolean marked = cell.getStatus() == SearchGridStatus.PARTIAL
                    || cell.getStatus() == SearchGridStatus.COMPLETE;
            // Do not redraw the whole 100 m grid over ATAK's own grid. At
            // detailed zoom we only draw the active cell outline and any cells
            // that have explicit manual progress colour.
            if (!selected && !marked)
                continue;

            int fill = fillForStatus(cell.getStatus());
            int stroke = selected ? Color.rgb(255, 255, 255)
                    : Color.argb(130, 216, 182, 76);
            double weight = selected ? 4.0 : 1.0;
            DrawingShape shape = createShape(cell.getId(),
                    cell.toGeoPoints(converter), fill, stroke, weight);
            shape.setMetaString("sartak.kind", "search-cell");
            overlayGroup.addItem(shape);
        }
    }

    private void renderReferenceGridLines(List<SearchGridCell> cells) {
        SearchGridCell first = cells.get(0);
        SearchGridCell last = cells.get(cells.size() - 1);
        int color = Color.argb(75, 255, 255, 255);

        for (int i = 0; i <= GridCoordinateConverter.AGGREGATE_CELLS_PER_SIDE; i++) {
            double x = first.getWest() + i
                    * GridCoordinateConverter.BASE_CELL_SIZE_METERS;
            DrawingShape line = createLine("100m Grid E " + i,
                    new GeoPoint[] {
                            converter.toGeoPoint(first.getZoneDescriptor(), x,
                                    first.getSouth()),
                            converter.toGeoPoint(first.getZoneDescriptor(), x,
                                    last.getNorth())
                    },
                    color, 1.0);
            line.setMetaString("sartak.kind", "search-grid-reference");
            overlayGroup.addItem(line);

            double y = first.getSouth() + i
                    * GridCoordinateConverter.BASE_CELL_SIZE_METERS;
            line = createLine("100m Grid N " + i,
                    new GeoPoint[] {
                            converter.toGeoPoint(first.getZoneDescriptor(),
                                    first.getWest(), y),
                            converter.toGeoPoint(first.getZoneDescriptor(),
                                    last.getEast(), y)
                    },
                    color, 1.0);
            line.setMetaString("sartak.kind", "search-grid-reference");
            overlayGroup.addItem(line);
        }
    }

    private void renderAggregate(List<SearchGridCell> cells,
            SearchGridStatus aggregateStatus) {
        SearchGridCell first = cells.get(0);
        SearchGridCell last = cells.get(cells.size() - 1);
        GeoPoint[] bounds = new GeoPoint[] {
                converter.toGeoPoint(first.getZoneDescriptor(), first.getWest(),
                        first.getSouth()),
                converter.toGeoPoint(first.getZoneDescriptor(), last.getEast(),
                        first.getSouth()),
                converter.toGeoPoint(first.getZoneDescriptor(), last.getEast(),
                        last.getNorth()),
                converter.toGeoPoint(first.getZoneDescriptor(), first.getWest(),
                        last.getNorth())
        };

        DrawingShape aggregate = createShape("SARtak Aggregate Summary", bounds,
                aggregateFillForStatus(aggregateStatus),
                Color.argb(190, 255, 255, 255), 3.0);
        aggregate.setMetaString("sartak.kind", "search-grid-aggregate");
        overlayGroup.addItem(aggregate);
    }

    private void renderSelectedCellLanes(SearchGridCell cell) {
        int lanes = assignmentManager.getLaneMemberCount();
        double laneWidth = (cell.getEast() - cell.getWest()) / lanes;
        // The only subdivision SARtak creates inside a base 100 m cell is the
        // search-lane split for the current team size.
        for (int i = 1; i < lanes; i++) {
            double x = cell.getWest() + laneWidth * i;
            DrawingShape lane = createLine("Lane " + i,
                    new GeoPoint[] {
                            converter.toGeoPoint(cell.getZoneDescriptor(), x,
                                    cell.getSouth()),
                            converter.toGeoPoint(cell.getZoneDescriptor(), x,
                                    cell.getNorth())
                    },
                    Color.argb(180, 255, 255, 255), 2.0);
            lane.setMetaString("sartak.kind", "search-lane-divider");
            overlayGroup.addItem(lane);
        }
    }

    private boolean shouldRender100mReference() {
        return mapView.getMapResolution() <= REFERENCE_GRID_MAX_RESOLUTION_METERS;
    }

    private int fillForStatus(SearchGridStatus status) {
        switch (status) {
            case PARTIAL:
                return Color.argb(75, 79, 170, 255);
            case COMPLETE:
                return Color.argb(85, 66, 195, 106);
            case IN_PROGRESS:
            case NOT_STARTED:
            default:
                return Color.argb(0, 0, 0, 0);
        }
    }

    private int aggregateFillForStatus(SearchGridStatus status) {
        switch (status) {
            case COMPLETE:
                return Color.argb(85, 66, 195, 106);
            case IN_PROGRESS:
                return Color.argb(65, 150, 150, 150);
            case PARTIAL:
                return Color.argb(75, 79, 170, 255);
            case NOT_STARTED:
            default:
                return Color.argb(0, 0, 0, 0);
        }
    }

    private DrawingShape createShape(String title, GeoPoint[] points, int fillColor,
            int strokeColor, double strokeWeight) {
        DrawingShape shape = new DrawingShape(mapView, uid(title));
        shape.setTitle(title);
        shape.setPoints(points);
        shape.setClosed(true);
        shape.setFillColor(fillColor);
        shape.setStrokeColor(strokeColor);
        shape.setStrokeWeight(strokeWeight);
        configureMapItem(shape);
        return shape;
    }

    private DrawingShape createLine(String title, GeoPoint[] points, int strokeColor,
            double strokeWeight) {
        DrawingShape line = new DrawingShape(mapView, uid(title));
        line.setTitle(title);
        line.setPoints(points);
        line.setClosed(false);
        line.setStrokeColor(strokeColor);
        line.setStrokeWeight(strokeWeight);
        configureMapItem(line);
        return line;
    }

    private void ensureOverlayGroup() {
        if (overlayGroup != null)
            return;
        overlayGroup = mapView.getRootGroup().findMapGroup(GROUP_NAME);
        if (overlayGroup == null)
            overlayGroup = mapView.getRootGroup().addGroup(GROUP_NAME);
        overlayGroup.setMetaBoolean("addToObjList", true);
    }

    private void configureMapItem(MapItem item) {
        item.setClickable(false);
        item.setMetaBoolean("archive", false);
        item.setMetaBoolean("editable", false);
        item.setMetaBoolean("movable", false);
        item.setMetaBoolean("removable", true);
        item.setMetaString("entry", "sartak");
        item.setMetaBoolean("labels_on", showLabels);
        item.setMetaBoolean("label_on", showLabels);
        item.setMetaBoolean("showLabel", showLabels);
        item.setMetaString("callsign", showLabels ? item.getTitle() : "");
        if (!showLabels)
            item.setTitle("");
    }

    private String uid(String title) {
        return "sartak-grid-" + title.toLowerCase().replace(' ', '-');
    }
}
