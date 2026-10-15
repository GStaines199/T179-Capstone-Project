package com.atakmap.android.plugintemplate.grid;

import android.graphics.Color;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the planned search area and its 100 m grid.
 * <p>
 * Only content that can currently be seen is drawn: the reference grid and
 * searched cells are limited to a render window around the visible map, and
 * searched cells are merged into rectangles. Nothing here walks every cell of
 * the planned area, so the cost does not grow with the area's size.
 */
public class SearchGridOverlay {

    private static final String GROUP_NAME = "SARtak Search Grid Overlay";
    private static final double REFERENCE_GRID_MAX_RESOLUTION_METERS = 250.0;
    private static final int MAX_REFERENCE_LINES = 120;
    /** Extra map drawn around the view so short pans need no re-render. */
    private static final double RENDER_WINDOW_PAD_FRACTION = 0.5;
    private static final double RENDER_WINDOW_SNAP_METERS = 500.0;
    private static final int MAX_RENDERED_CELL_BLOCKS = 400;
    private static final String UID_PREFIX = "sartak-grid-";

    private final MapView mapView;
    private final GridCoordinateConverter converter;
    private final SearchPartyAssignmentManager assignmentManager;
    private final OverlayItemSync itemSync = new OverlayItemSync();
    private MapGroup overlayGroup;
    private boolean visible;
    private boolean showLabels;
    private boolean selectionMode;
    private SearchGridCell routeSelectionAnchor;
    private int gridColorArgb = Color.argb(220, 74, 163, 255);
    private String lastRenderKey = "";
    private String lastDiagnostics = "Grid overlay not rendered yet";
    private String cachedBlocksVersion = "";
    private List<GridCellRuns.Rect> cachedBlocks =
            new ArrayList<GridCellRuns.Rect>();

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
            itemSync.clear(overlayGroup);
            overlayGroup.setVisible(false);
            lastRenderKey = "";
        } else {
            overlayGroup.setVisible(true);
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public MapGroup getMapGroup() {
        return overlayGroup;
    }

    public boolean toggleLabels() {
        showLabels = !showLabels;
        lastRenderKey = "";
        return showLabels;
    }

    public boolean isShowingLabels() {
        return showLabels;
    }

    public void setSelectionMode(boolean selectionMode) {
        if (this.selectionMode != selectionMode)
            lastRenderKey = "";
        this.selectionMode = selectionMode;
        if (!selectionMode)
            routeSelectionAnchor = null;
    }

    public void setRouteSelectionAnchor(SearchGridCell anchor) {
        routeSelectionAnchor = anchor;
        lastRenderKey = "";
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public void setGridColor(int gridColorArgb) {
        this.gridColorArgb = gridColorArgb;
        lastRenderKey = "";
    }

    public String getLastDiagnostics() {
        return lastDiagnostics;
    }

    public void render(SearchGridManager gridManager) {
        if (!visible) {
            lastDiagnostics = "Grid overlay hidden";
            return;
        }

        ensureOverlayGroup();
        SearchGridManager.GridExtent area = gridManager.getAreaExtent();
        double resolution = mapView.getMapResolution();
        if (area == null) {
            if (itemSync.size() > 0)
                itemSync.clear(overlayGroup);
            lastRenderKey = "";
            lastDiagnostics = "Grid overlay: no search area | resolution="
                    + Math.round(resolution) + " m";
            return;
        }

        boolean detail = resolution <= REFERENCE_GRID_MAX_RESOLUTION_METERS;
        GeoBounds view = mapView.getBounds();
        SearchGridManager.GridExtent visibleArea = detail
                ? gridManager.clipToView(area, view, 0.0,
                        GridCoordinateConverter.BASE_CELL_SIZE_METERS)
                : null;
        SearchGridManager.GridExtent window = detail
                ? gridManager.clipToView(area, view,
                        RENDER_WINDOW_PAD_FRACTION, RENDER_WINDOW_SNAP_METERS)
                : null;
        boolean drawReferenceLines = visibleArea != null
                && visibleArea.getColumns() + visibleArea.getRows()
                        <= MAX_REFERENCE_LINES;
        SearchGridCell selectedCell = gridManager.getSelectedCell();
        String areaVersion = gridManager.getAreaVersion();

        String renderKey = (detail ? "detail" : "aggregate")
                + "|lines=" + drawReferenceLines
                + "|window=" + (window == null ? "" : window.key())
                + "|area=" + areaVersion
                + "|labels=" + showLabels
                + "|select=" + selectionMode
                + "|gridColor=" + gridColorArgb
                + "|lanes=" + assignmentManager.getLaneMemberCount()
                + "|selected=" + describe(selectedCell)
                + "|anchor=" + describe(routeSelectionAnchor);
        if (renderKey.equals(lastRenderKey))
            return;
        lastRenderKey = renderKey;

        List<OverlayItemSync.Spec> specs = new ArrayList<>();
        int blocksDrawn = 0;
        boolean blocksCapped = false;
        addAreaOutline(specs, gridManager);
        if (detail) {
            if (drawReferenceLines && window != null)
                addReferenceLines(specs, window);
            if (window != null) {
                for (GridCellRuns.Rect block : getCellBlocks(gridManager,
                        areaVersion)) {
                    if (!intersects(block, window))
                        continue;
                    if (blocksDrawn >= MAX_RENDERED_CELL_BLOCKS) {
                        blocksCapped = true;
                        break;
                    }
                    addCellBlock(specs, block, area.zone);
                    blocksDrawn++;
                }
            }
            if (selectedCell != null) {
                addSelectedCell(specs, selectedCell);
                addSelectedCellLanes(specs, selectedCell);
            }
            if (routeSelectionAnchor != null)
                addAnchorCell(specs, routeSelectionAnchor);
        } else {
            addAggregate(specs, gridManager, area);
        }
        itemSync.apply(overlayGroup, specs, showLabels);

        lastDiagnostics = "Grid overlay: planned="
                + gridManager.getPlannedCellEstimate()
                + " | items=" + itemSync.size()
                + " (+" + itemSync.getLastAdded()
                + "/-" + itemSync.getLastRemoved() + ")"
                + " | searched blocks=" + blocksDrawn
                + (blocksCapped ? " (capped)" : "")
                + " | window=" + (window == null ? "none"
                        : window.getColumns() + "x" + window.getRows())
                + " | resolution=" + Math.round(resolution) + " m"
                + (selectionMode ? " | selection on" : "");
    }

    private List<GridCellRuns.Rect> getCellBlocks(
            SearchGridManager gridManager, String areaVersion) {
        if (areaVersion.equals(cachedBlocksVersion))
            return cachedBlocks;
        List<GridCellRuns.Cell> cells = new ArrayList<>();
        for (SearchGridCell cell : gridManager.getMarkedCellsInArea()) {
            cells.add(new GridCellRuns.Cell(columnOf(cell.getWest()),
                    rowOf(cell.getSouth()), cell.getStatus().name()));
        }
        cachedBlocks = GridCellRuns.merge(cells);
        cachedBlocksVersion = areaVersion;
        return cachedBlocks;
    }

    private void addAreaOutline(List<OverlayItemSync.Spec> specs,
            SearchGridManager gridManager) {
        if (!gridManager.hasPlannedArea())
            return;
        GeoPoint[] points = gridManager.getPlannedAreaOutlinePoints();
        if (points.length == 0)
            return;
        specs.add(new OverlayItemSync.Spec(UID_PREFIX + "planned-area",
                "SARtak Planned Search Area", "search-area-outline", points,
                true, withAlpha(gridColorArgb, 20),
                withAlpha(gridColorArgb, 220), 3.0));
    }

    private void addAggregate(List<OverlayItemSync.Spec> specs,
            SearchGridManager gridManager,
            SearchGridManager.GridExtent area) {
        GeoPoint[] points = gridManager.hasPlannedArea()
                ? gridManager.getPlannedAreaOutlinePoints()
                : rectangle(area.zone, area.west, area.south, area.east,
                        area.north);
        SearchGridStatus status = gridManager.getAreaProgress()
                .getAggregateStatus();
        specs.add(new OverlayItemSync.Spec(UID_PREFIX + "aggregate-summary",
                "SARtak Aggregate Summary", "search-grid-aggregate", points,
                true, aggregateFillForStatus(status),
                withAlpha(gridColorArgb, 190), 3.0));
    }

    /**
     * Draws every 100 m line in the window as two polylines (one for each
     * direction) that zig-zag along the window edge between lines. The
     * connecting segments lie on grid lines themselves, so they are hidden by
     * the lines they join.
     */
    private void addReferenceLines(List<OverlayItemSync.Spec> specs,
            SearchGridManager.GridExtent window) {
        int color = withAlpha(gridColorArgb, selectionMode ? 140 : 75);
        double size = GridCoordinateConverter.BASE_CELL_SIZE_METERS;
        List<GeoPoint> eastLines = new ArrayList<>();
        boolean up = true;
        for (int i = 0; i <= window.getColumns(); i++) {
            double x = window.west + i * size;
            eastLines.add(converter.toGeoPoint(window.zone, x,
                    up ? window.south : window.north));
            eastLines.add(converter.toGeoPoint(window.zone, x,
                    up ? window.north : window.south));
            up = !up;
        }
        List<GeoPoint> northLines = new ArrayList<>();
        boolean right = true;
        for (int i = 0; i <= window.getRows(); i++) {
            double y = window.south + i * size;
            northLines.add(converter.toGeoPoint(window.zone,
                    right ? window.west : window.east, y));
            northLines.add(converter.toGeoPoint(window.zone,
                    right ? window.east : window.west, y));
            right = !right;
        }
        specs.add(new OverlayItemSync.Spec(UID_PREFIX + "reference-e",
                "100m Grid", "search-grid-reference",
                eastLines.toArray(new GeoPoint[0]), false, 0, color, 1.0)
                        .unlabelled());
        specs.add(new OverlayItemSync.Spec(UID_PREFIX + "reference-n",
                "100m Grid", "search-grid-reference",
                northLines.toArray(new GeoPoint[0]), false, 0, color, 1.0)
                        .unlabelled());
    }

    private void addCellBlock(List<OverlayItemSync.Spec> specs,
            GridCellRuns.Rect block, String zone) {
        double size = GridCoordinateConverter.BASE_CELL_SIZE_METERS;
        double west = block.getFirstColumn() * size;
        double south = block.getFirstRow() * size;
        double east = (block.getLastColumn() + 1) * size;
        double north = (block.getLastRow() + 1) * size;
        SearchGridStatus status = SearchGridStatus.valueOf(block.getKey());
        String label = block.getCellCount() == 1
                ? converter.cellIdForUtmPoint(zone, west, south)
                : statusLabel(status) + " (" + block.getCellCount()
                        + " cells)";
        specs.add(new OverlayItemSync.Spec(UID_PREFIX + "block-"
                + block.getKey().toLowerCase() + "-" + zone + "-"
                + block.getFirstColumn() + "-" + block.getFirstRow() + "-"
                + block.getLastColumn() + "-" + block.getLastRow(), label,
                "search-cell", rectangle(zone, west, south, east, north), true,
                fillForStatus(status), withAlpha(gridColorArgb, 130), 1.0));
    }

    private void addSelectedCell(List<OverlayItemSync.Spec> specs,
            SearchGridCell cell) {
        // Searched cells already carry their status fill as part of a block,
        // so the selection is drawn as an outline only.
        specs.add(new OverlayItemSync.Spec(UID_PREFIX + "selected-cell",
                cell.getId(), "search-cell", cell.toGeoPoints(converter), true,
                Color.argb(0, 0, 0, 0), Color.rgb(255, 255, 255), 4.0)
                        .meta("sartak.cellId", cell.getId()));
    }

    private void addAnchorCell(List<OverlayItemSync.Spec> specs,
            SearchGridCell cell) {
        specs.add(new OverlayItemSync.Spec(UID_PREFIX + "route-anchor",
                cell.getId(), "search-cell", cell.toGeoPoints(converter), true,
                withAlpha(gridColorArgb, 65), Color.rgb(255, 255, 255), 4.0)
                        .meta("sartak.cellId", cell.getId()));
    }

    private void addSelectedCellLanes(List<OverlayItemSync.Spec> specs,
            SearchGridCell cell) {
        int lanes = assignmentManager.getLaneMemberCount();
        double laneWidth = (cell.getEast() - cell.getWest()) / lanes;
        // The only subdivision SARtak creates inside a base 100 m cell is the
        // search-lane split for the current team size.
        for (int i = 1; i < lanes; i++) {
            double x = cell.getWest() + laneWidth * i;
            specs.add(new OverlayItemSync.Spec(UID_PREFIX + "lane-" + i,
                    "Lane " + i, "search-lane-divider",
                    new GeoPoint[] {
                            converter.toGeoPoint(cell.getZoneDescriptor(), x,
                                    cell.getSouth()),
                            converter.toGeoPoint(cell.getZoneDescriptor(), x,
                                    cell.getNorth())
                    }, false, 0, Color.argb(180, 255, 255, 255), 2.0));
        }
    }

    private GeoPoint[] rectangle(String zone, double west, double south,
            double east, double north) {
        return new GeoPoint[] {
                converter.toGeoPoint(zone, west, south),
                converter.toGeoPoint(zone, east, south),
                converter.toGeoPoint(zone, east, north),
                converter.toGeoPoint(zone, west, north)
        };
    }

    private static boolean intersects(GridCellRuns.Rect block,
            SearchGridManager.GridExtent window) {
        return block.intersects(columnOf(window.west), rowOf(window.south),
                columnOf(window.east) - 1, rowOf(window.north) - 1);
    }

    private static int columnOf(double easting) {
        return (int) Math.floor(easting
                / GridCoordinateConverter.BASE_CELL_SIZE_METERS + 1e-6);
    }

    private static int rowOf(double northing) {
        return (int) Math.floor(northing
                / GridCoordinateConverter.BASE_CELL_SIZE_METERS + 1e-6);
    }

    private static String describe(SearchGridCell cell) {
        return cell == null ? "" : cell.getId() + ":" + cell.getStatus();
    }

    private static String statusLabel(SearchGridStatus status) {
        return status == SearchGridStatus.COMPLETE ? "Complete" : "Partial";
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

    private void ensureOverlayGroup() {
        if (overlayGroup != null)
            return;
        overlayGroup = mapView.getRootGroup().findMapGroup(GROUP_NAME);
        if (overlayGroup == null)
            overlayGroup = mapView.getRootGroup().addGroup(GROUP_NAME);
        overlayGroup.setMetaBoolean("addToObjList", true);
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color),
                Color.blue(color));
    }
}
