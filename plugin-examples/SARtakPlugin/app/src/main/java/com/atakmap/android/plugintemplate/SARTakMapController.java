package com.atakmap.android.plugintemplate;

import android.content.Context;

import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.plugintemplate.grid.GridCoordinateConverter;
import com.atakmap.android.plugintemplate.grid.SearchGridCell;
import com.atakmap.android.plugintemplate.grid.SearchGridManager;
import com.atakmap.android.plugintemplate.grid.SearchGridOverlay;
import com.atakmap.android.plugintemplate.grid.SearchGridStateStore;
import com.atakmap.android.plugintemplate.grid.SearchGridStatus;
import com.atakmap.android.plugintemplate.grid.SearchPartyAssignmentManager;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

public class SARTakMapController {

    private final MapView mapView;
    private final SearchGridManager gridManager;
    private final SearchGridOverlay gridOverlay;
    private final SearchPartyAssignmentManager assignmentManager;
    private final MapEventDispatcher.MapEventDispatchListener mapEventListener;

    public SARTakMapController(MapView mapView, Context pluginContext) {
        this.mapView = mapView;
        GridCoordinateConverter converter = new GridCoordinateConverter();
        SearchGridStateStore stateStore = new SearchGridStateStore(pluginContext);
        this.assignmentManager = new SearchPartyAssignmentManager();
        this.gridManager = new SearchGridManager(converter, stateStore);
        this.gridOverlay = new SearchGridOverlay(mapView, converter,
                assignmentManager);
        this.mapEventListener = new MapEventDispatcher.MapEventDispatchListener() {
            @Override
            public void onMapEvent(MapEvent event) {
                refreshOverlay();
            }
        };
        registerMapListeners();
    }

    public boolean toggleGridOverlay() {
        boolean visible = !gridOverlay.isVisible();
        gridOverlay.setVisible(visible);
        if (visible && gridManager.getSelectedCell() == null)
            selectCurrentCell();
        refreshOverlay();
        return visible;
    }

    public SearchGridCell selectCurrentCell() {
        SearchGridCell cell = gridManager.selectCellAt(getCurrentUserPoint());
        refreshOverlay();
        return cell;
    }

    public void markSelectedPartial() {
        gridManager.setSelectedStatus(SearchGridStatus.PARTIAL);
        refreshOverlay();
    }

    public void markSelectedComplete() {
        gridManager.setSelectedStatus(SearchGridStatus.COMPLETE);
        refreshOverlay();
    }

    public void clearSelectedStatus() {
        gridManager.setSelectedStatus(SearchGridStatus.NOT_STARTED);
        refreshOverlay();
    }

    public void increaseTeamSize() {
        assignmentManager.increaseTeamSize();
        refreshOverlay();
    }

    public void decreaseTeamSize() {
        assignmentManager.decreaseTeamSize();
        refreshOverlay();
    }

    public int getTeamSize() {
        return assignmentManager.getTeamSize();
    }

    public String getSelectedCellId() {
        SearchGridCell cell = gridManager.getSelectedCell();
        return cell == null ? "No cell selected" : cell.getId();
    }

    public String getSelectedCellStatus() {
        SearchGridCell cell = gridManager.getSelectedCell();
        return cell == null ? "" : cell.getStatus().name();
    }

    public String getAssignmentSummary() {
        return assignmentManager.describeAssignments(gridManager
                .getSelectedCell());
    }

    public boolean isGridOverlayVisible() {
        return gridOverlay.isVisible();
    }

    public void dispose() {
        unregisterMapListeners();
        gridOverlay.setVisible(false);
    }

    private void refreshOverlay() {
        gridOverlay.render(gridManager);
    }

    private GeoPoint getCurrentUserPoint() {
        if (mapView.getSelfMarker() != null
                && mapView.getSelfMarker().getPoint() != null
                && mapView.getSelfMarker().getPoint().isValid())
            return mapView.getSelfMarker().getPoint();

        GeoPointMetaData center = mapView.getCenterPoint();
        if (center != null && center.get() != null && center.get().isValid())
            return center.get();

        return new GeoPoint(-27.4705, 153.0260);
    }

    private void registerMapListeners() {
        MapEventDispatcher dispatcher = mapView.getMapEventDispatcher();
        dispatcher.addMapEventListener(MapEvent.MAP_ZOOM, mapEventListener);
        dispatcher.addMapEventListener(MapEvent.MAP_SCALE, mapEventListener);
        dispatcher.addMapEventListener(MapEvent.MAP_MOVED, mapEventListener);
    }

    private void unregisterMapListeners() {
        MapEventDispatcher dispatcher = mapView.getMapEventDispatcher();
        dispatcher.removeMapEventListener(MapEvent.MAP_ZOOM, mapEventListener);
        dispatcher.removeMapEventListener(MapEvent.MAP_SCALE, mapEventListener);
        dispatcher.removeMapEventListener(MapEvent.MAP_MOVED, mapEventListener);
    }
}
