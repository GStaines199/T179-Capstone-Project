package com.atakmap.android.plugintemplate.grid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A lane or box picked in route selection mode but not yet added to the
 * route. It is drawn on the map as a few merged rectangles and only joins
 * the route when the lead confirms it.
 * <p>
 * Free of ATAK types so it can be unit tested on a plain JVM.
 */
public final class RouteSelectionPreview {

    private final SearchGridCell anchor;
    private final SearchGridCell end;
    private final List<SearchGridCell> cells;
    private final boolean limited;
    private final int alreadyOnRoute;

    /**
     * @param cells the cells in route order, as returned by
     *        {@link SearchGridManager#cellsBetween}
     * @param maxCells the selection cap; reaching it means the box was cut
     * @param routeCellIds ids already on the route, or null when there is
     *        no route yet
     */
    public RouteSelectionPreview(SearchGridCell anchor, SearchGridCell end,
            List<SearchGridCell> cells, int maxCells,
            Collection<String> routeCellIds) {
        this.anchor = anchor;
        this.end = end;
        this.cells = cells == null ? Collections.<SearchGridCell>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(cells));
        this.limited = maxCells > 0 && this.cells.size() >= maxCells;
        int onRoute = 0;
        if (routeCellIds != null && !routeCellIds.isEmpty()) {
            for (SearchGridCell cell : this.cells) {
                if (routeCellIds.contains(cell.getId()))
                    onRoute++;
            }
        }
        this.alreadyOnRoute = onRoute;
    }

    public SearchGridCell getAnchor() {
        return anchor;
    }

    public SearchGridCell getEnd() {
        return end;
    }

    public List<SearchGridCell> getCells() {
        return cells;
    }

    public int getCellCount() {
        return cells.size();
    }

    /** Cells the route does not have yet; confirming adds only these. */
    public int getNewCellCount() {
        return cells.size() - alreadyOnRoute;
    }

    public boolean isEmpty() {
        return cells.isEmpty();
    }

    /** True when the selection cap cut the box short. */
    public boolean isLimited() {
        return limited;
    }

    /** A single row or column of cells rather than a box. */
    public boolean isLane() {
        return anchor != null && end != null
                && (Math.abs(anchor.getWest() - end.getWest()) < 0.1
                        || Math.abs(anchor.getSouth() - end.getSouth()) < 0.1);
    }

    public String getLabel() {
        return isLane() ? "Lane" : "Box";
    }

    /** One line for the route summary and the confirm button. */
    public String describe() {
        StringBuilder builder = new StringBuilder();
        builder.append(getLabel()).append(" preview: ").append(cells.size())
                .append(cells.size() == 1 ? " cell" : " cells");
        if (alreadyOnRoute > 0)
            builder.append(" (").append(alreadyOnRoute)
                    .append(" already on route)");
        if (limited)
            builder.append(" (selection limit reached)");
        return builder.toString();
    }

    /**
     * The selection as merged rectangles keyed by UTM zone, so even the
     * largest box draws as a handful of map items.
     */
    public List<GridCellRuns.Rect> blocks() {
        List<GridCellRuns.Cell> gridCells = new ArrayList<>(cells.size());
        double size = GridCoordinateConverter.BASE_CELL_SIZE_METERS;
        for (SearchGridCell cell : cells)
            gridCells.add(new GridCellRuns.Cell(
                    (int) Math.floor(cell.getWest() / size + 1e-6),
                    (int) Math.floor(cell.getSouth() / size + 1e-6),
                    cell.getZoneDescriptor()));
        return GridCellRuns.merge(gridCells);
    }
}
