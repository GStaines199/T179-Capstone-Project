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

    private static final int MAX_RENDER_CELLS = 2500;
    private static final int MAX_REVIEW_CELLS = 200;

    private final GridCoordinateConverter converter;
    private final SearchGridStateStore stateStore;
    private SearchGridCell selectedCell;
    private String selectedAggregateId;
    private SearchGridCell pendingReviewCell;
    private final Map<String, SearchGridCell> reviewedCells =
            new LinkedHashMap<>();

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

    public List<SearchGridCell> getRenderCells(GeoBounds visibleBounds) {
        if (hasPlannedArea())
            return cellsForPlannedArea(visibleBounds, MAX_RENDER_CELLS);
        return getSelectedAggregateCells();
    }

    public List<SearchGridCell> getRenderCells() {
        return getRenderCells(null);
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

    public List<SearchGridCell> getPlannedAreaCells() {
        return hasPlannedArea() ? cellsForPlannedArea(null, MAX_RENDER_CELLS)
                : new ArrayList<SearchGridCell>();
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

    private List<SearchGridCell> cellsForPlannedArea(GeoBounds visibleBounds,
            int maxCells) {
        List<SearchGridCell> cells = new ArrayList<>();
        if (!hasPlannedArea())
            return cells;
        double west = plannedWest;
        double east = plannedEast;
        double south = plannedSouth;
        double north = plannedNorth;
        if (visibleBounds != null) {
            UTMPoint sw = UTMPoint.fromGeoPoint(new GeoPoint(visibleBounds
                    .getSouth(), visibleBounds.getWest()));
            UTMPoint ne = UTMPoint.fromGeoPoint(new GeoPoint(visibleBounds
                    .getNorth(), visibleBounds.getEast()));
            if (plannedZone.equals(sw.getZoneDescriptor())
                    && plannedZone.equals(ne.getZoneDescriptor())) {
                west = Math.max(west, floorToGrid(Math.min(sw.getEasting(),
                        ne.getEasting()),
                        GridCoordinateConverter.BASE_CELL_SIZE_METERS));
                east = Math.min(east, ceilToGrid(Math.max(sw.getEasting(),
                        ne.getEasting()),
                        GridCoordinateConverter.BASE_CELL_SIZE_METERS));
                south = Math.max(south, floorToGrid(Math.min(sw.getNorthing(),
                        ne.getNorthing()),
                        GridCoordinateConverter.BASE_CELL_SIZE_METERS));
                north = Math.min(north, ceilToGrid(Math.max(sw.getNorthing(),
                        ne.getNorthing()),
                        GridCoordinateConverter.BASE_CELL_SIZE_METERS));
            }
        }
        for (double y = south; y < north; y += GridCoordinateConverter.BASE_CELL_SIZE_METERS) {
            for (double x = west; x < east; x += GridCoordinateConverter.BASE_CELL_SIZE_METERS) {
                SearchGridCell cell = createPlannedCell(x, y);
                if (cell == null || !isInsidePlannedArea(cell))
                    continue;
                cells.add(cell);
                if (cells.size() >= maxCells)
                    return cells;
            }
        }
        return cells;
    }

    private SearchGridCell createPlannedCell(double west, double south) {
        double aggregateWest = floorToGrid(west,
                GridCoordinateConverter.AGGREGATE_GRID_SIZE_METERS);
        double aggregateSouth = floorToGrid(south,
                GridCoordinateConverter.AGGREGATE_GRID_SIZE_METERS);
        int column = (int) Math.floor((west - aggregateWest)
                / GridCoordinateConverter.BASE_CELL_SIZE_METERS);
        int row = (int) Math.floor((south - aggregateSouth)
                / GridCoordinateConverter.BASE_CELL_SIZE_METERS);
        return converter.createCell(converter.aggregateId(plannedZone,
                aggregateWest, aggregateSouth), plannedZone, west, south, row,
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
