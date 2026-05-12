package com.atakmap.android.plugintemplate;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.plugintemplate.database.DatabaseHelper;
import com.atakmap.android.plugintemplate.database.LocationRepository;
import com.atakmap.android.plugintemplate.database.SearcherRepository;
import com.atakmap.android.plugintemplate.database.TrackSessionRepository;
import com.atakmap.android.plugintemplate.grid.GridCoordinateConverter;
import com.atakmap.android.plugintemplate.grid.SearchGridCell;
import com.atakmap.android.plugintemplate.grid.SearchGridManager;
import com.atakmap.android.plugintemplate.grid.SearchGridOverlay;
import com.atakmap.android.plugintemplate.grid.SearchGridStateStore;
import com.atakmap.android.plugintemplate.grid.SearchGridStatus;
import com.atakmap.android.plugintemplate.grid.SearchLineColorOption;
import com.atakmap.android.plugintemplate.grid.SearchLineManager;
import com.atakmap.android.plugintemplate.grid.SearchLineOverlay;
import com.atakmap.android.plugintemplate.grid.SearchPartyAssignmentManager;
import com.atakmap.android.plugintemplate.grid.SearchTeamMarkerOverlay;
import com.atakmap.android.plugintemplate.grid.SearchTeamMember;
import com.atakmap.android.plugintemplate.grid.SearchTeamStateStore;
import com.atakmap.android.plugintemplate.grid.SearchTrackManager;
import com.atakmap.android.plugintemplate.grid.SearchTrackOverlay;
import com.atakmap.android.plugintemplate.grid.TeamMarkerVisibilityMode;
import com.atakmap.android.plugintemplate.runtime.AtakLocationStatus;
import com.atakmap.android.plugintemplate.runtime.AtakRoleResolver;
import com.atakmap.android.plugintemplate.runtime.AtakTeamContactDataSource;
import com.atakmap.android.plugintemplate.runtime.AtakTrackBridge;
import com.atakmap.android.plugintemplate.runtime.IdentityManager;
import com.atakmap.android.plugintemplate.runtime.LocationCaptureManager;
import com.atakmap.android.plugintemplate.runtime.PluginHealthManager;
import com.atakmap.android.plugintemplate.runtime.SearchTeamCotMessage;
import com.atakmap.android.plugintemplate.runtime.SearchTeamCotWorkflow;
import com.atakmap.coremap.maps.coords.GeoPoint;

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
    private final AtakTrackBridge atakTrackBridge;
    private final SearchLineManager searchLineManager;
    private final SearchLineOverlay searchLineOverlay;
    private final PluginHealthManager healthManager;
    private final IdentityManager identityManager;
    private final LocationCaptureManager locationCaptureManager;
    private final AtakTeamContactDataSource teamContactDataSource;
    private final SearchTeamCotWorkflow teamCotWorkflow;
    private final SearchTeamStateStore teamStateStore;
    private final MapEventDispatcher.MapEventDispatchListener mapEventListener;
    private final Handler backgroundHandler = new Handler(Looper.getMainLooper());
    private final Runnable backgroundRunnable;
    private String atakContactSummary = "ATAK contacts not scanned yet";
    private AtakRoleResolver.Role currentRole = AtakRoleResolver.Role.TEAM_MEMBER;

    public SARTakMapController(MapView mapView, Context pluginContext) {
        this.mapView = mapView;
        this.converter = new GridCoordinateConverter();
        // ATAK plugin contexts are suitable for resources/layout inflation, but
        // runtime files belong under ATAK's writable app context.
        Context runtimeContext = mapView.getContext();
        DatabaseHelper databaseHelper = DatabaseHelper.getInstance(runtimeContext);
        SearcherRepository searcherRepository = new SearcherRepository(
                databaseHelper);
        TrackSessionRepository trackSessionRepository =
                new TrackSessionRepository(databaseHelper);
        LocationRepository locationRepository = new LocationRepository(
                databaseHelper);
        SearchGridStateStore stateStore = new SearchGridStateStore(runtimeContext);
        this.assignmentManager = new SearchPartyAssignmentManager();
        this.teamStateStore = new SearchTeamStateStore(runtimeContext);
        this.teamStateStore.load(assignmentManager);
        this.gridManager = new SearchGridManager(converter, stateStore);
        this.gridOverlay = new SearchGridOverlay(mapView, converter,
                assignmentManager);
        this.searchLineManager = new SearchLineManager(converter,
                assignmentManager);
        this.teamMarkerOverlay = new SearchTeamMarkerOverlay(mapView,
                assignmentManager, searchLineManager);
        this.trackManager = new SearchTrackManager(trackSessionRepository,
                locationRepository);
        this.trackOverlay = new SearchTrackOverlay(mapView);
        this.atakTrackBridge = new AtakTrackBridge(mapView);
        this.searchLineOverlay = new SearchLineOverlay(mapView);
        this.healthManager = new PluginHealthManager();
        this.identityManager = new IdentityManager(runtimeContext, mapView,
                searcherRepository);
        this.teamContactDataSource = new AtakTeamContactDataSource(mapView);
        this.teamCotWorkflow = new SearchTeamCotWorkflow(mapView,
                identityManager);
        this.locationCaptureManager = new LocationCaptureManager(mapView,
                identityManager, trackManager, healthManager,
                new LocationCaptureManager.Listener() {
                    @Override
                    public void onLocationCaptured() {
                        refreshOverlay();
                    }
                });
        this.mapEventListener = new MapEventDispatcher.MapEventDispatchListener() {
            @Override
            public void onMapEvent(MapEvent event) {
                refreshOverlay();
            }
        };
        this.backgroundRunnable = new Runnable() {
            @Override
            public void run() {
                runBackgroundTeamRefresh();
                backgroundHandler.postDelayed(this, 5000L);
            }
        };
        registerMapListeners();
        initialiseRuntime();
        startBackgroundRefresh();
        teamMarkerOverlay.render();
        trackOverlay.render(trackManager.getTrackPoints());
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
        GeoPoint currentPoint = getCurrentUserPoint();
        if (currentPoint == null)
            return gridManager.getSelectedCell();
        SearchGridCell cell = gridManager.selectCellAt(currentPoint);
        searchLineManager.updateLeaderPosition(cell, currentPoint);
        arrangeTeamMembers();
        refreshOverlay();
        return cell;
    }

    public void startSearchLine() {
        SearchGridCell cell = ensureSelectedCell();
        GeoPoint currentPoint = getCurrentUserPoint();
        if (currentPoint == null)
            return;
        searchLineManager.start(cell, currentPoint);
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

    public void setSearchLineColor(SearchLineColorOption colorOption) {
        searchLineManager.setColorOption(colorOption);
        refreshOverlay();
    }

    public void setSearchLineTolerance(double toleranceMeters) {
        searchLineManager.setReturnMarkToleranceMeters(toleranceMeters);
        refreshOverlay();
    }

    public boolean toggleTeamCallsigns() {
        return teamMarkerOverlay.toggleCallsigns();
    }

    public boolean isShowingTeamCallsigns() {
        return teamMarkerOverlay.isShowingCallsigns();
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
        refreshAtakTeamContacts();
    }

    public void decreaseTeamSize() {
        assignmentManager.removeLastLaneMember();
        arrangeTeamMembers();
        refreshOverlay();
        teamStateStore.save(assignmentManager);
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
        syncSelfTeamMemberFromAtak();
        return assignmentManager.describeTeamRoster();
    }

    public String getTeamName() {
        return assignmentManager.getTeamName().length() == 0
                ? "No SARtak team" : assignmentManager.getTeamName();
    }

    public String getTeamId() {
        return assignmentManager.getTeamId().length() == 0
                ? getFixedLeaderTeamId() : assignmentManager.getTeamId();
    }

    public String getSelfMemberId() {
        return assignmentManager.getSelfMemberId();
    }

    public List<SearchTeamMember> getTeamMembers() {
        if (!assignmentManager.isTeamCreated())
            return assignmentManager.getVisibleMembers();
        syncSelfTeamMemberFromAtak();
        refreshTeamContactsInternal();
        return assignmentManager.getVisibleMembers();
    }

    public boolean isTeamCreated() {
        return assignmentManager.isTeamCreated();
    }

    public void createTeam(String teamName) {
        assignmentManager.createTeam(teamName, getFixedLeaderTeamId());
        teamStateStore.save(assignmentManager);
        teamCotWorkflow.advertiseTeam(assignmentManager.getTeamId(),
                assignmentManager.getTeamName());
        refreshOverlay();
    }

    public void requestJoinTeam(SearchTeamCotMessage team) {
        teamCotWorkflow.requestJoin(team);
    }

    public void cancelJoinRequest(SearchTeamCotMessage request) {
        teamCotWorkflow.cancelJoinRequest(request);
    }

    public void inviteTeamMember(String uniqueId) {
        if (!assignmentManager.isTeamCreated())
            return;
        AtakTeamContactDataSource.ContactSnapshot contact = findContact(uniqueId);
        if (contact == null)
            return;
        teamCotWorkflow.inviteMember(assignmentManager.getTeamId(),
                assignmentManager.getTeamName(), contact.getUid(),
                contact.getCallsign());
    }

    public void cancelInvite(SearchTeamCotMessage invite) {
        teamCotWorkflow.cancelInvite(invite);
    }

    public void respondToJoinRequest(SearchTeamCotMessage request,
            boolean accepted) {
        teamCotWorkflow.respondToJoin(request, accepted);
        if (accepted) {
            AtakTeamContactDataSource.ContactSnapshot contact = findContact(
                    request.getSenderUid());
            if (contact != null)
                assignmentManager.addTeamMember(contact,
                        getAvailableSelfPoint(), converter);
            teamStateStore.save(assignmentManager);
            refreshOverlay();
        }
    }

    public void acceptJoinResponse(SearchTeamCotMessage response) {
        assignmentManager.joinTeam(response.getTeamName(),
                response.getTeamId());
        teamStateStore.save(assignmentManager);
        refreshOverlay();
    }

    public void respondToInvite(SearchTeamCotMessage invite,
            boolean accepted) {
        teamCotWorkflow.respondToInvite(invite, accepted);
        if (accepted) {
            assignmentManager.joinTeam(invite.getTeamName(),
                    invite.getTeamId());
            teamStateStore.save(assignmentManager);
            refreshOverlay();
        }
    }

    public boolean acceptInviteResponse(SearchTeamCotMessage response) {
        if (!SearchTeamCotMessage.ACTION_INVITE_ACCEPT.equals(
                response.getAction()))
            return false;
        boolean added = addTeamMemberFromContact(response.getSenderUid());
        if (added)
            teamStateStore.save(assignmentManager);
        return added;
    }

    public List<SearchTeamCotMessage> getActiveTeamAdvertisements() {
        List<SearchTeamCotMessage> teams = new java.util.ArrayList<>(
                teamCotWorkflow.getTeamAdvertisements());
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        for (AtakTeamContactDataSource.ContactSnapshot contact
                : teamContactDataSource.getContacts()) {
            if (!contact.isTeamLead()
                    || contact.getUid().equals(identity.getUid()))
                continue;
            String teamId = "TEAM-" + contact.getUid();
            if (containsTeam(teams, teamId))
                continue;
            teams.add(new SearchTeamCotMessage("sartak-fallback-team-"
                    + contact.getUid(), SearchTeamCotMessage.ACTION_ADVERTISE,
                    teamId, contact.getCallsign() + " Team",
                    contact.getUid(), contact.getCallsign(), contact.getUid(),
                    contact.getCallsign(), "", ""));
        }
        return dedupeTeams(teams);
    }

    public List<SearchTeamCotMessage> getPendingJoinRequests() {
        if (!assignmentManager.isTeamCreated())
            return java.util.Collections.emptyList();
        return teamCotWorkflow.getJoinRequests(assignmentManager.getTeamId());
    }

    public List<SearchTeamCotMessage> getJoinResponsesForMe() {
        return teamCotWorkflow.getJoinResponsesForMe();
    }

    public List<SearchTeamCotMessage> getInvitesForMe() {
        return teamCotWorkflow.getInvitesForMe();
    }

    public List<SearchTeamCotMessage> getInviteResponsesForLeader() {
        if (!assignmentManager.isTeamCreated())
            return java.util.Collections.emptyList();
        return teamCotWorkflow.getInviteResponsesForLeader();
    }

    public List<SearchTeamCotMessage> getOutgoingInvites() {
        if (!assignmentManager.isTeamCreated())
            return java.util.Collections.emptyList();
        return teamCotWorkflow.getOutgoingInvites(assignmentManager.getTeamId());
    }

    public List<SearchTeamCotMessage> getOutgoingJoinRequests() {
        return teamCotWorkflow.getOutgoingJoinRequests();
    }

    public void updateTeamSetup(String teamName) {
        assignmentManager.setTeamDetails(teamName, assignmentManager
                .getTeamId());
        teamStateStore.save(assignmentManager);
        refreshOverlay();
    }

    public boolean addTeamMemberFromContact(String uniqueId) {
        if (!assignmentManager.isTeamCreated())
            return false;
        AtakTeamContactDataSource.ContactSnapshot contact = findContact(uniqueId);
        if (contact == null)
            return false;
        SearchTeamMember member = assignmentManager.addTeamMember(contact,
                getAvailableSelfPoint(), converter);
        arrangeTeamMembers();
        refreshOverlay();
        teamStateStore.save(assignmentManager);
        return member != null;
    }

    public List<AtakTeamContactDataSource.ContactSnapshot> getAvailableContacts() {
        return teamContactDataSource.getContacts();
    }

    public boolean removeTeamMemberFromSetup(String uniqueId) {
        boolean removed = assignmentManager.removeTeamMember(uniqueId);
        arrangeTeamMembers();
        refreshOverlay();
        if (removed)
            teamStateStore.save(assignmentManager);
        return removed;
    }

    public String refreshAtakTeamContacts() {
        if (isLeaderRole() && assignmentManager.isTeamCreated())
            teamCotWorkflow.advertiseTeam(assignmentManager.getTeamId(),
                    assignmentManager.getTeamName());
        refreshTeamContactsInternal();
        arrangeTeamMembers();
        refreshOverlay();
        return atakContactSummary;
    }

    public void advertiseTeamIfDue() {
        if (isLeaderRole() && assignmentManager.isTeamCreated())
            teamCotWorkflow.advertiseTeamIfDue(assignmentManager.getTeamId(),
                    assignmentManager.getTeamName());
    }

    public String getAtakContactSummary() {
        return atakContactSummary;
    }

    public boolean isLeaderRole() {
        currentRole = AtakRoleResolver.resolve(mapView);
        return currentRole == AtakRoleResolver.Role.TEAM_LEADER;
    }

    public String getRoleLabel() {
        currentRole = AtakRoleResolver.resolve(mapView);
        return AtakRoleResolver.label(currentRole);
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

    public int getSearchLineColorIndex() {
        return searchLineManager.getColorOption().ordinal();
    }

    public String getSearchLineToleranceLabel() {
        return Math.round(searchLineManager.getReturnMarkToleranceMeters())
                + " m restart tolerance";
    }

    public double getSearchLineToleranceMeters() {
        return searchLineManager.getReturnMarkToleranceMeters();
    }

    public String getSearchLineRestartPrompt() {
        return searchLineManager.getRestartPrompt();
    }

    public String getMemberSearchLineSummary(String uniqueId) {
        return searchLineManager.getMemberCardLineSummary(uniqueId);
    }

    public String getGridProgressSummary() {
        List<SearchGridCell> cells = gridManager.getSelectedAggregateCells();
        if (cells.isEmpty())
            return "Select the current GPS cell to show grid progress.";
        int partial = 0;
        int complete = 0;
        int inProgress = 0;
        for (SearchGridCell cell : cells) {
            if (cell.getStatus() == SearchGridStatus.PARTIAL)
                partial++;
            else if (cell.getStatus() == SearchGridStatus.COMPLETE)
                complete++;
            else if (cell.getStatus() == SearchGridStatus.IN_PROGRESS)
                inProgress++;
        }
        return "1 km area: " + complete + " complete, " + partial
                + " partial, " + inProgress + " in progress, "
                + cells.size() + " cells total";
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
        boolean recording = trackManager.toggleRecording();
        atakTrackBridge.setTracking(recording);
        return recording;
    }

    public boolean toggleTrackVisibility() {
        boolean visible = trackManager.toggleVisible();
        atakTrackBridge.setVisible(visible);
        trackOverlay.setVisible(visible
                && !atakTrackBridge.hasAtakTrackTrail());
        trackOverlay.render(trackManager.getTrackPoints());
        return visible;
    }

    public void clearTrackHistory() {
        trackManager.clearCurrentTrack();
        atakTrackBridge.clearVisibleTrack();
        trackOverlay.render(trackManager.getTrackPoints());
    }

    public String getTrackStatusSummary() {
        return atakTrackBridge.getStatusSummary(trackManager);
    }

    public String getTrackDetailsSummary() {
        return atakTrackBridge.getDetailsSummary(trackManager);
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

    public String getPluginHealthSummary() {
        refreshLocationAvailability();
        return healthManager.getSummary();
    }

    public String getIdentitySummary() {
        return identityManager.getIdentitySummary();
    }

    public String getGpsSummary() {
        refreshLocationAvailability();
        return healthManager.getLocationMessage();
    }

    public boolean isGpsActive() {
        refreshLocationAvailability();
        return healthManager.isLocationActive();
    }

    public void dispose() {
        locationCaptureManager.stop();
        healthManager.stop();
        teamCotWorkflow.dispose();
        backgroundHandler.removeCallbacks(backgroundRunnable);
        unregisterMapListeners();
        gridOverlay.setVisible(false);
        teamMarkerOverlay.setVisible(false);
        trackOverlay.setVisible(false);
        searchLineOverlay.setVisible(false);
    }

    private void refreshOverlay() {
        GeoPoint currentPoint = getCurrentUserPoint();
        if (currentPoint != null)
            searchLineManager.updateLeaderPosition(gridManager
                    .getSelectedCell(), currentPoint);
        arrangeTeamMembers();
        gridOverlay.render(gridManager);
        searchLineOverlay.render(searchLineManager);
        teamMarkerOverlay.render();
        trackOverlay.setVisible(trackManager.isVisible()
                && !atakTrackBridge.hasAtakTrackTrail());
        trackOverlay.render(trackManager.getTrackPoints());
    }

    private void startBackgroundRefresh() {
        backgroundHandler.removeCallbacks(backgroundRunnable);
        backgroundHandler.postDelayed(backgroundRunnable, 5000L);
    }

    private void runBackgroundTeamRefresh() {
        advertiseTeamIfDue();
        if (assignmentManager.isTeamCreated())
            refreshTeamContactsInternal();
        refreshLocationAvailability();
        refreshOverlay();
    }

    private void arrangeTeamMembers() {
        SearchGridCell cell = gridManager.getSelectedCell();
        if (cell == null)
            return;
        GeoPoint leaderPoint = getCurrentUserPoint();
        if (leaderPoint == null)
            return;
        double lineNorthing = searchLineManager.getArrangementNorthing(cell,
                leaderPoint);
        assignmentManager.arrangeMembersForCell(cell, converter, leaderPoint,
                lineNorthing);
    }

    private SearchGridCell ensureSelectedCell() {
        SearchGridCell cell = gridManager.getSelectedCell();
        GeoPoint currentPoint = getCurrentUserPoint();
        if (cell == null && currentPoint != null)
            cell = gridManager.selectCellAt(currentPoint);
        return cell;
    }

    private GeoPoint getCurrentUserPoint() {
        AtakLocationStatus.Snapshot snapshot = AtakLocationStatus.from(mapView);
        if (snapshot.isAvailable())
            return snapshot.getPoint();
        healthManager.recordLocationFailure(snapshot.getMessage());
        return null;
    }

    private void initialiseRuntime() {
        healthManager.start();
        healthManager.setStorageReady(true, "Local storage ready");
        IdentityManager.Identity identity = identityManager.resolveIdentity();
        healthManager.setIdentityResolved(identity.isResolved(),
                identity.getMessage());
        if (identity.isResolved()) {
            assignmentManager.setSelfIdentity(identity.getUid(),
                    identity.getCallsign());
            if (assignmentManager.isTeamCreated())
                assignmentManager.setTeamDetails(assignmentManager
                        .getTeamName(), getFixedLeaderTeamId());
            trackManager.startOrResume(identity.getUid(),
                    identity.getCallsign());
            healthManager.setTrackingActive(trackManager.isRecording());
        } else {
            healthManager.setTrackingActive(false);
        }
        locationCaptureManager.start();
    }

    private void refreshLocationAvailability() {
        AtakLocationStatus.Snapshot snapshot = AtakLocationStatus.from(mapView);
        if (snapshot.isAvailable()) {
            healthManager.recordLocationSuccess(snapshot.getTimestamp(),
                    snapshot.getPoint().getCE(), snapshot.getSource());
        } else {
            healthManager.recordLocationFailure(snapshot.getMessage());
        }
    }

    private void syncSelfTeamMemberFromAtak() {
        AtakLocationStatus.Snapshot snapshot = AtakLocationStatus.from(mapView);
        assignmentManager.updateSelfFromAtak(snapshot,
                gridManager.getSelectedCell());
        if (!snapshot.isAvailable())
            healthManager.recordLocationFailure(snapshot.getMessage());
    }

    private void refreshTeamContactsInternal() {
        if (!assignmentManager.isTeamCreated()) {
            atakContactSummary = isLeaderRole()
                    ? "Create a SARtak team before adding members."
                    : "Join a SARtak team to show team info.";
            return;
        }
        assignmentManager.updateFromAtakContacts(teamContactDataSource
                .getContacts(), getAvailableSelfPoint(), converter);
        atakContactSummary = teamContactDataSource.describeLastScan(
                assignmentManager.getLastAtakMatchedMembers());
    }

    private AtakTeamContactDataSource.ContactSnapshot findContact(
            String uniqueId) {
        if (uniqueId == null)
            return null;
        for (AtakTeamContactDataSource.ContactSnapshot contact
                : teamContactDataSource.getContacts()) {
            if (uniqueId.equals(contact.getUid()))
                return contact;
        }
        return null;
    }

    private boolean containsTeam(List<SearchTeamCotMessage> teams,
            String teamId) {
        for (SearchTeamCotMessage team : teams) {
            if (teamId.equals(team.getTeamId()))
                return true;
        }
        return false;
    }

    private List<SearchTeamCotMessage> dedupeTeams(
            List<SearchTeamCotMessage> teams) {
        java.util.LinkedHashMap<String, SearchTeamCotMessage> byTeam =
                new java.util.LinkedHashMap<>();
        for (SearchTeamCotMessage team : teams) {
            String key = team.getTeamId() == null
                    || team.getTeamId().length() == 0
                            ? team.getLeaderUid() : team.getTeamId();
            byTeam.put(key, team);
        }
        return new java.util.ArrayList<>(byTeam.values());
    }

    private GeoPoint getAvailableSelfPoint() {
        AtakLocationStatus.Snapshot snapshot = AtakLocationStatus.from(mapView);
        return snapshot.isAvailable() ? snapshot.getPoint() : null;
    }

    private String getFixedLeaderTeamId() {
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        String uid = identity != null && identity.isResolved()
                ? identity.getUid() : MapView.getDeviceUid();
        return "TEAM-" + uid;
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
