package com.atakmap.android.plugintemplate.grid;

import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.ArrayList;
import java.util.List;

public class SearchGridManager {

    private final GridCoordinateConverter converter;
    private final SearchGridStateStore stateStore;
    private SearchGridCell selectedCell;
    private String selectedAggregateId;

    public SearchGridManager(GridCoordinateConverter converter,
            SearchGridStateStore stateStore) {
        this.converter = converter;
        this.stateStore = stateStore;
    }

    public SearchGridCell selectCellAt(GeoPoint point) {
        // Fallback grid alignment snaps directly to ATAK's UTM metre
        // coordinates at 100 m boundaries. If ATAK exposes the exact visible
        // MGRS 100 m square later, this is the method to replace.
        selectedCell = converter.cellForPoint(point, stateStore);
        selectedAggregateId = selectedCell.getAggregateId();
        if (selectedCell.getStatus() == SearchGridStatus.NOT_STARTED) {
            // Selecting a cell means the team has started operating in it, but
            // IN_PROGRESS intentionally has no fill colour and does not count
            // toward zoomed-out progress aggregation.
            selectedCell.setStatus(SearchGridStatus.IN_PROGRESS);
            stateStore.setStatus(selectedCell.getId(),
                    SearchGridStatus.IN_PROGRESS);
        }
        return selectedCell;
    }

    public SearchGridCell getSelectedCell() {
        return selectedCell;
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
        if (selectedCell != null && cellId.equals(selectedCell.getId()))
            selectedCell.setStatus(status);
        if (status == SearchGridStatus.NOT_STARTED)
            stateStore.clearStatus(cellId);
        else
            stateStore.setStatus(cellId, status);
    }

    public List<SearchGridCell> getSelectedAggregateCells() {
        if (selectedCell == null || selectedAggregateId == null)
            return new ArrayList<>();

        // Aggregation is only a rendering summary for low zoom. It groups the
        // already-defined 100 m cells in the surrounding 1 km bucket; it is not
        // the source model for creating searchable cells.
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
            // Per requirements, low-zoom grey/green summaries only consider
            // manually marked PARTIAL/COMPLETE cells. IN_PROGRESS remains
            // visually unfilled.
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

    private double floorToGrid(double value, double gridSize) {
        return Math.floor(value / gridSize) * gridSize;
    }
}
