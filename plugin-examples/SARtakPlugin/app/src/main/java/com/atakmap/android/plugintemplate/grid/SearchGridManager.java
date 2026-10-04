package com.atakmap.android.plugintemplate.grid;

import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.UTMPoint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SearchGridManager {

    public enum PlannedAreaShape {
        NONE,
        BOX,
        CIRCLE
    }

    /** A UTM rectangle snapped to the 100 m grid. */
    public static final class GridExtent {
        public final String zone;
        public final double west;
        public final double south;
        public final double east;
        public final double north;

        public GridExtent(String zone, double west, double south, double east,
                double north) {
            this.zone = zone == null ? "" : zone;
            this.west = west;
            this.south = south;
            this.east = east;
            this.north = north;
        }

        public int getColumns() {
            return Math.max(0, (int) Math.round((east - west)
                    / GridCoordinateConverter.BASE_CELL_SIZE_METERS));
        }

        public int getRows() {
            return Math.max(0, (int) Math.round((north - south)
                    / GridCoordinateConverter.BASE_CELL_SIZE_METERS));
        }

        public boolean isEmpty() {
            return east <= west || north <= south;
        }

        public String key() {
            return zone + ":" + Math.round(west) + ":" + Math.round(south)
                    + ":" + Math.round(east) + ":" + Math.round(north);
        }
    }

    /** Stored progress for the cells inside the current search area. */
    public static final class AreaProgress {
        public final int complete;
        public final int partial;
        public final int inProgress;
        public final int totalCells;

        AreaProgress(int complete, int partial, int inProgress,
                int totalCells) {
            this.complete = complete;
            this.partial = partial;
            this.inProgress = inProgress;
            this.totalCells = totalCells;
        }

        public SearchGridStatus getAggregateStatus() {
            if (totalCells > 0 && complete >= totalCells)
                return SearchGridStatus.COMPLETE;
            return complete + partial > 0 ? SearchGridStatus.IN_PROGRESS
                    : SearchGridStatus.NOT_STARTED;
        }
    }

    private static final int MAX_RENDER_CELLS = 600;
    private static final int MAX_REVIEW_CELLS = 200;

    private final GridCoordinateConverter converter;
    private final SearchGridStateStore stateStore;
    private SearchGridCell selectedCell;
    private String selectedAggregateId;
    private SearchGridCell pendingReviewCell;
    private final Map<String, SearchGridCell> reviewedCells =
            new LinkedHashMap<>();
    private List<SearchGridCell> cachedMarkedCells =
            new ArrayList<SearchGridCell>();
    private AreaProgress cachedAreaProgress;
    private String cachedAreaVersion;

    private PlannedAreaShape plannedShape = PlannedAreaShape.NONE;
    private String plannedZone = "";
    private double plannedCenterEasting;
    private double plannedCenterNorthing;
    private double plannedWest;
    private double plannedSouth;
    private double plannedEast;
    private double plannedNorth;
    private double plannedRadiusMeters;
    private double plannedWidthMeters;
    private double plannedHeightMeters;

    public SearchGridManager(GridCoordinateConverter converter,
            SearchGridStateStore stateStore) {
        this.converter = converter;
        this.stateStore = stateStore;
        loadOperationState();
    }

    public SearchGridCell selectCellAt(GeoPoint point) {
        UTMPoint utm = UTMPoint.fromGeoPoint(point);
        return selectCell(converter.cellForUtmPoint(utm.getZoneDescriptor(),
                utm.getEasting(), utm.getNorthing(), stateStore));
    }

    public SearchGridCell cellAt(GeoPoint point) {
        if (point == null)
            return null;
        return converter.cellForPoint(point, stateStore);
    }

    SearchGridCell selectCell(SearchGridCell cell) {
        if (cell == null)
            return selectedCell;
        selectedCell = cell;
        selectedAggregateId = cell.getAggregateId();
        if (cell.getStatus() == SearchGridStatus.NOT_STARTED) {
            cell.setStatus(SearchGridStatus.IN_PROGRESS);
            stateStore.setStatus(cell.getId(), SearchGridStatus.IN_PROGRESS);
        }
        return cell;
    }

    public SearchGridCell getSelectedCell() {
        return selectedCell;
    }

    public void refreshSelectedCellStatus() {
        if (selectedCell != null)
            selectedCell.setStatus(stateStore.getStatus(selectedCell.getId()));
    }

    public String getSelectedAggregateId() {
        return selectedAggregateId;
    }

    public void setSelectedStatus(SearchGridStatus status) {
        if (selectedCell == null)
            return;
        setCellStatus(selectedCell.getId(), status);
    }

    public void setCellStatus(String cellId, SearchGridStatus status) {
        if (cellId == null || cellId.length() == 0 || status == null)
            return;
        SearchGridCell cell = knownOrParsedCell(cellId);
        if (cell != null)
            cell.setStatus(status);
        if (selectedCell != null && cellId.equals(selectedCell.getId()))
            selectedCell.setStatus(status);
        if (pendingReviewCell != null && cellId.equals(pendingReviewCell
                .getId()))
            pendingReviewCell.setStatus(status);
        if (status == SearchGridStatus.PARTIAL
                || status == SearchGridStatus.COMPLETE) {
            if (cell != null)
                reviewedCells.put(cellId, cell);
        } else {
            reviewedCells.remove(cellId);
        }
        if (status == SearchGridStatus.NOT_STARTED)
            stateStore.clearStatus(cellId);
        else
            stateStore.setStatus(cellId, status);
    }

    public void planSelectedAggregateArea() {
        if (selectedCell == null)
            return;
        planBoxFromCell(selectedCell, 1.0, 1.0);
    }

    public void planBoxAt(GeoPoint center, double widthKm, double heightKm) {
        if (center == null)
            return;
        SearchGridCell centerCell = selectCellAt(center);
        planBoxFromCell(centerCell, widthKm, heightKm);
    }

    public void planCircleAt(GeoPoint center, double radiusKm) {
        if (center == null)
            return;
        SearchGridCell centerCell = selectCellAt(center);
        UTMPoint utm = UTMPoint.fromGeoPoint(center);
        plannedShape = PlannedAreaShape.CIRCLE;
        plannedZone = utm.getZoneDescriptor();
        plannedCenterEasting = utm.getEasting();
        plannedCenterNorthing = utm.getNorthing();
        plannedRadiusMeters = Math.max(100.0, radiusKm * 1000.0);
        plannedWidthMeters = plannedRadiusMeters * 2.0;
        plannedHeightMeters = plannedRadiusMeters * 2.0;
        plannedWest = floorToGrid(plannedCenterEasting - plannedRadiusMeters,
                GridCoordinateConverter.BASE_CELL_SIZE_METERS);
        plannedSouth = floorToGrid(plannedCenterNorthing - plannedRadiusMeters,
                GridCoordinateConverter.BASE_CELL_SIZE_METERS);
        plannedEast = ceilToGrid(plannedCenterEasting + plannedRadiusMeters,
                GridCoordinateConverter.BASE_CELL_SIZE_METERS);
        plannedNorth = ceilToGrid(plannedCenterNorthing + plannedRadiusMeters,
                GridCoordinateConverter.BASE_CELL_SIZE_METERS);
        selectedCell = centerCell;
        selectedAggregateId = centerCell.getAggregateId();
        persistPlannedArea();
    }

    public boolean hasPlannedArea() {
        return plannedShape != PlannedAreaShape.NONE;
    }

    public PlannedAreaShape getPlannedShape() {
        return plannedShape;
    }

    public void clearPlannedArea() {
        plannedShape = PlannedAreaShape.NONE;
        plannedZone = "";
        pendingReviewCell = null;
        stateStore.clearPlannedArea();
    }

    public void loadOperationState() {
        loadReviewedCellsFromStore();
        SearchGridStateStore.PlannedAreaRecord record =
                stateStore.loadPlannedArea();
        if (record == null) {
            resetPlannedAreaFields();
            return;
        }
        try {
            plannedShape = PlannedAreaShape.valueOf(record.shape);
        } catch (IllegalArgumentException ignored) {
            resetPlannedAreaFields();
            return;
        }
        if (plannedShape == PlannedAreaShape.NONE) {
            resetPlannedAreaFields();
            return;
        }
        plannedZone = record.zone == null ? "" : record.zone;
        plannedCenterEasting = record.centerEasting;
        plannedCenterNorthing = record.centerNorthing;
        plannedWest = record.west;
        plannedSouth = record.south;
        plannedEast = record.east;
        plannedNorth = record.north;
        plannedRadiusMeters = record.radiusMeters;
        plannedWidthMeters = record.widthMeters;
        plannedHeightMeters = record.heightMeters;
        selectedCell = converter.cellForUtmPoint(plannedZone,
                plannedCenterEasting, plannedCenterNorthing, stateStore);
        selectedAggregateId = selectedCell == null ? null
                : selectedCell.getAggregateId();
    }

    public SearchGridStateStore.PlannedAreaRecord getPlannedAreaRecord() {
        if (!hasPlannedArea())
            return null;
        return new SearchGridStateStore.PlannedAreaRecord(plannedShape.name(),
                plannedZone, plannedCenterEasting, plannedCenterNorthing,
                plannedWest, plannedSouth, plannedEast, plannedNorth,
                plannedRadiusMeters, plannedWidthMeters, plannedHeightMeters);
    }

    public boolean restorePlannedArea(
            SearchGridStateStore.PlannedAreaRecord record) {
        if (record == null || record.shape == null
                || record.shape.length() == 0)
            return false;
        try {
            plannedShape = PlannedAreaShape.valueOf(record.shape);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        if (plannedShape == PlannedAreaShape.NONE)
            return false;
        plannedZone = record.zone == null ? "" : record.zone;
        plannedCenterEasting = record.centerEasting;
        plannedCenterNorthing = record.centerNorthing;
        plannedWest = record.west;
        plannedSouth = record.south;
        plannedEast = record.east;
        plannedNorth = record.north;
        plannedRadiusMeters = record.radiusMeters;
        plannedWidthMeters = record.widthMeters;
        plannedHeightMeters = record.heightMeters;
        selectedCell = converter.cellForUtmPoint(plannedZone,
                plannedCenterEasting, plannedCenterNorthing, stateStore);
        selectedAggregateId = selectedCell == null ? null
                : selectedCell.getAggregateId();
        persistPlannedArea();
        return true;
    }

    public int getPlannedCellEstimate() {
        if (!hasPlannedArea())
            return 0;
        int columns = Math.max(0, (int) Math.ceil((plannedEast - plannedWest)
                / GridCoordinateConverter.BASE_CELL_SIZE_METERS));
        int rows = Math.max(0, (int) Math.ceil((plannedNorth - plannedSouth)
                / GridCoordinateConverter.BASE_CELL_SIZE_METERS));
        if (plannedShape == PlannedAreaShape.CIRCLE)
            return (int) Math.round(Math.PI * plannedRadiusMeters
                    * plannedRadiusMeters
                    / (GridCoordinateConverter.BASE_CELL_SIZE_METERS
                            * GridCoordinateConverter.BASE_CELL_SIZE_METERS));
        return columns * rows;
    }

    public Map<SearchGridStatus, Integer> getKnownStatusCounts() {
        Map<SearchGridStatus, Integer> counts =
                new LinkedHashMap<SearchGridStatus, Integer>();
        for (SearchGridStatus status : SearchGridStatus.values())
            counts.put(status, 0);
        for (SearchGridStatus status : stateStore.getKnownStatuses()
                .values()) {
            Integer count = counts.get(status);
            counts.put(status, count == null ? 1 : count + 1);
        }
        return counts;
    }

    public int getKnownStatusCount(SearchGridStatus status) {
        Integer count = getKnownStatusCounts().get(status);
        return count == null ? 0 : count;
    }

    public String getPlannedAreaDescription() {
        if (!hasPlannedArea())
            return "No planned search area";
        if (plannedShape == PlannedAreaShape.CIRCLE)
            return "Circle radius "
                    + Math.round(plannedRadiusMeters / 100.0) / 10.0
                    + " km";
        return "Box " + Math.round(plannedWidthMeters / 100.0) / 10.0
                + " km x " + Math.round(plannedHeightMeters / 100.0) / 10.0
                + " km";
    }

    public List<SearchGridCell> getSerpentineRouteCells() {
        List<SearchGridCell> cells = new ArrayList<>();
        if (!hasPlannedArea())
            return cells;
        boolean northbound = true;
        for (double x = plannedWest; x < plannedEast; x +=
                GridCoordinateConverter.BASE_CELL_SIZE_METERS) {
            List<SearchGridCell> column = new ArrayList<>();
            for (double y = plannedSouth; y < plannedNorth; y +=
                    GridCoordinateConverter.BASE_CELL_SIZE_METERS) {
                SearchGridCell cell = createPlannedCell(x, y);
                if (cell != null && isInsidePlannedArea(cell))
                    column.add(cell);
            }
            if (northbound) {
                cells.addAll(column);
            } else {
                for (int i = column.size() - 1; i >= 0; i--)
                    cells.add(column.get(i));
            }
            northbound = !northbound;
            if (cells.size() >= MAX_RENDER_CELLS)
                break;
        }
        return cells;
    }

    public List<SearchGridCell> cellsForIds(List<String> cellIds) {
        List<SearchGridCell> cells = new ArrayList<>();
        if (cellIds == null)
            return cells;
        for (String cellId : cellIds) {
            SearchGridCell cell = knownOrParsedCell(cellId);
            if (cell != null)
                cells.add(cell);
        }
        return cells;
    }

    public List<SearchGridCell> cellsBetween(SearchGridCell anchor,
            SearchGridCell end, int maxCells) {
        List<SearchGridCell> cells = new ArrayList<>();
        if (anchor == null || end == null || maxCells <= 0)
            return cells;
        if (!anchor.getZoneDescriptor().equals(end.getZoneDescriptor()))
            return cells;
        double cellSize = GridCoordinateConverter.BASE_CELL_SIZE_METERS;
        int xStep = end.getWest() >= anchor.getWest() ? 1 : -1;
        int yStep = end.getSouth() >= anchor.getSouth() ? 1 : -1;
        int columns = Math.abs((int) Math.round((end.getWest()
                - anchor.getWest()) / cellSize)) + 1;
        int rows = Math.abs((int) Math.round((end.getSouth()
                - anchor.getSouth()) / cellSize)) + 1;
        for (int column = 0; column < columns; column++) {
            double west = anchor.getWest() + column * xStep * cellSize;
            boolean reverseColumn = column % 2 == 1;
            for (int row = 0; row < rows; row++) {
                int rowOffset = reverseColumn ? rows - 1 - row : row;
                double south = anchor.getSouth() + rowOffset * yStep
                        * cellSize;
                SearchGridCell cell = createCellForZone(anchor
                        .getZoneDescriptor(), west, south);
                if (cell == null || !isInsidePlannedArea(cell))
                    continue;
                cells.add(cell);
                if (cells.size() >= maxCells)
                    return cells;
            }
        }
        return cells;
    }

    public List<SearchGridCell> getReviewCells() {
        List<SearchGridCell> cells = new ArrayList<>();
        for (SearchGridCell cell : reviewedCells.values()) {
            cell.setStatus(stateStore.getStatus(cell.getId()));
            if (cell.getStatus() == SearchGridStatus.PARTIAL
                    || cell.getStatus() == SearchGridStatus.COMPLETE)
                cells.add(cell);
            if (cells.size() >= MAX_REVIEW_CELLS)
                break;
        }
        if (cells.isEmpty() && !hasPlannedArea()) {
            for (SearchGridCell cell : getSelectedAggregateCells()) {
                SearchGridStatus status = cell.getStatus();
                if (status == SearchGridStatus.PARTIAL
                        || status == SearchGridStatus.COMPLETE)
                    cells.add(cell);
            }
        }
        return cells;
    }

    public SearchGridCell selectCellById(String cellId) {
        SearchGridCell cell = knownOrParsedCell(cellId);
        if (cell == null)
            return selectedCell;
        return selectCell(cell);
    }

    public SearchGridCell getNextPlannedCell() {
        if (selectedCell == null || !hasPlannedArea())
            return null;
        double west = selectedCell.getWest();
        double south = selectedCell.getSouth();
        int maxSteps = Math.max(1, getPlannedCellEstimate());
        for (int i = 0; i < maxSteps; i++) {
            west += GridCoordinateConverter.BASE_CELL_SIZE_METERS;
            if (west >= plannedEast) {
                west = plannedWest;
                south += GridCoordinateConverter.BASE_CELL_SIZE_METERS;
            }
            if (south >= plannedNorth)
                return null;
            SearchGridCell cell = createPlannedCell(west, south);
            if (cell != null && isInsidePlannedArea(cell))
                return cell;
        }
        return null;
    }

    public SearchGridCell updateCurrentCellAt(GeoPoint point,
            boolean markPreviousPartial) {
        SearchGridCell current = converter.cellForPoint(point, stateStore);
        if (selectedCell == null)
            return selectCell(current);
        if (selectedCell.getId().equals(current.getId())) {
            selectedCell.setStatus(stateStore.getStatus(selectedCell.getId()));
            return selectedCell;
        }

        SearchGridCell previous = selectedCell;
        selectCell(current);
        if (markPreviousPartial
                && previous.getStatus() != SearchGridStatus.COMPLETE
                && previous.getStatus() != SearchGridStatus.PARTIAL) {
            setCellStatus(previous.getId(), SearchGridStatus.PARTIAL);
            pendingReviewCell = previous;
        }
        return selectedCell;
    }

    public SearchGridCell getPendingReviewCell() {
        if (pendingReviewCell == null)
            return null;
        pendingReviewCell.setStatus(stateStore.getStatus(pendingReviewCell
                .getId()));
        return pendingReviewCell;
    }

    public void clearPendingReviewCell(String cellId) {
        if (pendingReviewCell != null && pendingReviewCell.getId()
                .equals(cellId))
            pendingReviewCell = null;
    }

    public List<SearchGridCell> getSelectedAggregateCells() {
        if (selectedCell == null || selectedAggregateId == null)
            return new ArrayList<>();

        double aggregateWest = floorToGrid(selectedCell.getWest(),
                GridCoordinateConverter.AGGREGATE_GRID_SIZE_METERS);
        double aggregateSouth = floorToGrid(selectedCell.getSouth(),
                GridCoordinateConverter.AGGREGATE_GRID_SIZE_METERS);
        List<SearchGridCell> cells = new ArrayList<>();
        for (int row = 0; row < GridCoordinateConverter.AGGREGATE_CELLS_PER_SIDE; row++) {
            for (int column = 0; column < GridCoordinateConverter.AGGREGATE_CELLS_PER_SIDE; column++) {
                double west = aggregateWest + column
                        * GridCoordinateConverter.BASE_CELL_SIZE_METERS;
                double south = aggregateSouth + row
                        * GridCoordinateConverter.BASE_CELL_SIZE_METERS;
                cells.add(converter.createCell(selectedAggregateId,
                        selectedCell.getZoneDescriptor(), west, south, row,
                        column, stateStore));
            }
        }
        return cells;
    }

    public SearchGridStatus getAggregateStatus(List<SearchGridCell> cells) {
        if (cells.isEmpty())
            return SearchGridStatus.NOT_STARTED;

        boolean hasProgress = false;
        boolean allComplete = true;
        for (SearchGridCell cell : cells) {
            SearchGridStatus status = cell.getStatus();
            if (status == SearchGridStatus.PARTIAL
                    || status == SearchGridStatus.COMPLETE)
                hasProgress = true;
            if (status != SearchGridStatus.COMPLETE)
                allComplete = false;
        }

        if (allComplete)
            return SearchGridStatus.COMPLETE;
        return hasProgress ? SearchGridStatus.IN_PROGRESS
                : SearchGridStatus.NOT_STARTED;
    }

    /**
     * The rectangle SARtak draws a grid for: the planned search area, or the
     * selected cell's 1 km square when nothing has been planned yet.
     */
    public GridExtent getAreaExtent() {
        if (hasPlannedArea())
            return new GridExtent(plannedZone, plannedWest, plannedSouth,
                    plannedEast, plannedNorth);
        if (selectedCell == null)
            return null;
        double west = floorToGrid(selectedCell.getWest(),
                GridCoordinateConverter.AGGREGATE_GRID_SIZE_METERS);
        double south = floorToGrid(selectedCell.getSouth(),
                GridCoordinateConverter.AGGREGATE_GRID_SIZE_METERS);
        return new GridExtent(selectedCell.getZoneDescriptor(), west, south,
                west + GridCoordinateConverter.AGGREGATE_GRID_SIZE_METERS,
                south + GridCoordinateConverter.AGGREGATE_GRID_SIZE_METERS);
    }

    /**
     * Intersects {@code area} with the visible map, grown by
     * {@code padFraction} of the view on every side and snapped outward to
     * {@code snapMeters}. Returns null when the view does not overlap the
     * area. When the view is in a different UTM zone the whole area is
     * returned, since its cells cannot be clipped in the view's zone.
     */
    public GridExtent clipToView(GridExtent area, GeoBounds view,
            double padFraction, double snapMeters) {
        if (area == null || view == null)
            return area;
        double[] eastings = new double[4];
        double[] northings = new double[4];
        GeoPoint[] corners = new GeoPoint[] {
                new GeoPoint(view.getSouth(), view.getWest()),
                new GeoPoint(view.getSouth(), view.getEast()),
                new GeoPoint(view.getNorth(), view.getEast()),
                new GeoPoint(view.getNorth(), view.getWest())
        };
        for (int i = 0; i < corners.length; i++) {
            UTMPoint utm = UTMPoint.fromGeoPoint(corners[i]);
            if (!area.zone.equals(utm.getZoneDescriptor()))
                return area;
            eastings[i] = utm.getEasting();
            northings[i] = utm.getNorthing();
        }
        return clipToUtmView(area, min(eastings), min(northings),
                max(eastings), max(northings), padFraction, snapMeters);
    }

    /** ATAK-free core of {@link #clipToView} for unit tests. */
    static GridExtent clipToUtmView(GridExtent area, double viewWest,
            double viewSouth, double viewEast, double viewNorth,
            double padFraction, double snapMeters) {
        double padX = (viewEast - viewWest) * padFraction;
        double padY = (viewNorth - viewSouth) * padFraction;
        double snap = Math.max(GridCoordinateConverter.BASE_CELL_SIZE_METERS,
                snapMeters);
        double west = Math.max(area.west, Math.floor((viewWest - padX)
                / snap) * snap);
        double south = Math.max(area.south, Math.floor((viewSouth - padY)
                / snap) * snap);
        double east = Math.min(area.east, Math.ceil((viewEast + padX)
                / snap) * snap);
        double north = Math.min(area.north, Math.ceil((viewNorth + padY)
                / snap) * snap);
        GridExtent clipped = new GridExtent(area.zone, west, south, east,
                north);
        return clipped.isEmpty() ? null : clipped;
    }

    /**
     * Every PARTIAL/COMPLETE cell inside the current area. Built from the
     * sparse stored statuses, never by enumerating the area's cells, and
     * cached until a status or the area changes.
     */
    public List<SearchGridCell> getMarkedCellsInArea() {
        refreshAreaCache();
        return cachedMarkedCells;
    }

    public AreaProgress getAreaProgress() {
        refreshAreaCache();
        return cachedAreaProgress;
    }

    /** Store revision plus area scope; changes whenever marked cells may. */
    public String getAreaVersion() {
        GridExtent area = getAreaExtent();
        return stateStore.getRevision() + "@" + (area == null ? ""
                : area.key() + ":" + plannedShape + ":"
                        + Math.round(plannedRadiusMeters));
    }

    /** Exact number of 100 m cells whose centre lies inside the area. */
    public int getAreaCellCount() {
        GridExtent area = getAreaExtent();
        if (area == null)
            return 0;
        if (!hasPlannedArea() || plannedShape != PlannedAreaShape.CIRCLE)
            return area.getColumns() * area.getRows();
        double size = GridCoordinateConverter.BASE_CELL_SIZE_METERS;
        double radiusSquared = plannedRadiusMeters * plannedRadiusMeters;
        int count = 0;
        for (int column = 0; column < area.getColumns(); column++) {
            double dx = area.west + (column + 0.5) * size
                    - plannedCenterEasting;
            double remaining = radiusSquared - dx * dx;
            if (remaining < 0)
                continue;
            double half = Math.sqrt(remaining);
            // Rows whose centre northing is within +/- half of the centre.
            int firstRow = (int) Math.ceil((plannedCenterNorthing - half
                    - area.south) / size - 0.5);
            int lastRow = (int) Math.floor((plannedCenterNorthing + half
                    - area.south) / size - 0.5);
            firstRow = Math.max(0, firstRow);
            lastRow = Math.min(area.getRows() - 1, lastRow);
            if (lastRow >= firstRow)
                count += lastRow - firstRow + 1;
        }
        return count;
    }

    private void refreshAreaCache() {
        String version = getAreaVersion();
        if (cachedAreaProgress != null && version.equals(cachedAreaVersion))
            return;
        GridExtent area = getAreaExtent();
        List<SearchGridCell> marked = new ArrayList<>();
        int complete = 0;
        int partial = 0;
        int inProgress = 0;
        if (area != null) {
            for (Map.Entry<String, SearchGridStatus> entry : stateStore
                    .getKnownStatuses().entrySet()) {
                SearchGridCell cell = converter.parseCell(entry.getKey(),
                        entry.getValue());
                if (cell == null || !area.zone.equals(cell
                        .getZoneDescriptor()) || !isInsideArea(cell, area))
                    continue;
                SearchGridStatus status = entry.getValue();
                if (status == SearchGridStatus.COMPLETE) {
                    complete++;
                    marked.add(cell);
                } else if (status == SearchGridStatus.PARTIAL) {
                    partial++;
                    marked.add(cell);
                } else if (status == SearchGridStatus.IN_PROGRESS) {
                    inProgress++;
                }
            }
        }
        cachedMarkedCells = marked;
        cachedAreaProgress = new AreaProgress(complete, partial, inProgress,
                getAreaCellCount());
        cachedAreaVersion = version;
    }

    private boolean isInsideArea(SearchGridCell cell, GridExtent area) {
        double centerEasting = (cell.getWest() + cell.getEast()) / 2.0;
        double centerNorthing = (cell.getSouth() + cell.getNorth()) / 2.0;
        if (centerEasting < area.west || centerEasting > area.east
                || centerNorthing < area.south || centerNorthing > area.north)
            return false;
        return !hasPlannedArea() || isInsidePlannedArea(cell);
    }

    private static double min(double[] values) {
        double result = values[0];
        for (double value : values)
            result = Math.min(result, value);
        return result;
    }

    private static double max(double[] values) {
        double result = values[0];
        for (double value : values)
            result = Math.max(result, value);
        return result;
    }

    public GeoPoint[] getPlannedAreaOutlinePoints() {
        if (!hasPlannedArea())
            return new GeoPoint[0];
        if (plannedShape == PlannedAreaShape.CIRCLE) {
            int points = 36;
            GeoPoint[] outline = new GeoPoint[points];
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2.0 * i / points;
                outline[i] = converter.toGeoPoint(plannedZone,
                        plannedCenterEasting + Math.cos(angle)
                                * plannedRadiusMeters,
                        plannedCenterNorthing + Math.sin(angle)
                                * plannedRadiusMeters);
            }
            return outline;
        }
        return new GeoPoint[] {
                converter.toGeoPoint(plannedZone, plannedWest, plannedSouth),
                converter.toGeoPoint(plannedZone, plannedEast, plannedSouth),
                converter.toGeoPoint(plannedZone, plannedEast, plannedNorth),
                converter.toGeoPoint(plannedZone, plannedWest, plannedNorth)
        };
    }

    private void planBoxFromCell(SearchGridCell centerCell, double widthKm,
            double heightKm) {
        if (centerCell == null)
            return;
        plannedShape = PlannedAreaShape.BOX;
        plannedZone = centerCell.getZoneDescriptor();
        plannedWidthMeters = Math.max(100.0, widthKm * 1000.0);
        plannedHeightMeters = Math.max(100.0, heightKm * 1000.0);
        plannedCenterEasting = (centerCell.getWest() + centerCell.getEast())
                / 2.0;
        plannedCenterNorthing = (centerCell.getSouth() + centerCell
                .getNorth()) / 2.0;
        plannedWest = floorToGrid(plannedCenterEasting - plannedWidthMeters
                / 2.0, GridCoordinateConverter.BASE_CELL_SIZE_METERS);
        plannedSouth = floorToGrid(plannedCenterNorthing - plannedHeightMeters
                / 2.0, GridCoordinateConverter.BASE_CELL_SIZE_METERS);
        plannedEast = ceilToGrid(plannedCenterEasting + plannedWidthMeters
                / 2.0, GridCoordinateConverter.BASE_CELL_SIZE_METERS);
        plannedNorth = ceilToGrid(plannedCenterNorthing + plannedHeightMeters
                / 2.0, GridCoordinateConverter.BASE_CELL_SIZE_METERS);
        selectedCell = centerCell;
        selectedAggregateId = centerCell.getAggregateId();
        persistPlannedArea();
    }

    private void persistPlannedArea() {
        if (!hasPlannedArea()) {
            stateStore.clearPlannedArea();
            return;
        }
        stateStore.savePlannedArea(new SearchGridStateStore.PlannedAreaRecord(
                plannedShape.name(), plannedZone, plannedCenterEasting,
                plannedCenterNorthing, plannedWest, plannedSouth, plannedEast,
                plannedNorth, plannedRadiusMeters, plannedWidthMeters,
                plannedHeightMeters));
    }

    private void resetPlannedAreaFields() {
        plannedShape = PlannedAreaShape.NONE;
        plannedZone = "";
        plannedCenterEasting = 0.0;
        plannedCenterNorthing = 0.0;
        plannedWest = 0.0;
        plannedSouth = 0.0;
        plannedEast = 0.0;
        plannedNorth = 0.0;
        plannedRadiusMeters = 0.0;
        plannedWidthMeters = 0.0;
        plannedHeightMeters = 0.0;
        selectedCell = null;
        selectedAggregateId = null;
        pendingReviewCell = null;
    }

    private void loadReviewedCellsFromStore() {
        reviewedCells.clear();
        for (Map.Entry<String, SearchGridStatus> entry : stateStore
                .getKnownStatuses().entrySet()) {
            SearchGridStatus status = entry.getValue();
            if (status != SearchGridStatus.PARTIAL
                    && status != SearchGridStatus.COMPLETE)
                continue;
            SearchGridCell cell = converter.cellForId(entry.getKey(),
                    stateStore);
            if (cell == null)
                continue;
            cell.setStatus(status);
            reviewedCells.put(entry.getKey(), cell);
            if (reviewedCells.size() >= MAX_REVIEW_CELLS)
                break;
        }
    }

    private SearchGridCell createPlannedCell(double west, double south) {
        return createCellForZone(plannedZone, west, south);
    }

    private SearchGridCell createCellForZone(String zone, double west,
            double south) {
        double aggregateWest = floorToGrid(west,
                GridCoordinateConverter.AGGREGATE_GRID_SIZE_METERS);
        double aggregateSouth = floorToGrid(south,
                GridCoordinateConverter.AGGREGATE_GRID_SIZE_METERS);
        int column = (int) Math.floor((west - aggregateWest)
                / GridCoordinateConverter.BASE_CELL_SIZE_METERS);
        int row = (int) Math.floor((south - aggregateSouth)
                / GridCoordinateConverter.BASE_CELL_SIZE_METERS);
        return converter.createCell(converter.aggregateId(zone,
                aggregateWest, aggregateSouth), zone, west, south, row,
                column, stateStore);
    }

    private boolean isInsidePlannedArea(SearchGridCell cell) {
        if (!hasPlannedArea())
            return true;
        double centerEasting = (cell.getWest() + cell.getEast()) / 2.0;
        double centerNorthing = (cell.getSouth() + cell.getNorth()) / 2.0;
        if (centerEasting < plannedWest || centerEasting > plannedEast
                || centerNorthing < plannedSouth
                || centerNorthing > plannedNorth)
            return false;
        if (plannedShape != PlannedAreaShape.CIRCLE)
            return true;
        double dx = centerEasting - plannedCenterEasting;
        double dy = centerNorthing - plannedCenterNorthing;
        return dx * dx + dy * dy <= plannedRadiusMeters * plannedRadiusMeters;
    }

    private SearchGridCell knownOrParsedCell(String cellId) {
        if (cellId == null || cellId.length() == 0)
            return null;
        if (selectedCell != null && cellId.equals(selectedCell.getId()))
            return selectedCell;
        SearchGridCell reviewed = reviewedCells.get(cellId);
        if (reviewed != null)
            return reviewed;
        return converter.cellForId(cellId, stateStore);
    }

    private double floorToGrid(double value, double gridSize) {
        return Math.floor(value / gridSize) * gridSize;
    }

    private double ceilToGrid(double value, double gridSize) {
        return Math.ceil(value / gridSize) * gridSize;
    }
}

