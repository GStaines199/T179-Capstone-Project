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
import com.atakmap.android.plugintemplate.grid.SearchGridDisplayFormatter;
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
import com.atakmap.android.plugintemplate.runtime.DittoSyncManager;
import com.atakmap.android.plugintemplate.runtime.IdentityManager;
import com.atakmap.android.plugintemplate.runtime.LocationCaptureManager;
import com.atakmap.android.plugintemplate.runtime.PluginHealthManager;
import com.atakmap.android.plugintemplate.runtime.SearchGridCotMessage;
import com.atakmap.android.plugintemplate.runtime.SearchGridCotWorkflow;
import com.atakmap.android.plugintemplate.runtime.SearchLineCotMessage;
import com.atakmap.android.plugintemplate.runtime.SearchLineCotWorkflow;
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
    private final SearchGridCotWorkflow gridCotWorkflow;
    private final SearchLineCotWorkflow searchLineCotWorkflow;
    private final DittoSyncManager dittoSyncManager;
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
        this.assignmentManager = new SearchPartyAssignmentManager(searcherRepository);
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
        this.gridCotWorkflow = new SearchGridCotWorkflow(mapView,
                identityManager);
        this.searchLineCotWorkflow = new SearchLineCotWorkflow(mapView,
                identityManager);
        this.dittoSyncManager = new DittoSyncManager(mapView, identityManager);
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

    public boolean toggleGridMapLabels() {
        boolean showing = gridOverlay.toggleLabels();
        refreshOverlay();
        return showing;
    }

    public boolean isShowingGridMapLabels() {
        return gridOverlay.isShowingLabels();
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
        if (cell == null || currentPoint == null)
            return;
        searchLineManager.start(cell, currentPoint);
        publishSearchLine(SearchLineCotMessage.ACTION_START);
        refreshOverlay();
    }

    public void endSearchLine() {
        searchLineManager.end();
        publishSearchLine(SearchLineCotMessage.ACTION_END);
        refreshOverlay();
    }

    public void pauseSearchLine() {
        searchLineManager.pause();
        publishSearchLine(SearchLineCotMessage.ACTION_PAUSE);
        refreshOverlay();
    }

    public boolean resumeSearchLine() {
        boolean resumed = searchLineManager.resume();
        if (resumed)
            publishSearchLine(SearchLineCotMessage.ACTION_RESUME);
        refreshOverlay();
        return resumed;
    }

    public void forceResumeSearchLine() {
        searchLineManager.forceResume();
        publishSearchLine(SearchLineCotMessage.ACTION_RESUME);
        refreshOverlay();
    }

    public String cycleSearchLineColor() {
        String label = searchLineManager.cycleColor().getLabel();
        publishSearchLine(SearchLineCotMessage.ACTION_UPDATE);
        refreshOverlay();
        return label;
    }

    public void setSearchLineColor(SearchLineColorOption colorOption) {
        searchLineManager.setColorOption(colorOption);
        publishSearchLine(SearchLineCotMessage.ACTION_UPDATE);
        refreshOverlay();
    }

    public void setSearchLineTolerance(double toleranceMeters) {
        searchLineManager.setReturnMarkToleranceMeters(toleranceMeters);
        publishSearchLine(SearchLineCotMessage.ACTION_UPDATE);
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
        publishSelectedGridStatus(SearchGridStatus.PARTIAL);
        refreshOverlay();
    }

    public void markSelectedComplete() {
        gridManager.setSelectedStatus(SearchGridStatus.COMPLETE);
        publishSelectedGridStatus(SearchGridStatus.COMPLETE);
        refreshOverlay();
    }

    public void clearSelectedStatus() {
        gridManager.setSelectedStatus(SearchGridStatus.NOT_STARTED);
        publishSelectedGridStatus(SearchGridStatus.NOT_STARTED);
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

    public String getSelectedCellDisplaySummary() {
        SearchGridCell cell = gridManager.getSelectedCell();
        return SearchGridDisplayFormatter.formatCellSummaryWithStatus(cell);
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
        publishTeamPresence();
        refreshOverlay();
    }

    public void removeTeam() {
        if (assignmentManager.isTeamCreated()) {
            teamCotWorkflow.removeTeam(assignmentManager.getTeamId(),
                    assignmentManager.getTeamName());
        }
        leaveTeam();
    }

    public void leaveTeam() {
        assignmentManager.clearTeam();
        teamStateStore.clear();
        refreshTeamContactsInternal();
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
                    request.getSenderUid(), request.getSenderCallsign());
            if (contact != null) {
                assignmentManager.addTeamMember(contact,
                        getAvailableSelfPoint(), converter);
            } else {
                assignmentManager.addConfirmedRosterMember(
                        request.getSenderUid(), request.getSenderCallsign(),
                        SearchTeamMember.TeamRole.SEARCHER);
            }
            teamStateStore.save(assignmentManager);
            publishTeamPresence();
            refreshOverlay();
        }
    }

    public void acceptJoinResponse(SearchTeamCotMessage response) {
        assignmentManager.joinTeam(response.getTeamName(),
                response.getTeamId());
        addLeaderFromContact(response.getLeaderUid(),
                response.getLeaderCallsign());
        teamStateStore.save(assignmentManager);
        publishTeamPresence();
        refreshOverlay();
    }

    public void respondToInvite(SearchTeamCotMessage invite,
            boolean accepted) {
        teamCotWorkflow.respondToInvite(invite, accepted);
        if (accepted) {
            assignmentManager.joinTeam(invite.getTeamName(),
                    invite.getTeamId());
            addLeaderFromContact(invite.getLeaderUid(),
                    invite.getLeaderCallsign());
            teamStateStore.save(assignmentManager);
            publishTeamPresence();
            refreshOverlay();
        }
    }

    public boolean acceptInviteResponse(SearchTeamCotMessage response) {
        if (!SearchTeamCotMessage.ACTION_INVITE_ACCEPT.equals(
                response.getAction()))
            return false;
        boolean added = addTeamMemberFromResponse(response);
        if (added)
            teamStateStore.save(assignmentManager);
        if (added)
            publishTeamPresence();
        return added;
    }

    public List<SearchTeamCotMessage> getActiveTeamAdvertisements() {
        // Only show teams that have explicitly advertised over the SARtak CoT
        // workflow. A visible ATAK contact is not enough to prove that a SARtak
        // team exists, and inventing one makes member devices join fake teams.
        return dedupeTeams(new java.util.ArrayList<>(teamCotWorkflow
                .getTeamAdvertisements()));
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

    private boolean addLeaderFromContact(String leaderUid,
            String leaderCallsign) {
        AtakTeamContactDataSource.ContactSnapshot contact = findContact(
                leaderUid, leaderCallsign);
        if (contact == null) {
            SearchTeamMember fallback = assignmentManager
                    .addConfirmedRosterMember(leaderUid, leaderCallsign,
                            SearchTeamMember.TeamRole.TEAM_LEADER);
            arrangeTeamMembers();
            return fallback != null;
        }
        SearchTeamMember leader = assignmentManager.addTeamMember(contact,
                getAvailableSelfPoint(), converter);
        if (leader == null)
            return false;
        leader.setRole(SearchTeamMember.TeamRole.TEAM_LEADER);
        arrangeTeamMembers();
        return true;
    }

    private boolean addTeamMemberFromResponse(SearchTeamCotMessage response) {
        AtakTeamContactDataSource.ContactSnapshot contact = findContact(
                response.getSenderUid(), response.getSenderCallsign());
        SearchTeamMember member = contact == null
                ? assignmentManager.addConfirmedRosterMember(
                        response.getSenderUid(), response.getSenderCallsign(),
                        SearchTeamMember.TeamRole.SEARCHER)
                : assignmentManager.addTeamMember(contact,
                        getAvailableSelfPoint(), converter);
        arrangeTeamMembers();
        refreshOverlay();
        return member != null;
    }

    public List<AtakTeamContactDataSource.ContactSnapshot> getAvailableContacts() {
        java.util.List<AtakTeamContactDataSource.ContactSnapshot> available =
                new java.util.ArrayList<>();
        String selfUid = assignmentManager.getSelfMemberId();
        for (AtakTeamContactDataSource.ContactSnapshot contact
                : teamContactDataSource.getContacts()) {
            if (contact.getUid() == null || contact.getUid().equals(selfUid))
                continue;
            if (assignmentManager.findMemberById(contact.getUid()) != null)
                continue;
            available.add(contact);
        }
        java.util.Collections.sort(available,
                new java.util.Comparator<AtakTeamContactDataSource.ContactSnapshot>() {
                    @Override
                    public int compare(
                            AtakTeamContactDataSource.ContactSnapshot first,
                            AtakTeamContactDataSource.ContactSnapshot second) {
                        int group = first.getAtakGroupName()
                                .compareToIgnoreCase(second.getAtakGroupName());
                        if (group != 0)
                            return group;
                        return first.getCallsign().compareToIgnoreCase(
                                second.getCallsign());
                    }
                });
        return available;
    }

    public boolean removeTeamMemberFromSetup(String uniqueId) {
        boolean removed = assignmentManager.removeTeamMember(uniqueId);
        arrangeTeamMembers();
        refreshOverlay();
        if (removed)
            teamStateStore.save(assignmentManager);
        if (removed)
            publishTeamPresence();
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
        if (assignmentManager.isTeamCreated())
            publishTeamPresenceIfDue();
        else
            teamCotWorkflow.publishPresenceIfDue("", "", "", "",
                    "", 0, "", 0, "");
        teamCotWorkflow.republishPendingMessagesIfDue();
    }

    public String getAtakContactSummary() {
        return atakContactSummary;
    }

    public String getTeamSyncSummary() {
        if (!assignmentManager.isTeamCreated())
            return atakContactSummary + "\n" + dittoSyncManager.getSummary();
        return "CoT: sent " + formatAge(teamCotWorkflow
                .getLastPresenceSentTime())
                + " | received " + formatAge(teamCotWorkflow
                        .getLatestPresenceReceivedTime(assignmentManager
                                .getTeamId()))
                + "\n" + atakContactSummary
                + "\nMembers: " + assignmentManager.getVisibleMemberCount()
                + " synced, " + assignmentManager.getConnectedMemberCount()
                + " connected, " + assignmentManager.getStaleMemberCount()
                + " stale/disconnected"
                + "\n" + dittoSyncManager.getSummary();
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

    public TeamMarkerVisibilityMode getTeamMarkerVisibilityMode() {
        return teamMarkerOverlay.getVisibilityMode();
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
        return "UTM: " + SearchGridDisplayFormatter.formatParentUtm(
                cells.get(0)) + "\n1 km area: " + complete + " complete, " + partial
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

    public boolean hasAtakTrackTrail() {
        return atakTrackBridge.hasAtakTrackTrail();
    }

    public boolean hasVisibleTrackData() {
        return hasAtakTrackTrail() || !trackManager.getTrackPoints().isEmpty();
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
        dittoSyncManager.stop();
        teamCotWorkflow.dispose();
        gridCotWorkflow.dispose();
        searchLineCotWorkflow.dispose();
        backgroundHandler.removeCallbacks(backgroundRunnable);
        unregisterMapListeners();
        gridOverlay.setVisible(false);
        teamMarkerOverlay.setVisible(false);
        trackOverlay.setVisible(false);
        searchLineOverlay.setVisible(false);
    }

    private void refreshOverlay() {
        applyRemoteGridStatusMessages();
        applyRemoteSearchLineIfAvailable();
        GeoPoint currentPoint = getCurrentUserPoint();
        if (currentPoint != null && isLeaderRole()
                && !searchLineManager.isRemoteControlled())
            searchLineManager.updateLeaderPosition(gridManager
                    .getSelectedCell(), currentPoint);
        arrangeTeamMembers();
        gridOverlay.render(gridManager);
        searchLineOverlay.render(searchLineManager);
        teamMarkerOverlay.render();
        trackOverlay.setVisible(trackManager.isVisible()
                && !atakTrackBridge.hasAtakTrackTrail());
        trackOverlay.render(trackManager.getTrackPoints());
        publishSearchLineUpdateIfDue();
    }

    private void startBackgroundRefresh() {
        backgroundHandler.removeCallbacks(backgroundRunnable);
        backgroundHandler.postDelayed(backgroundRunnable, 5000L);
    }

    private void runBackgroundTeamRefresh() {
        advertiseTeamIfDue();
        if (assignmentManager.isTeamCreated())
            refreshTeamContactsInternal();
        applyRemoteSearchLineIfAvailable();
        applyRemoteGridStatusMessages();
        refreshLocationAvailability();
        publishDittoDeviceStateIfDue();
        applyDittoDeviceSnapshots();
        refreshOverlay();
    }

    private void publishSelectedGridStatus(SearchGridStatus status) {
        if (!isLeaderRole() || !assignmentManager.isTeamCreated())
            return;
        SearchGridCell cell = gridManager.getSelectedCell();
        if (cell == null)
            return;
        gridCotWorkflow.publishStatus(assignmentManager.getTeamId(),
                cell.getId(), status);
    }

    private void applyRemoteGridStatusMessages() {
        if (!assignmentManager.isTeamCreated())
            return;
        for (SearchGridCotMessage message : gridCotWorkflow
                .consumeMessagesForTeam(assignmentManager.getTeamId()))
            gridManager.setCellStatus(message.getCellId(),
                    message.getStatus());
    }

    private void publishSearchLine(String action) {
        if (!isLeaderRole() || !assignmentManager.isTeamCreated())
            return;
        if (SearchLineCotMessage.ACTION_UPDATE.equals(action)
                && searchLineManager.isPaused())
            action = SearchLineCotMessage.ACTION_PAUSE;
        searchLineCotWorkflow.publishNow(action, assignmentManager.getTeamId(),
                searchLineManager);
    }

    private void publishSearchLineUpdateIfDue() {
        if (!isLeaderRole() || !assignmentManager.isTeamCreated()
                || !searchLineManager.isStarted()
                || searchLineManager.isRemoteControlled())
            return;
        searchLineCotWorkflow.publishUpdateIfDue(assignmentManager.getTeamId(),
                searchLineManager);
    }

    private void applyRemoteSearchLineIfAvailable() {
        if (isLeaderRole() || !assignmentManager.isTeamCreated())
            return;
        SearchLineCotMessage message = searchLineCotWorkflow
                .getLatestForTeam(assignmentManager.getTeamId());
        if (message == null)
            return;
        searchLineManager.applyRemote(message);
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
        dittoSyncManager.start();
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
        List<AtakTeamContactDataSource.ContactSnapshot> contacts =
                teamContactDataSource.getContacts();
        assignmentManager.updateFromAtakContacts(contacts,
                getAvailableSelfPoint(), converter);
        boolean presenceChanged = assignmentManager.updateFromPresence(
                teamCotWorkflow.getPresenceForTeam(assignmentManager
                        .getTeamId()), contacts, getAvailableSelfPoint(),
                converter);
        boolean dittoChanged = assignmentManager.updateFromDittoDevices(
                dittoSyncManager.getDeviceSnapshots(), getAvailableSelfPoint(),
                converter);
        assignmentManager.updateConnectionAges();
        if (presenceChanged || dittoChanged)
            teamStateStore.save(assignmentManager);
        atakContactSummary = teamContactDataSource.describeLastScan(
                assignmentManager.getLastAtakMatchedMembers());
    }

    private void publishDittoDeviceStateIfDue() {
        SearchTeamMember self = assignmentManager.getSelfMember();
        dittoSyncManager.publishDeviceStateIfDue(
                assignmentManager.isTeamCreated(), assignmentManager
                        .getTeamId(), assignmentManager.getTeamName(),
                assignmentManager.getLeaderUid(), assignmentManager
                        .getLeaderCallsign(),
                getRoleLabel(), assignmentManager.getTeamColorName(),
                assignmentManager.getTeamColorArgb(), self,
                gridManager.getSelectedCell(), AtakLocationStatus.from(
                        mapView));
    }

    private void applyDittoDeviceSnapshots() {
        if (!assignmentManager.isTeamCreated())
            return;
        boolean changed = assignmentManager.updateFromDittoDevices(
                dittoSyncManager.getDeviceSnapshots(), getAvailableSelfPoint(),
                converter);
        if (changed)
            teamStateStore.save(assignmentManager);
    }

    private String formatAge(long timestamp) {
        if (timestamp <= 0L)
            return "never";
        long seconds = Math.max(0L, (System.currentTimeMillis() - timestamp)
                / 1000L);
        return seconds <= 1L ? "now" : seconds + " sec ago";
    }

    private void publishTeamPresence() {
        if (!assignmentManager.isTeamCreated())
            return;
        SearchTeamMember self = assignmentManager.getSelfMember();
        teamCotWorkflow.publishPresence(assignmentManager.getTeamId(),
                assignmentManager.getTeamName(),
                assignmentManager.getLeaderUid(),
                assignmentManager.getLeaderCallsign(),
                assignmentManager.getTeamColorName(),
                assignmentManager.getTeamColorArgb(),
                self == null ? "" : self.getColorName(),
                self == null ? 0 : self.getDisplayColor(),
                self == null ? "" : self.getRoleLabel());
    }

    private void publishTeamPresenceIfDue() {
        if (!assignmentManager.isTeamCreated())
            return;
        SearchTeamMember self = assignmentManager.getSelfMember();
        teamCotWorkflow.publishPresenceIfDue(assignmentManager.getTeamId(),
                assignmentManager.getTeamName(),
                assignmentManager.getLeaderUid(),
                assignmentManager.getLeaderCallsign(),
                assignmentManager.getTeamColorName(),
                assignmentManager.getTeamColorArgb(),
                self == null ? "" : self.getColorName(),
                self == null ? 0 : self.getDisplayColor(),
                self == null ? "" : self.getRoleLabel());
    }

    private AtakTeamContactDataSource.ContactSnapshot findContact(
            String uniqueId) {
        return findContact(uniqueId, "");
    }

    private AtakTeamContactDataSource.ContactSnapshot findContact(
            String uniqueId, String callsign) {
        if (uniqueId == null)
            uniqueId = "";
        if (callsign == null)
            callsign = "";
        String targetUid = uniqueId.trim();
        String targetCallsign = callsign.trim();
        for (AtakTeamContactDataSource.ContactSnapshot contact
                : teamContactDataSource.getContacts()) {
            if (targetUid.length() > 0 && targetUid.equals(contact.getUid()))
                return contact;
            if (targetCallsign.length() > 0
                    && targetCallsign.equalsIgnoreCase(contact.getCallsign()))
                return contact;
        }
        return null;
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
