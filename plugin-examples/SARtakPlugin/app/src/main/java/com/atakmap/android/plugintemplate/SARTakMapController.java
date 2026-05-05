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
import com.atakmap.android.plugintemplate.grid.SearchLineManager;
import com.atakmap.android.plugintemplate.grid.SearchLineOverlay;
import com.atakmap.android.plugintemplate.grid.SearchPartyAssignmentManager;
import com.atakmap.android.plugintemplate.grid.SearchTeamMarkerOverlay;
import com.atakmap.android.plugintemplate.grid.SearchTeamMember;
import com.atakmap.android.plugintemplate.grid.SearchTrackManager;
import com.atakmap.android.plugintemplate.grid.SearchTrackOverlay;
import com.atakmap.android.plugintemplate.grid.TeamMarkerVisibilityMode;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.List;

public class SARTakMapController {

    private final MapView mapView;
    private final GridCoordinateConverter converter;
    private final SearchGridManager gridManager;
    private final SearchGridOverlay gridOverlay;
    private final SearchPartyAssignmentManager assignmentManager;
    private final SearchTeamMarkerOverlay teamMarkerOverlay;
    private final SearchTrackManager trackManager;
    private final SearchTrackOverlay trackOverlay;
    private final SearchLineManager searchLineManager;
    private final SearchLineOverlay searchLineOverlay;
    private final MapEventDispatcher.MapEventDispatchListener mapEventListener;

    public SARTakMapController(MapView mapView, Context pluginContext) {
        this.mapView = mapView;
        this.converter = new GridCoordinateConverter();
        SearchGridStateStore stateStore = new SearchGridStateStore(pluginContext);
        this.assignmentManager = new SearchPartyAssignmentManager();
        this.gridManager = new SearchGridManager(converter, stateStore);
        this.gridOverlay = new SearchGridOverlay(mapView, converter,
                assignmentManager);
        this.teamMarkerOverlay = new SearchTeamMarkerOverlay(mapView,
                assignmentManager);
        this.trackManager = new SearchTrackManager();
        this.trackOverlay = new SearchTrackOverlay(mapView);
        this.searchLineManager = new SearchLineManager(converter,
                assignmentManager);
        this.searchLineOverlay = new SearchLineOverlay(mapView);
        this.mapEventListener = new MapEventDispatcher.MapEventDispatchListener() {
            @Override
            public void onMapEvent(MapEvent event) {
                refreshOverlay();
            }
        };
        registerMapListeners();
        teamMarkerOverlay.render();
        trackOverlay.render();
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
        searchLineManager.updateLeaderPosition(cell, getCurrentUserPoint());
        arrangeTeamMembers();
        refreshOverlay();
        return cell;
    }

    public void startSearchLine() {
        SearchGridCell cell = ensureSelectedCell();
        searchLineManager.start(cell, getCurrentUserPoint());
        refreshOverlay();
    }

    public void endSearchLine() {
        searchLineManager.end();
        refreshOverlay();
    }

    public void pauseSearchLine() {
        searchLineManager.pause();
        refreshOverlay();
    }

    public boolean resumeSearchLine() {
        boolean resumed = searchLineManager.resume();
        refreshOverlay();
        return resumed;
    }

    public void forceResumeSearchLine() {
        searchLineManager.forceResume();
        refreshOverlay();
    }

    public String cycleSearchLineColor() {
        String label = searchLineManager.cycleColor().getLabel();
        refreshOverlay();
        return label;
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
        assignmentManager.addMockMember();
        arrangeTeamMembers();
        refreshOverlay();
    }

    public void decreaseTeamSize() {
        assignmentManager.removeLastLaneMember();
        arrangeTeamMembers();
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

    public String getTeamRosterSummary() {
        return assignmentManager.describeTeamRoster();
    }

    public String getTeamName() {
        return assignmentManager.getTeamName();
    }

    public String getTeamId() {
        return assignmentManager.getTeamId();
    }

    public List<SearchTeamMember> getTeamMembers() {
        return assignmentManager.getVisibleMembers();
    }

    public void setTeamMarkerVisibilityMode(TeamMarkerVisibilityMode mode) {
        teamMarkerOverlay.setVisibilityMode(mode);
    }

    public String getTeamMarkerVisibilityLabel() {
        return teamMarkerOverlay.getVisibilityMode().getLabel();
    }

    public String getConnectionAlertSummary() {
        return assignmentManager.describeConnectionAlerts();
    }

    public String getSearchLineSummary() {
        return searchLineManager.getSummary();
    }

    public String getSearchLineMemberSummary() {
        return searchLineManager.getMemberLineSummary();
    }

    public String getSearchLineWarningSummary() {
        String warning = searchLineManager.getWarningSummary();
        String connection = getConnectionAlertSummary();
        if ("No search line warnings".equals(warning))
            return connection;
        if ("No team connection alerts".equals(connection))
            return warning;
        return warning + "\n" + connection;
    }

    public boolean isSearchLineStarted() {
        return searchLineManager.isStarted();
    }

    public boolean isSearchLinePaused() {
        return searchLineManager.isPaused();
    }

    public String getSearchLineColorLabel() {
        return searchLineManager.getColorOption().getLabel();
    }

    public String getSearchLineRestartPrompt() {
        return searchLineManager.getRestartPrompt();
    }

    public String getMemberSearchLineSummary(String uniqueId) {
        return searchLineManager.getMemberCardLineSummary(uniqueId);
    }

    public SearchTeamMember selectTeamMember(String uniqueId) {
        SearchTeamMember member = assignmentManager.findMemberById(uniqueId);
        if (member == null)
            return null;

        teamMarkerOverlay.setSelectedMemberId(uniqueId);
        mapView.getMapController().panTo(new GeoPoint(member.getLatitude(),
                member.getLongitude()), true);
        return member;
    }

    public String getSelectedTeamMemberId() {
        return teamMarkerOverlay.getSelectedMemberId();
    }

    public boolean toggleTrackRecording() {
        return trackManager.toggleRecording();
    }

    public boolean toggleTrackVisibility() {
        boolean visible = trackManager.toggleVisible();
        trackOverlay.setVisible(visible);
        return visible;
    }

    public String getTrackStatusSummary() {
        return trackManager.getStatusSummary();
    }

    public String getTrackDetailsSummary() {
        return trackManager.getDetailsSummary();
    }

    public boolean isTrackRecording() {
        return trackManager.isRecording();
    }

    public boolean isTrackVisible() {
        return trackManager.isVisible();
    }

    public boolean isGridOverlayVisible() {
        return gridOverlay.isVisible();
    }

    public void dispose() {
        unregisterMapListeners();
        gridOverlay.setVisible(false);
        teamMarkerOverlay.setVisible(false);
        trackOverlay.setVisible(false);
        searchLineOverlay.setVisible(false);
    }

    private void refreshOverlay() {
        searchLineManager.updateLeaderPosition(gridManager.getSelectedCell(),
                getCurrentUserPoint());
        arrangeTeamMembers();
        gridOverlay.render(gridManager);
        searchLineOverlay.render(searchLineManager);
        teamMarkerOverlay.render();
    }

    private void arrangeTeamMembers() {
        SearchGridCell cell = gridManager.getSelectedCell();
        if (cell == null)
            return;
        GeoPoint leaderPoint = getCurrentUserPoint();
        double lineNorthing = searchLineManager.getArrangementNorthing(cell,
                leaderPoint);
        assignmentManager.arrangeMembersForCell(cell, converter, leaderPoint,
                lineNorthing);
    }

    private SearchGridCell ensureSelectedCell() {
        SearchGridCell cell = gridManager.getSelectedCell();
        if (cell == null)
            cell = gridManager.selectCellAt(getCurrentUserPoint());
        return cell;
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
