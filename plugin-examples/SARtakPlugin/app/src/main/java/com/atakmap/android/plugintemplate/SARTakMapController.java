package com.atakmap.android.plugintemplate;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;

import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.plugintemplate.database.DatabaseHelper;
import com.atakmap.android.plugintemplate.database.LocationRepository;
import com.atakmap.android.plugintemplate.database.SearcherRepository;
import com.atakmap.android.plugintemplate.database.StorageAvailability;
import com.atakmap.android.plugintemplate.database.TrackSessionRepository;
import com.atakmap.android.plugintemplate.grid.GridCoordinateConverter;
import com.atakmap.android.plugintemplate.grid.MemberPositionPolicy;
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
import com.atakmap.android.plugintemplate.runtime.DeviceConnectivitySnapshot;
import com.atakmap.android.plugintemplate.runtime.DittoCredentialProfile;
import com.atakmap.android.plugintemplate.runtime.DittoCredentialStore;
import com.atakmap.android.plugintemplate.runtime.DittoAtakContactBridge;
import com.atakmap.android.plugintemplate.runtime.DittoDeviceSnapshot;
import com.atakmap.android.plugintemplate.runtime.DittoTeamMembershipSnapshot;
import com.atakmap.android.plugintemplate.runtime.DittoSyncManager;
import com.atakmap.android.plugintemplate.runtime.IdentityManager;
import com.atakmap.android.plugintemplate.runtime.LocationCaptureManager;
import com.atakmap.android.plugintemplate.runtime.OperationProfile;
import com.atakmap.android.plugintemplate.runtime.OperationStateStore;
import com.atakmap.android.plugintemplate.runtime.PluginHealthManager;
import com.atakmap.android.plugintemplate.runtime.RawGnssCapture;
import com.atakmap.android.plugintemplate.runtime.RawGnssCaptureManager;
import com.atakmap.android.plugintemplate.runtime.SearchGridCotMessage;
import com.atakmap.android.plugintemplate.runtime.SearchGridCotWorkflow;
import com.atakmap.android.plugintemplate.runtime.SearchAlertMessage;
import com.atakmap.android.plugintemplate.runtime.SearchLineCotMessage;
import com.atakmap.android.plugintemplate.runtime.SearchLineCotWorkflow;
import com.atakmap.android.plugintemplate.runtime.SearchTeamCotMessage;
import com.atakmap.android.plugintemplate.runtime.SearchTeamCotWorkflow;
import com.atakmap.android.plugintemplate.runtime.SharedMapMarkerSyncManager;
import com.atakmap.android.plugintemplate.plugin.BuildConfig;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SARTakMapController {

    private static final String TAG = "SARTakMapController";

    public static final int READINESS_BLOCKED = 0;
    public static final int READINESS_WAITING = 1;
    public static final int READINESS_READY = 2;

    private static final double MIN_HEADING_SPEED_METERS_PER_SECOND = 0.4;
    private static final double MAX_REASONABLE_SEARCH_SPEED_METERS_PER_SECOND =
            12.0;

    private final MapView mapView;
    private final GridCoordinateConverter converter;
    private final SearchGridStateStore gridStateStore;
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
    private final RawGnssCaptureManager rawGnssCaptureManager;
    private final AtakTeamContactDataSource teamContactDataSource;
    private final SearchTeamCotWorkflow teamCotWorkflow;
    private final SearchGridCotWorkflow gridCotWorkflow;
    private final SearchLineCotWorkflow searchLineCotWorkflow;
    private final DittoSyncManager dittoSyncManager;
    private final DittoCredentialStore dittoCredentialStore;
    private final DittoAtakContactBridge dittoAtakContactBridge;
    private final SharedMapMarkerSyncManager sharedMapMarkerSyncManager;
    private final SearchTeamStateStore teamStateStore;
    private final OperationStateStore operationStateStore;
    private final DatabaseHelper databaseHelper;
    private final MapEventDispatcher.MapEventDispatchListener mapEventListener;
    private final Handler backgroundHandler = new Handler(Looper.getMainLooper());
    private final Runnable backgroundRunnable;
    private final java.util.Map<String, Long> rosterJoinTimes =
            new java.util.HashMap<>();
    private final java.util.Map<String, Long> inactiveMembershipTimes =
            new java.util.HashMap<>();
    private final java.util.Set<String> appliedTeamMessageIds =
            new java.util.HashSet<>();
    private long localTeamJoinTime;
    private String atakContactSummary = "ATAK contacts not scanned yet";
    private AtakRoleResolver.Role currentRole = AtakRoleResolver.Role.TEAM_MEMBER;
    private OperationProfile activeOperationProfile;

    public SARTakMapController(MapView mapView, Context pluginContext) {
        this.mapView = mapView;
        this.converter = new GridCoordinateConverter();
        // ATAK plugin contexts are suitable for resources/layout inflation, but
        // runtime files belong under ATAK's writable app context.
        Context runtimeContext = mapView.getContext();
        this.databaseHelper = DatabaseHelper.getInstance(runtimeContext);
        SearcherRepository searcherRepository = new SearcherRepository(
                databaseHelper);
        TrackSessionRepository trackSessionRepository =
                new TrackSessionRepository(databaseHelper);
        LocationRepository locationRepository = new LocationRepository(
                databaseHelper);
        this.operationStateStore = new OperationStateStore(runtimeContext);
        this.activeOperationProfile = operationStateStore.load();
        this.dittoCredentialStore = new DittoCredentialStore(runtimeContext);
        this.gridStateStore = new SearchGridStateStore(runtimeContext);
        this.gridStateStore.setOperationId(getActiveOperationId());
        this.assignmentManager = new SearchPartyAssignmentManager(searcherRepository);
        this.teamStateStore = new SearchTeamStateStore(runtimeContext);
        this.teamStateStore.setOperationId(getActiveOperationId());
        this.teamStateStore.load(assignmentManager);
        this.gridManager = new SearchGridManager(converter, gridStateStore);
        this.gridOverlay = new SearchGridOverlay(mapView, converter,
                assignmentManager);
        this.searchLineManager = new SearchLineManager(converter,
                assignmentManager);
        this.teamMarkerOverlay = new SearchTeamMarkerOverlay(mapView,
                assignmentManager, searchLineManager);
        this.trackManager = new SearchTrackManager(trackSessionRepository,
                locationRepository);
        this.trackManager.setOperationId(getActiveOperationId());
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
        this.dittoAtakContactBridge = new DittoAtakContactBridge(mapView);
        this.sharedMapMarkerSyncManager = new SharedMapMarkerSyncManager(
                mapView, identityManager, dittoSyncManager);
        this.dittoSyncManager.useOperationProfile(activeOperationProfile);
        this.teamCotWorkflow.setDittoSyncManager(dittoSyncManager);
        this.gridCotWorkflow.setDittoSyncManager(dittoSyncManager);
        this.searchLineCotWorkflow.setDittoSyncManager(dittoSyncManager);
        applyOperationIdToWorkflows();
        this.rawGnssCaptureManager = new RawGnssCaptureManager(runtimeContext,
                identityManager, trackManager, locationRepository,
                new RawGnssCaptureManager.Listener() {
                    @Override
                    public void onRawGnssCaptured(RawGnssCapture capture) {
                        refreshOverlay();
                    }
                });
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

    public void planSearchAreaFromCurrentCell() {
        SearchGridCell cell = ensureSelectedCell();
        if (cell == null)
            return;
        gridManager.planSelectedAggregateArea();
        gridOverlay.setVisible(true);
        refreshOverlay();
    }

    public void planBoxSearchArea(double widthKm, double heightKm) {
        GeoPoint currentPoint = getCurrentUserPoint();
        if (currentPoint == null)
            return;
        gridManager.planBoxAt(currentPoint, widthKm, heightKm);
        searchLineManager.updateLeaderPosition(gridManager.getSelectedCell(),
                currentPoint);
        gridOverlay.setVisible(true);
        arrangeTeamMembers();
        refreshOverlay();
    }

    public void planCircleSearchArea(double radiusKm) {
        GeoPoint currentPoint = getCurrentUserPoint();
        if (currentPoint == null)
            return;
        gridManager.planCircleAt(currentPoint, radiusKm);
        searchLineManager.updateLeaderPosition(gridManager.getSelectedCell(),
                currentPoint);
        gridOverlay.setVisible(true);
        arrangeTeamMembers();
        refreshOverlay();
    }

    public void clearPlannedSearchArea() {
        gridManager.clearPlannedArea();
        refreshOverlay();
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

    public boolean sendTeamAlert(String alertType) {
        if (!assignmentManager.isTeamCreated())
            return false;
        IdentityManager.Identity identity = identityManager.resolveIdentity();
        if (identity == null || !identity.isResolved())
            return false;
        long now = System.currentTimeMillis();
        String alertId = "sartak-alert-" + assignmentManager.getTeamId()
                + "-" + identity.getUid() + "-" + alertType + "-" + now;
        GeoPoint point = getAvailableSelfPoint();
        boolean requiresHalt = SearchAlertMessage.requiresHalt(alertType);
        SearchAlertMessage message = new SearchAlertMessage(
                alertId + "-active", SearchAlertMessage.ACTION_ACTIVE,
                alertId, alertType, SearchAlertMessage.titleForType(alertType),
                SearchAlertMessage.messageForType(alertType,
                        identity.getCallsign()),
                assignmentManager.getTeamId(), assignmentManager.getTeamName(),
                identity.getUid(), identity.getCallsign(), "", "",
                point == null ? Double.NaN : point.getLatitude(),
                point == null ? Double.NaN : point.getLongitude(),
                requiresHalt, now, getActiveOperationId());
        boolean sent = dittoSyncManager.publishAlert(message);
        if (sent && requiresHalt)
            pauseSearchLineForAlert();
        return sent;
    }

    public boolean cancelTeamAlert(SearchAlertMessage alert) {
        if (alert == null || !assignmentManager.isTeamCreated())
            return false;
        IdentityManager.Identity identity = identityManager.resolveIdentity();
        if (identity == null || !identity.isResolved())
            return false;
        long now = System.currentTimeMillis();
        SearchAlertMessage cancel = new SearchAlertMessage(
                alert.getAlertId() + "-cancel-" + identity.getUid() + "-"
                        + now,
                SearchAlertMessage.ACTION_CANCEL, alert.getAlertId(),
                alert.getAlertType(), "Alert Cleared",
                SearchAlertMessage.messageForType(
                        SearchAlertMessage.TYPE_RESUME_SEARCH,
                        identity.getCallsign()),
                assignmentManager.getTeamId(), assignmentManager.getTeamName(),
                identity.getUid(), identity.getCallsign(), "", "",
                Double.NaN, Double.NaN, false, now, getActiveOperationId());
        return dittoSyncManager.publishAlert(cancel);
    }

    public boolean acknowledgeAlert(SearchAlertMessage alert) {
        if (alert == null || !assignmentManager.isTeamCreated())
            return false;
        IdentityManager.Identity identity = identityManager.resolveIdentity();
        if (identity == null || !identity.isResolved())
            return false;
        long now = System.currentTimeMillis();
        SearchAlertMessage ack = new SearchAlertMessage(
                alert.getAlertId() + "-ack-" + identity.getUid() + "-"
                        + now,
                SearchAlertMessage.ACTION_ACK, alert.getAlertId(),
                alert.getAlertType(), alert.getTitle(), "Acknowledged",
                alert.getTeamId(), alert.getTeamName(),
                alert.getSenderUid(), alert.getSenderCallsign(),
                identity.getUid(), identity.getCallsign(), Double.NaN,
                Double.NaN, alert.requiresHalt(), now,
                getActiveOperationId());
        boolean sent = dittoSyncManager.publishAlert(ack);
        if (alert.requiresHalt())
            pauseSearchLineForAlert();
        return sent;
    }

    public List<SearchAlertMessage> getActiveTeamAlerts() {
        return getActiveAlerts(false);
    }

    public List<SearchAlertMessage> getOutgoingActiveAlerts() {
        return getActiveAlerts(true);
    }

    public List<SearchAlertMessage> getUnacknowledgedAlertsForMe() {
        List<SearchAlertMessage> result = new ArrayList<>();
        String selfUid = getSelfMemberId();
        for (SearchAlertMessage alert : getActiveTeamAlerts()) {
            if (selfUid.length() > 0 && selfUid.equals(alert.getSenderUid()))
                continue;
            if (!hasAckFrom(alert, selfUid))
                result.add(alert);
        }
        return result;
    }

    public boolean hasAcknowledgedAlert(SearchAlertMessage alert) {
        return hasAckFrom(alert, getSelfMemberId());
    }

    public boolean hasActiveAlertType(String alertType) {
        String selfUid = getSelfMemberId();
        for (SearchAlertMessage alert : getOutgoingActiveAlerts()) {
            if (alert.getAlertType().equals(alertType)
                    && alert.getSenderUid().equals(selfUid))
                return true;
        }
        return false;
    }

    public SearchAlertMessage getOutgoingAlertOfType(String alertType) {
        String selfUid = getSelfMemberId();
        for (SearchAlertMessage alert : getOutgoingActiveAlerts()) {
            if (alert.getAlertType().equals(alertType)
                    && alert.getSenderUid().equals(selfUid))
                return alert;
        }
        return null;
    }

    public String getAlertSummary() {
        List<SearchAlertMessage> alerts = getActiveTeamAlerts();
        if (alerts.isEmpty())
            return "No active SARtak alerts";
        StringBuilder builder = new StringBuilder();
        for (SearchAlertMessage alert : alerts) {
            if (builder.length() > 0)
                builder.append('\n');
            builder.append(alert.getTitle()).append(" from ")
                    .append(alert.getSenderCallsign());
            if (alert.getSenderUid().equals(getSelfMemberId()))
                builder.append(" | ").append(getAlertAckSummary(alert));
        }
        return builder.toString();
    }

    public String getAlertAckSummary(SearchAlertMessage alert) {
        if (alert == null)
            return "No alert selected";
        java.util.Set<String> expected = new java.util.LinkedHashSet<>();
        java.util.Map<String, String> names = new java.util.LinkedHashMap<>();
        for (SearchTeamMember member : assignmentManager.getVisibleMembers()) {
            if (member.getUniqueId().equals(alert.getSenderUid()))
                continue;
            expected.add(member.getUniqueId());
            names.put(member.getUniqueId(), member.getCallsign());
        }
        java.util.Set<String> acknowledged = new java.util.LinkedHashSet<>();
        for (SearchAlertMessage message : dittoSyncManager.getAlertMessages()) {
            if (message.getAlertId().equals(alert.getAlertId())
                    && SearchAlertMessage.ACTION_ACK.equals(message
                            .getAction())
                    && message.getCreated() >= alert.getCreated()
                    && expected.contains(message.getAckUid()))
                acknowledged.add(message.getAckUid());
        }
        StringBuilder missing = new StringBuilder();
        for (String uid : expected) {
            if (!acknowledged.contains(uid)) {
                if (missing.length() > 0)
                    missing.append(", ");
                missing.append(names.get(uid));
            }
        }
        String summary = acknowledged.size() + "/" + expected.size()
                + " acknowledged";
        return missing.length() == 0 ? summary : summary + " | Waiting: "
                + missing;
    }

    public boolean toggleTeamCallsigns() {
        boolean showing = teamMarkerOverlay.toggleCallsigns();
        syncDittoDevicesToAtakMarkers();
        return showing;
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

    public String getNextCellDisplaySummary() {
        SearchGridCell cell = gridManager.getNextPlannedCell();
        if (cell == null)
            return "Next cell: not planned";
        return "Next: " + SearchGridDisplayFormatter.formatCellCompact(cell);
    }

    public String getPlannedSearchAreaSummary() {
        if (!gridManager.hasPlannedArea())
            return "No planned search area. Select your current GPS cell, then plan the search area.";
        SearchGridCell cell = gridManager.getSelectedCell();
        String utm = cell == null ? "" : " from "
                + SearchGridDisplayFormatter.formatParentUtm(cell);
        return gridManager.getPlannedAreaDescription() + utm + "\nApprox "
                + gridManager.getPlannedCellEstimate()
                + " x 100 m cells. Visible cells are generated on demand.";
    }

    public List<SearchGridCell> getGridReviewCells() {
        return gridManager.getReviewCells();
    }

    public void selectGridCell(String cellId) {
        SearchGridCell cell = gridManager.selectCellById(cellId);
        if (cell == null)
            return;
        GeoPoint currentPoint = getCurrentUserPoint();
        if (currentPoint != null)
            searchLineManager.updateLeaderPosition(cell, currentPoint);
        arrangeTeamMembers();
        refreshOverlay();
    }

    public void focusGridCell(String cellId) {
        SearchGridCell cell = gridManager.selectCellById(cellId);
        if (cell == null)
            return;
        GeoPoint center = converter.toGeoPoint(cell.getZoneDescriptor(),
                (cell.getWest() + cell.getEast()) / 2.0,
                (cell.getSouth() + cell.getNorth()) / 2.0);
        mapView.getMapController().panTo(center, true);
        refreshOverlay();
    }

    public String getPendingGridReviewCellId() {
        SearchGridCell cell = gridManager.getPendingReviewCell();
        return cell == null ? "" : cell.getId();
    }

    public String getPendingGridReviewCellSummary() {
        SearchGridCell cell = gridManager.getPendingReviewCell();
        return cell == null ? ""
                : SearchGridDisplayFormatter.formatCellCompact(cell);
    }

    public void markGridCellPartial(String cellId) {
        gridManager.setCellStatus(cellId, SearchGridStatus.PARTIAL);
        publishGridStatusForCell(cellId, SearchGridStatus.PARTIAL);
        gridManager.clearPendingReviewCell(cellId);
        refreshOverlay();
    }

    public void markGridCellComplete(String cellId) {
        gridManager.setCellStatus(cellId, SearchGridStatus.COMPLETE);
        publishGridStatusForCell(cellId, SearchGridStatus.COMPLETE);
        gridManager.clearPendingReviewCell(cellId);
        refreshOverlay();
    }

    public void deferGridCellReview(String cellId) {
        gridManager.clearPendingReviewCell(cellId);
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

    public String getTeamColorName() {
        return assignmentManager.getTeamColorName();
    }

    public int getTeamColorArgb() {
        return assignmentManager.getTeamColorArgb();
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

    public boolean hasActiveOperation() {
        return activeOperationProfile != null
                && activeOperationProfile.getOperationId().length() > 0;
    }

    public boolean canCreateOperationFromLocalDittoConfig() {
        return getOperationCredentialProfile() != null;
    }

    public boolean canCreateOperation() {
        return isLeaderRole() && canCreateOperationFromLocalDittoConfig();
    }

    public String getDittoCredentialSummary() {
        DittoCredentialProfile selected = dittoCredentialStore
                .getSelectedProfile();
        int savedCount = dittoCredentialStore.getProfiles().size();
        if (selected != null) {
            String summary = "Selected Ditto profile: " + selected.getLabel()
                    + "\nSaved profiles: " + savedCount;
            if (selected.isHttpProfile())
                summary += "\nHTTP profile saved; SDK profile required for live offline mesh sync";
            return summary;
        }
        if (dittoSyncManager.canCreateOperationFromBuildConfig())
            return "Using developer local.properties Ditto profile"
                    + "\nSaved profiles: " + savedCount;
        return "No Ditto profile saved\nAdd a profile before creating an operation";
    }

    public String getOperationReadinessSummary() {
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        refreshLocationAvailability();

        boolean identityReady = identity != null && identity.isResolved();
        boolean gpsReady = healthManager.isLocationActive();
        boolean storageReady = healthManager.isStorageReady();
        boolean operationReady = hasActiveOperation();
        boolean dittoProfileReady = operationReady
                ? activeOperationProfile.hasDittoCredentials()
                : canCreateOperationFromLocalDittoConfig();
        boolean dittoRunning = dittoSyncManager.isConfigured()
                && dittoSyncManager.isStarted();
        boolean teamReady = assignmentManager.isTeamCreated();
        boolean trackReady = healthManager.isTrackingActive()
                && trackManager.getActiveSessionId() != null
                && trackManager.getActiveSessionId().length() > 0;
        int dittoState = getDittoReadinessState(dittoProfileReady,
                operationReady);

        StringBuilder builder = new StringBuilder();
        appendReadinessLine(builder,
                identityReady ? READINESS_READY : READINESS_WAITING,
                "ATAK identity",
                identityReady ? identity.getCallsign()
                        : "No ATAK identity resolved");
        appendReadinessLine(builder,
                gpsReady ? READINESS_READY : READINESS_BLOCKED, "GPS signal",
                healthManager.getLocationMessage());
        appendReadinessLine(builder,
                storageReady ? READINESS_READY : READINESS_BLOCKED,
                "Local storage",
                storageReady ? "Ready" : "Storage unavailable");
        appendReadinessLine(builder,
                dittoProfileReady ? READINESS_READY : READINESS_WAITING,
                "Ditto profile", getDittoProfileReadinessDetail(
                        dittoProfileReady));
        appendReadinessLine(builder,
                operationReady ? READINESS_READY : READINESS_WAITING,
                "Operation",
                operationReady ? activeOperationProfile.getOperationName()
                        : "Create or join an operation");
        appendReadinessLine(builder, dittoState, "Ditto sync",
                dittoSyncManager.getSummary());
        appendReadinessLine(builder,
                teamReady ? READINESS_READY : READINESS_WAITING, "SAR team",
                teamReady ? assignmentManager.getTeamName()
                        : "Create or join a SARtak team");
        appendReadinessLine(builder,
                trackReady ? READINESS_READY : READINESS_WAITING,
                "Track recording",
                trackReady ? trackManager.getStatusSummary()
                        : "Waiting for active operation track");
        return builder.toString();
    }

    public int getOperationReadinessLevel() {
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        refreshLocationAvailability();

        boolean identityReady = identity != null && identity.isResolved();
        boolean gpsReady = healthManager.isLocationActive();
        boolean storageReady = healthManager.isStorageReady();
        boolean operationReady = hasActiveOperation();
        boolean dittoProfileReady = operationReady
                ? activeOperationProfile.hasDittoCredentials()
                : canCreateOperationFromLocalDittoConfig();
        boolean teamReady = assignmentManager.isTeamCreated();
        boolean trackReady = healthManager.isTrackingActive()
                && trackManager.getActiveSessionId() != null
                && trackManager.getActiveSessionId().length() > 0;

        int dittoState = getDittoReadinessState(dittoProfileReady,
                operationReady);
        if (!gpsReady || !storageReady
                || dittoState == READINESS_BLOCKED)
            return READINESS_BLOCKED;
        if (!identityReady || !operationReady || !dittoProfileReady
                || dittoState == READINESS_WAITING || !teamReady
                || !trackReady)
            return READINESS_WAITING;
        return READINESS_READY;
    }

    public boolean isOperationReady() {
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        refreshLocationAvailability();
        return identity != null && identity.isResolved()
                && healthManager.isLocationActive()
                && healthManager.isStorageReady()
                && hasActiveOperation()
                && dittoSyncManager.isConfigured()
                && dittoSyncManager.isStarted()
                && assignmentManager.isTeamCreated()
                && healthManager.isTrackingActive();
    }

    public List<DittoCredentialProfile> getDittoCredentialProfiles() {
        return dittoCredentialStore.getProfiles();
    }

    public DittoCredentialProfile getSelectedDittoCredentialProfile() {
        return dittoCredentialStore.getSelectedProfile();
    }

    public void selectDittoCredentialProfile(String profileId) {
        dittoCredentialStore.selectProfile(profileId);
    }

    public boolean saveDittoCredentialProfile(String existingProfileId,
            String label, String databaseId, String authUrl,
            String developmentToken) {
        return saveDittoCredentialProfile(existingProfileId, label,
                DittoCredentialProfile.CONNECTION_SDK, databaseId, authUrl,
                developmentToken);
    }

    public boolean saveDittoCredentialProfile(String existingProfileId,
            String label, String connectionType, String databaseId,
            String authUrl, String developmentToken) {
        DittoCredentialProfile existing = findDittoCredentialProfile(
                existingProfileId);
        DittoCredentialProfile profile = existing == null
                ? DittoCredentialProfile.create(label, connectionType,
                        databaseId, authUrl, developmentToken)
                : existing.updated(label, connectionType, databaseId, authUrl,
                        developmentToken);
        if (!profile.isComplete())
            return false;
        dittoCredentialStore.saveProfile(profile);
        return true;
    }

    public boolean removeSelectedDittoCredentialProfile() {
        DittoCredentialProfile selected = dittoCredentialStore
                .getSelectedProfile();
        if (selected == null)
            return false;
        dittoCredentialStore.removeProfile(selected.getId());
        return true;
    }

    public String getOperationSummary() {
        return activeOperationProfile == null
                ? "No active operation selected"
                : activeOperationProfile.getSummary();
    }

    public String getOperationJoinCode() {
        if (activeOperationProfile == null)
            return "";
        try {
            return activeOperationProfile.toJoinCode();
        } catch (Exception exception) {
            return "";
        }
    }

    public boolean createOperation(String operationName) {
        if (!isLeaderRole())
            return false;
        DittoCredentialProfile credentials = getOperationCredentialProfile();
        if (credentials == null)
            return false;
        IdentityManager.Identity identity = identityManager.resolveIdentity();
        OperationProfile profile = OperationProfile.create(operationName,
                identity, credentials.getDatabaseId(), credentials.getAuthUrl(),
                credentials.getDevelopmentToken());
        activateOperation(profile, true);
        return true;
    }

    public boolean joinOperationFromCode(String joinCode) {
        try {
            OperationProfile profile = OperationProfile.fromJoinCode(joinCode);
            saveCredentialsFromOperationProfile(profile);
            activateOperation(profile, true);
            return true;
        } catch (Exception exception) {
            Log.w(TAG, "Operation join code rejected", exception);
            return false;
        }
    }

    public void leaveOperation() {
        clearLocalTeam(true);
        activeOperationProfile = null;
        operationStateStore.clear();
        scopeLocalStoresToActiveOperation();
        applyOperationIdToWorkflows();
        dittoSyncManager.useOperationProfile(null);
        refreshOverlay();
    }

    public void createTeam(String teamName) {
        if (!hasActiveOperation())
            return;
        inactiveMembershipTimes.clear();
        assignmentManager.createTeam(teamName, getFixedLeaderTeamId());
        localTeamJoinTime = System.currentTimeMillis();
        teamStateStore.save(assignmentManager);
        publishSelfMembership(DittoTeamMembershipSnapshot.STATUS_ACTIVE);
        teamCotWorkflow.advertiseTeam(assignmentManager.getTeamId(),
                assignmentManager.getTeamName());
        publishTeamPresence();
        publishDittoDeviceStateNow();
        refreshOverlay();
    }

    public void removeTeam() {
        if (assignmentManager.isTeamCreated()) {
            teamCotWorkflow.removeTeam(assignmentManager.getTeamId(),
                    assignmentManager.getTeamName());
            publishSelfMembership(DittoTeamMembershipSnapshot.STATUS_REMOVED);
        }
        clearLocalTeam(false);
    }

    public void resetLocalSyncState() {
        assignmentManager.clearTeam();
        teamStateStore.clear();
        rosterJoinTimes.clear();
        inactiveMembershipTimes.clear();
        teamCotWorkflow.clearLocalState();
        gridCotWorkflow.clearLocalMessages();
        searchLineCotWorkflow.clearLocalMessages();
        dittoSyncManager.clearLocalCaches();
        dittoAtakContactBridge.clear();
        atakContactSummary = "Local SARtak sync state reset.";
        publishDittoDeviceStateNow();
        refreshTeamContactsInternal();
        refreshOverlay();
    }

    public void leaveTeam() {
        clearLocalTeam(true);
    }

    private void clearLocalTeam(boolean notifyLeader) {
        if (notifyLeader && assignmentManager.isTeamCreated()
                && !isLeaderRole())
            publishSelfMembership(DittoTeamMembershipSnapshot.STATUS_LEFT);
        if (notifyLeader && assignmentManager.isTeamCreated() && !isLeaderRole())
            teamCotWorkflow.memberLeft(assignmentManager.getTeamId(),
                    assignmentManager.getTeamName(),
                    assignmentManager.getLeaderUid(),
                    assignmentManager.getLeaderCallsign());
        if (assignmentManager.isTeamCreated())
            teamCotWorkflow.publishPresence("", "", "", "",
                    "", 0, "", 0, "");
        assignmentManager.clearTeam();
        teamStateStore.clear();
        publishDittoDeviceStateNow();
        refreshTeamContactsInternal();
        refreshOverlay();
    }

    public void requestJoinTeam(SearchTeamCotMessage team) {
        if (!hasActiveOperation())
            return;
        teamCotWorkflow.requestJoin(team);
    }

    public void cancelJoinRequest(SearchTeamCotMessage request) {
        teamCotWorkflow.cancelJoinRequest(request);
    }

    public void inviteTeamMember(String uniqueId) {
        if (!hasActiveOperation() || !assignmentManager.isTeamCreated())
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
            forgetInactiveMembership(request.getTeamId(),
                    request.getSenderUid(), request.getSenderCallsign(),
                    System.currentTimeMillis());
            AtakTeamContactDataSource.ContactSnapshot contact = findContact(
                    request.getSenderUid(), request.getSenderCallsign());
            assignmentManager.unblockRosterMember(request.getSenderUid(),
                    request.getSenderCallsign());
            if (contact != null) {
                assignmentManager.addTeamMember(contact,
                        getAvailableSelfPoint(), converter);
            } else {
                assignmentManager.addConfirmedRosterMember(
                        request.getSenderUid(), request.getSenderCallsign(),
                        SearchTeamMember.TeamRole.SEARCHER);
            }
            rememberRosterJoin(request.getSenderUid(),
                    request.getSenderCallsign());
            publishMembership(request.getSenderUid(),
                    request.getSenderCallsign(),
                    DittoTeamMembershipSnapshot.STATUS_ACTIVE,
                    "Searcher");
            teamStateStore.save(assignmentManager);
            publishTeamPresence();
            publishDittoDeviceStateNow();
            refreshOverlay();
        }
    }

    public void acceptJoinResponse(SearchTeamCotMessage response) {
        forgetInactiveMembership(response.getTeamId(),
                assignmentManager.getSelfMemberId(),
                response.getTargetCallsign(), response.getCreated());
        assignmentManager.joinTeam(response.getTeamName(),
                response.getTeamId());
        localTeamJoinTime = System.currentTimeMillis();
        addLeaderFromContact(response.getLeaderUid(),
                response.getLeaderCallsign());
        teamStateStore.save(assignmentManager);
        publishSelfMembership(DittoTeamMembershipSnapshot.STATUS_ACTIVE);
        publishTeamPresence();
        publishDittoDeviceStateNow();
        refreshOverlay();
    }

    public void respondToInvite(SearchTeamCotMessage invite,
            boolean accepted) {
        teamCotWorkflow.respondToInvite(invite, accepted);
        if (accepted) {
            forgetInactiveMembership(invite.getTeamId(),
                    assignmentManager.getSelfMemberId(),
                    invite.getTargetCallsign(), System.currentTimeMillis());
            assignmentManager.joinTeam(invite.getTeamName(),
                    invite.getTeamId());
            localTeamJoinTime = System.currentTimeMillis();
            addLeaderFromContact(invite.getLeaderUid(),
                    invite.getLeaderCallsign());
            teamStateStore.save(assignmentManager);
            publishSelfMembership(DittoTeamMembershipSnapshot.STATUS_ACTIVE);
            publishTeamPresence();
            publishDittoDeviceStateNow();
            refreshOverlay();
        }
    }

    public boolean acceptInviteResponse(SearchTeamCotMessage response) {
        if (!SearchTeamCotMessage.ACTION_INVITE_ACCEPT.equals(
                response.getAction()))
            return false;
        assignmentManager.unblockRosterMember(response.getSenderUid(),
                response.getSenderCallsign());
        forgetInactiveMembership(response.getTeamId(), response.getSenderUid(),
                response.getSenderCallsign(), response.getCreated());
        boolean added = addTeamMemberFromResponse(response);
        if (added) {
            rememberRosterJoin(response.getSenderUid(),
                    response.getSenderCallsign());
            publishMembership(response.getSenderUid(),
                    response.getSenderCallsign(),
                    DittoTeamMembershipSnapshot.STATUS_ACTIVE,
                    "Searcher");
            teamStateStore.save(assignmentManager);
        }
        if (added) {
            publishTeamPresence();
            publishDittoDeviceStateNow();
        }
        return added;
    }

    public List<SearchTeamCotMessage> getActiveTeamAdvertisements() {
        if (!hasActiveOperation())
            return java.util.Collections.emptyList();
        // Only show teams that have explicitly advertised over the SARtak CoT
        // workflow. A visible ATAK contact is not enough to prove that a SARtak
        // team exists, and inventing one makes member devices join fake teams.
        java.util.ArrayList<SearchTeamCotMessage> teams =
                new java.util.ArrayList<>(teamCotWorkflow
                        .getTeamAdvertisements());
        teams.addAll(getTeamAdvertisementsFromDittoDevices());
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

    public List<SearchTeamCotMessage> getMemberRemovalsForMe() {
        return teamCotWorkflow.getMemberRemovalsForMe();
    }

    public List<SearchTeamCotMessage> getMemberLeavesForLeader() {
        if (!assignmentManager.isTeamCreated())
            return java.util.Collections.emptyList();
        return teamCotWorkflow.getMemberLeavesForMe();
    }

    public List<SearchTeamCotMessage> getInvitesForMe() {
        if (assignmentManager.isTeamCreated())
            return java.util.Collections.emptyList();
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
        if (assignmentManager.isTeamCreated())
            return java.util.Collections.emptyList();
        return teamCotWorkflow.getOutgoingJoinRequests();
    }

    public int getVisibleTeamAdvertisementCount() {
        return getActiveTeamAdvertisements().size();
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
        for (DittoDeviceSnapshot snapshot
                : dittoSyncManager.getDeviceSnapshots()) {
            if (snapshot.getUid() == null || snapshot.getUid().equals(selfUid))
                continue;
            if (assignmentManager.findMemberById(snapshot.getUid()) != null)
                continue;
            if (containsContact(available, snapshot.getUid(),
                    snapshot.getCallsign()))
                continue;
            available.add(AtakTeamContactDataSource.ContactSnapshot
                    .fromDitto(snapshot));
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
        SearchTeamMember member = assignmentManager.findMemberById(uniqueId);
        long removedAt = System.currentTimeMillis();
        boolean removed = assignmentManager.removeTeamMember(uniqueId);
        if (removed && member != null) {
            rememberInactiveMembership(assignmentManager.getTeamId(),
                    member.getUniqueId(), member.getCallsign(), removedAt);
            teamCotWorkflow.removeMember(assignmentManager.getTeamId(),
                    assignmentManager.getTeamName(), member.getUniqueId(),
                    member.getCallsign());
        }
        if (removed && member != null)
            publishMembership(member.getUniqueId(), member.getCallsign(),
                    DittoTeamMembershipSnapshot.STATUS_REMOVED,
                    member.getRoleLabel());
        arrangeTeamMembers();
        refreshOverlay();
        if (removed)
            teamStateStore.save(assignmentManager);
        if (removed) {
            publishTeamPresence();
            publishDittoDeviceStateNow();
        }
        return removed;
    }

    public boolean applyMemberRemoval(SearchTeamCotMessage removal) {
        if (removal == null || !assignmentManager.isTeamCreated())
            return false;
        if (!assignmentManager.getTeamId().equals(removal.getTeamId()))
            return false;
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        String selfUid = identity == null ? assignmentManager.getSelfMemberId()
                : identity.getUid();
        String selfCallsign = identity == null ? "" : identity.getCallsign();
        if (!matchesMember(selfUid, selfCallsign, removal.getTargetUid(),
                removal.getTargetCallsign()))
            return false;
        if (localTeamJoinTime > 0L
                && removal.getCreated() < localTeamJoinTime)
            return false;
        rememberInactiveMembership(removal.getTeamId(), selfUid,
                selfCallsign, removal.getCreated());
        clearLocalTeam(false);
        return true;
    }

    public String applyMemberLeft(SearchTeamCotMessage message) {
        if (message == null || !assignmentManager.isTeamCreated())
            return "";
        if (!assignmentManager.getTeamId().equals(message.getTeamId()))
            return "";
        Long joinedAt = getRosterJoinTime(message.getSenderUid(),
                message.getSenderCallsign());
        if (joinedAt != null && message.getCreated() < joinedAt)
            return "";
        SearchTeamMember member = assignmentManager.findMemberById(
                message.getSenderUid());
        String callsign = member == null ? message.getSenderCallsign()
                : member.getCallsign();
        rememberInactiveMembership(message.getTeamId(), message.getSenderUid(),
                message.getSenderCallsign(), message.getCreated());
        boolean removed = assignmentManager.removeTeamMemberByIdentity(
                message.getSenderUid(), message.getSenderCallsign());
        if (!removed)
            return "";
        arrangeTeamMembers();
        teamStateStore.save(assignmentManager);
        publishTeamPresence();
        refreshOverlay();
        return callsign;
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
        if (!hasActiveOperation())
            return;
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

    public List<DeviceConnectivitySnapshot> getVisibleDevices() {
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        String selfUid = identity == null ? "" : identity.getUid();
        String selfCallsign = identity == null ? "" : identity.getCallsign();
        Map<String, DeviceBuilder> devices = new LinkedHashMap<>();

        for (AtakTeamContactDataSource.ContactSnapshot contact
                : teamContactDataSource.getContacts()) {
            String key = deviceKey(contact.getUid(), contact.getCallsign());
            DeviceBuilder builder = devices.get(key);
            if (builder == null) {
                builder = new DeviceBuilder(contact.getUid(),
                        contact.getCallsign());
                devices.put(key, builder);
            }
            builder.atak = true;
            builder.atakGroupName = contact.getAtakGroupName();
            builder.role = contact.getRole();
            builder.lastAtakUpdate = contact.getTimestamp();
        }

        for (DittoDeviceSnapshot snapshot
                : dittoSyncManager.getDeviceSnapshots()) {
            String key = deviceKey(snapshot.getUid(), snapshot.getCallsign());
            DeviceBuilder builder = devices.get(key);
            if (builder == null) {
                builder = new DeviceBuilder(snapshot.getUid(),
                        snapshot.getCallsign());
                devices.put(key, builder);
            }
            builder.ditto = true;
            builder.teamName = snapshot.getTeamName();
            builder.teamId = snapshot.getTeamId();
            if (builder.role.length() == 0)
                builder.role = AtakTeamContactDataSource.ContactSnapshot
                        .fromDitto(snapshot).getRole();
            builder.lastDittoUpdate = snapshot.getUpdatedAt();
            if (builder.atakGroupName.length() == 0)
                builder.atakGroupName = AtakTeamContactDataSource
                        .ContactSnapshot.fromDitto(snapshot)
                        .getAtakGroupName();
        }

        List<DeviceConnectivitySnapshot> snapshots = new ArrayList<>();
        for (DeviceBuilder builder : devices.values()) {
            boolean isSelf = matchesMember(selfUid, selfCallsign,
                    builder.uid, builder.callsign);
            snapshots.add(builder.build(isSelf));
        }
        java.util.Collections.sort(snapshots,
                new java.util.Comparator<DeviceConnectivitySnapshot>() {
                    @Override
                    public int compare(DeviceConnectivitySnapshot first,
                            DeviceConnectivitySnapshot second) {
                        if (first.isSelf() != second.isSelf())
                            return first.isSelf() ? -1 : 1;
                        return first.getCallsign().compareToIgnoreCase(
                                second.getCallsign());
                    }
                });
        return snapshots;
    }

    public String getDeviceDiagnosticsSummary(int visibleCount,
            int atakCount, int dittoCount, int staleCount) {
        return visibleCount + " device(s) visible | " + atakCount
                + " via ATAK/CoT | " + dittoCount + " via Ditto | "
                + staleCount + " stale"
                + "\nNetwork: " + getNetworkConnectivitySummary()
                + "\n" + dittoSyncManager.getDiagnosticsSummary();
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
        syncDittoDevicesToAtakMarkers();
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
        List<SearchGridCell> cells = gridManager.getRenderCells();
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
                cells.get(0)) + "\nPlanned area: " + complete + " complete, "
                + partial + " partial, " + inProgress + " in progress, "
                + cells.size() + " cells total\n"
                + getNextCellDisplaySummary();
    }

    public SearchTeamMember selectTeamMember(String uniqueId) {
        SearchTeamMember member = assignmentManager.findMemberById(uniqueId);
        if (member == null)
            return null;

        teamMarkerOverlay.setSelectedMemberId(uniqueId);
        // Selecting a member whose position ATAK has not reported is fine --
        // their card still opens. Flying the map to their placeholder
        // coordinates is not: it tells the operator we know where they are.
        if (MemberPositionPolicy.hasUsablePosition(member))
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
        rawGnssCaptureManager.stop();
        healthManager.stop();
        dittoSyncManager.stop();
        dittoAtakContactBridge.clear();
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
        dittoSyncManager.maintainConnection();
        advertiseTeamIfDue();
        applyTeamLifecycleMessages();
        applyDittoMembershipSnapshots();
        syncDittoDevicesToAtakMarkers();
        sharedMapMarkerSyncManager.sync(assignmentManager.getTeamId());
        if (assignmentManager.isTeamCreated())
            refreshTeamContactsInternal();
        applyRemoteSearchLineIfAvailable();
        applyRemoteGridStatusMessages();
        applyAlertSearchLineSideEffects();
        refreshLocationAvailability();
        syncSelfTeamMemberFromAtak();
        updateCurrentGridCellFromLocation();
        publishDittoDeviceStateIfDue();
        applyDittoDeviceSnapshots();
        refreshOverlay();
    }

    private void updateCurrentGridCellFromLocation() {
        GeoPoint point = getCurrentUserPoint();
        if (point == null)
            return;
        SearchGridCell before = gridManager.getSelectedCell();
        String previousId = before == null ? "" : before.getId();
        SearchGridCell current = gridManager.updateCurrentCellAt(point,
                isLeaderRole() && assignmentManager.isTeamCreated());
        if (current == null || previousId.equals(current.getId()))
            return;

        if (isLeaderRole() && assignmentManager.isTeamCreated()) {
            SearchGridCell pending = gridManager.getPendingReviewCell();
            if (pending != null)
                publishGridStatusForCell(pending.getId(), pending.getStatus());
            publishGridStatusForCell(current.getId(), current.getStatus());
        }
    }

    private void applyAlertSearchLineSideEffects() {
        for (SearchAlertMessage alert : getActiveTeamAlerts()) {
            if (alert.requiresHalt()) {
                pauseSearchLineForAlert();
                return;
            }
        }
    }

    private void pauseSearchLineForAlert() {
        if (!searchLineManager.isStarted() || searchLineManager.isPaused())
            return;
        searchLineManager.pause();
        if (isLeaderRole() && assignmentManager.isTeamCreated())
            publishSearchLine(SearchLineCotMessage.ACTION_PAUSE);
        refreshOverlay();
    }

    private List<SearchAlertMessage> getActiveAlerts(boolean onlyMine) {
        List<SearchAlertMessage> active = new ArrayList<>();
        if (!assignmentManager.isTeamCreated())
            return active;
        String teamId = assignmentManager.getTeamId();
        String selfUid = getSelfMemberId();
        java.util.LinkedHashMap<String, SearchAlertMessage> byAlertId =
                new java.util.LinkedHashMap<>();
        for (SearchAlertMessage message : dittoSyncManager.getAlertMessages()) {
            if (!getActiveOperationId().equals(message.getOperationId()))
                continue;
            if (!teamId.equals(message.getTeamId()))
                continue;
            if (!SearchAlertMessage.ACTION_ACTIVE.equals(message.getAction()))
                continue;
            if (onlyMine && !selfUid.equals(message.getSenderUid()))
                continue;
            SearchAlertMessage existing = byAlertId.get(message.getAlertId());
            if (existing == null || message.getCreated()
                    > existing.getCreated())
                byAlertId.put(message.getAlertId(), message);
        }
        for (SearchAlertMessage alert : byAlertId.values()) {
            if (!isAlertCancelled(alert))
                active.add(alert);
        }
        return active;
    }

    private boolean isAlertCancelled(SearchAlertMessage alert) {
        for (SearchAlertMessage message : dittoSyncManager.getAlertMessages()) {
            if (message.getAlertId().equals(alert.getAlertId())
                    && SearchAlertMessage.ACTION_CANCEL.equals(message
                            .getAction())
                    && message.getCreated() >= alert.getCreated())
                return true;
        }
        return false;
    }

    private boolean hasAckFrom(SearchAlertMessage alert, String uid) {
        if (uid == null || uid.length() == 0)
            return false;
        for (SearchAlertMessage message : dittoSyncManager.getAlertMessages()) {
            if (message.getAlertId().equals(alert.getAlertId())
                    && SearchAlertMessage.ACTION_ACK.equals(message
                            .getAction())
                    && message.getCreated() >= alert.getCreated()
                    && uid.equals(message.getAckUid()))
                return true;
        }
        return false;
    }

    private void publishSelectedGridStatus(SearchGridStatus status) {
        SearchGridCell cell = gridManager.getSelectedCell();
        if (cell == null)
            return;
        publishGridStatusForCell(cell.getId(), status);
    }

    private void publishGridStatusForCell(String cellId,
            SearchGridStatus status) {
        if (!isLeaderRole() || !assignmentManager.isTeamCreated()
                || cellId == null || cellId.length() == 0 || status == null)
            return;
        gridCotWorkflow.publishStatus(assignmentManager.getTeamId(),
                cellId, status);
    }

    private void applyRemoteGridStatusMessages() {
        if (!assignmentManager.isTeamCreated())
            return;
        List<SearchGridCotMessage> messages = isLeaderRole()
                ? gridCotWorkflow.consumeMessagesForOperation()
                : gridCotWorkflow.consumeMessagesForTeam(
                        assignmentManager.getTeamId());
        for (SearchGridCotMessage message : messages)
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

    /**
     * Brings the runtime up, but only as far as storage allows.
     *
     * <p>The storage probe gates everything after it rather than only setting a
     * flag. Every step below writes to SQLite -- {@code resolveIdentity} stores
     * the self row, {@code startOrResume} opens a track session, and the capture
     * loop resolves identity again every cycle -- so running them against a
     * database that did not open would throw on ATAK's thread instead of
     * reporting INACTIVE. Reporting the failure is the whole point of the
     * check; crashing past it would report nothing.
     */
    private void initialiseRuntime() {
        healthManager.start();
        StorageAvailability.Result storage =
                StorageAvailability.probe(databaseHelper);
        healthManager.setStorageReady(storage.isReady(), storage.getMessage());
        if (!storage.isReady()) {
            healthManager.setTrackingActive(false);
            healthManager.reportNotCapturing(
                    "Not capturing: local storage unavailable");
            return;
        }
        IdentityManager.Identity identity = identityManager.resolveIdentity();
        healthManager.setIdentityResolved(identity.isResolved(),
                identity.getMessage());
        if (identity.isResolved()) {
            assignmentManager.setSelfIdentity(identity.getUid(),
                    identity.getCallsign());
            if (!hasActiveOperation() && assignmentManager.isTeamCreated()) {
                assignmentManager.clearTeam();
                teamStateStore.clear();
            }
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
        rawGnssCaptureManager.start();
    }

    private void activateOperation(OperationProfile profile,
            boolean clearExistingTeam) {
        if (clearExistingTeam && assignmentManager.isTeamCreated())
            clearLocalTeam(true);
        activeOperationProfile = profile;
        inactiveMembershipTimes.clear();
        localTeamJoinTime = 0L;
        operationStateStore.save(profile);
        scopeLocalStoresToActiveOperation();
        applyOperationIdToWorkflows();
        dittoSyncManager.useOperationProfile(profile);
        rosterJoinTimes.clear();
        teamStateStore.load(assignmentManager);
        publishDittoDeviceStateNow();
        syncDittoDevicesToAtakMarkers();
        sharedMapMarkerSyncManager.sync(assignmentManager.getTeamId());
        refreshTeamContactsInternal();
        refreshOverlay();
    }

    private String getActiveOperationId() {
        return activeOperationProfile == null ? ""
                : activeOperationProfile.getOperationId();
    }

    private void scopeLocalStoresToActiveOperation() {
        String operationId = getActiveOperationId();
        gridStateStore.setOperationId(operationId);
        teamStateStore.setOperationId(operationId);
        trackManager.setOperationId(operationId);
        gridManager.refreshSelectedCellStatus();
    }

    private void applyOperationIdToWorkflows() {
        String operationId = getActiveOperationId();
        teamCotWorkflow.setOperationId(operationId);
        gridCotWorkflow.setOperationId(operationId);
        searchLineCotWorkflow.setOperationId(operationId);
        sharedMapMarkerSyncManager.setOperationId(operationId);
    }

    private void appendReadinessLine(StringBuilder builder, int state,
            String label, String detail) {
        if (builder.length() > 0)
            builder.append('\n');
        if (state == READINESS_READY)
            builder.append("[OK] ");
        else if (state == READINESS_BLOCKED)
            builder.append("[NO] ");
        else
            builder.append("[WAIT] ");
        builder.append(label);
        if (detail != null && detail.trim().length() > 0)
            builder.append(": ").append(detail.trim());
    }

    private int getDittoReadinessState(boolean profileReady,
            boolean operationReady) {
        if (!profileReady || !operationReady)
            return READINESS_WAITING;
        String summary = dittoSyncManager.getSummary().toLowerCase(
                java.util.Locale.US);
        if (summary.contains("unavailable")
                || summary.contains("not configured"))
            return READINESS_BLOCKED;
        if (!dittoSyncManager.isConfigured())
            return READINESS_BLOCKED;
        if (!dittoSyncManager.isStarted()
                || summary.contains("failed")
                || summary.contains("waiting")
                || summary.contains("stopped")
                || summary.contains("(inactive)")
                || summary.contains("(unknown)"))
            return READINESS_WAITING;
        return READINESS_READY;
    }

    private String getDittoProfileReadinessDetail(boolean profileReady) {
        if (profileReady)
            return "SDK credentials available";
        DittoCredentialProfile selected = dittoCredentialStore
                .getSelectedProfile();
        if (selected != null && selected.isHttpProfile())
            return "HTTP profile saved; SDK profile required for live offline mesh sync";
        return "Add or scan an operation profile";
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
        if (snapshot.isAvailable()) {
            Marker self = mapView.getSelfMarker();
            double speed = self == null ? 0.0
                    : sanitizeSpeed(self.getTrackSpeed());
            double heading = self == null ? 0.0 : self.getTrackHeading();
            assignmentManager.updateSelfMovement(heading,
                    speed > MIN_HEADING_SPEED_METERS_PER_SECOND, speed);
        } else {
            healthManager.recordLocationFailure(snapshot.getMessage());
        }
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
                filterActivePresence(teamCotWorkflow.getPresenceForTeam(
                        assignmentManager.getTeamId())), contacts,
                getAvailableSelfPoint(),
                converter);
        boolean dittoChanged = assignmentManager.updateFromDittoDevices(
                filterActiveDittoDevices(dittoSyncManager.getDeviceSnapshots()),
                getAvailableSelfPoint(),
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

    private void publishDittoDeviceStateNow() {
        SearchTeamMember self = assignmentManager.getSelfMember();
        dittoSyncManager.publishDeviceStateNow(
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
        syncDittoDevicesToAtakMarkers();
        if (!assignmentManager.isTeamCreated())
            return;
        boolean changed = assignmentManager.updateFromDittoDevices(
                filterActiveDittoDevices(dittoSyncManager.getDeviceSnapshots()),
                getAvailableSelfPoint(),
                converter);
        if (changed)
            teamStateStore.save(assignmentManager);
    }

    private void syncDittoDevicesToAtakMarkers() {
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        String selfUid = identity == null ? assignmentManager.getSelfMemberId()
                : identity.getUid();
        dittoAtakContactBridge.sync(filterActiveDittoDevices(
                dittoSyncManager.getDeviceSnapshots()), selfUid,
                getActiveOperationId(),
                assignmentManager.isTeamCreated()
                        ? assignmentManager.getTeamId() : "",
                isLeaderRole(), teamMarkerOverlay.getVisibilityMode(),
                teamMarkerOverlay.isShowingCallsigns());
    }

    private List<SearchTeamCotMessage> filterActivePresence(
            List<SearchTeamCotMessage> presenceMessages) {
        List<SearchTeamCotMessage> filtered = new ArrayList<>();
        if (presenceMessages == null)
            return filtered;
        for (SearchTeamCotMessage presence : presenceMessages) {
            if (!hasNewerInactiveMembership(presence.getTeamId(),
                    presence.getSenderUid(), presence.getSenderCallsign(),
                    presence.getCreated()))
                filtered.add(presence);
        }
        return filtered;
    }

    private List<DittoDeviceSnapshot> filterActiveDittoDevices(
            List<DittoDeviceSnapshot> snapshots) {
        List<DittoDeviceSnapshot> filtered = new ArrayList<>();
        if (snapshots == null)
            return filtered;
        for (DittoDeviceSnapshot snapshot : snapshots) {
            if (!hasNewerInactiveMembership(snapshot.getTeamId(),
                    snapshot.getUid(), snapshot.getCallsign(),
                    snapshot.getUpdatedAt()))
                filtered.add(snapshot);
        }
        return filtered;
    }

    private void applyTeamLifecycleMessages() {
        for (SearchTeamCotMessage removal : getMemberRemovalsForMe()) {
            if (rememberAppliedMessage(removal))
                applyMemberRemoval(removal);
        }

        if (isLeaderRole() && assignmentManager.isTeamCreated()) {
            for (SearchTeamCotMessage left : getMemberLeavesForLeader()) {
                if (rememberAppliedMessage(left))
                    applyMemberLeft(left);
            }
            for (SearchTeamCotMessage response
                    : getInviteResponsesForLeader()) {
                if (!rememberAppliedMessage(response))
                    continue;
                if (SearchTeamCotMessage.ACTION_INVITE_ACCEPT.equals(
                        response.getAction()))
                    acceptInviteResponse(response);
            }
            return;
        }

        if (!assignmentManager.isTeamCreated()) {
            for (SearchTeamCotMessage response : getJoinResponsesForMe()) {
                if (!rememberAppliedMessage(response))
                    continue;
                if (SearchTeamCotMessage.ACTION_JOIN_ACCEPT.equals(
                        response.getAction()))
                    acceptJoinResponse(response);
            }
        }
    }

    private boolean rememberAppliedMessage(SearchTeamCotMessage message) {
        return message != null && message.getUid() != null
                && appliedTeamMessageIds.add(message.getUid());
    }

    private String formatAge(long timestamp) {
        if (timestamp <= 0L)
            return "never";
        long seconds = Math.max(0L, (System.currentTimeMillis() - timestamp)
                / 1000L);
        return seconds <= 1L ? "now" : seconds + " sec ago";
    }

    private String deviceKey(String uid, String callsign) {
        String safeUid = uid == null ? "" : uid.trim();
        if (safeUid.length() > 0)
            return "uid:" + safeUid;
        String safeCallsign = callsign == null ? "" : callsign.trim()
                .toLowerCase(java.util.Locale.US);
        return "callsign:" + safeCallsign;
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

    private void publishSelfMembership(String status) {
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        if (identity == null || !identity.isResolved()
                || !assignmentManager.isTeamCreated())
            return;
        SearchTeamMember self = assignmentManager.getSelfMember();
        String role = self == null ? getRoleLabel() : self.getRoleLabel();
        publishMembership(identity.getUid(), identity.getCallsign(), status,
                role);
    }

    private void publishMembership(String memberUid, String memberCallsign,
            String status, String roleLabel) {
        if (!assignmentManager.isTeamCreated())
            return;
        dittoSyncManager.publishTeamMembership(assignmentManager.getTeamId(),
                assignmentManager.getTeamName(),
                assignmentManager.getLeaderUid(),
                assignmentManager.getLeaderCallsign(),
                memberUid, memberCallsign, status, roleLabel);
    }

    private void applyDittoMembershipSnapshots() {
        List<DittoTeamMembershipSnapshot> memberships =
                dittoSyncManager.getTeamMemberships();
        if (memberships.isEmpty())
            return;

        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        String selfUid = identity == null ? assignmentManager.getSelfMemberId()
                : identity.getUid();
        String selfCallsign = identity == null ? "" : identity.getCallsign();
        boolean changed = false;

        List<DittoTeamMembershipSnapshot> ordered =
                new ArrayList<>(memberships);
        java.util.Collections.sort(ordered,
                new java.util.Comparator<DittoTeamMembershipSnapshot>() {
                    @Override
                    public int compare(DittoTeamMembershipSnapshot first,
                            DittoTeamMembershipSnapshot second) {
                        return Long.compare(first.getUpdatedAt(),
                                second.getUpdatedAt());
                    }
                });

        for (DittoTeamMembershipSnapshot membership : ordered) {
            if (membership.getTeamId().length() == 0)
                continue;

            boolean appliesToSelf = matchesMember(selfUid, selfCallsign,
                    membership.getMemberUid(),
                    membership.getMemberCallsign());
            if (appliesToSelf) {
                changed = applySelfMembership(membership) || changed;
                continue;
            }

            if (!assignmentManager.isTeamCreated()
                    || !assignmentManager.getTeamId().equals(
                            membership.getTeamId()))
                continue;

            if (membership.isActive())
                changed = applyActiveMembership(membership) || changed;
            else if (membership.isLeftOrRemoved())
                changed = applyInactiveMembership(membership) || changed;
        }

        if (changed) {
            teamStateStore.save(assignmentManager);
            arrangeTeamMembers();
            refreshOverlay();
        }
    }

    private boolean applySelfMembership(
            DittoTeamMembershipSnapshot membership) {
        if (!assignmentManager.isTeamCreated()) {
            if (!membership.isActive())
                return false;
            if (hasNewerInactiveMembership(membership.getTeamId(),
                    membership.getMemberUid(), membership.getMemberCallsign(),
                    membership.getUpdatedAt()))
                return false;
            if (membership.getLeaderUid().length() == 0
                    || membership.getTeamId().length() == 0)
                return false;
            forgetInactiveMembership(membership.getTeamId(),
                    membership.getMemberUid(), membership.getMemberCallsign(),
                    membership.getUpdatedAt());
            assignmentManager.joinTeam(membership.getTeamName(),
                    membership.getTeamId());
            localTeamJoinTime = Math.max(System.currentTimeMillis(),
                    membership.getUpdatedAt());
            addLeaderFromContact(membership.getLeaderUid(),
                    membership.getLeaderCallsign());
            publishTeamPresence();
            publishDittoDeviceStateNow();
            return true;
        }

        if (!assignmentManager.getTeamId().equals(membership.getTeamId()))
            return false;
        if (membership.isLeftOrRemoved()
                && membership.getUpdatedAt() >= localTeamJoinTime) {
            rememberInactiveMembership(membership.getTeamId(),
                    membership.getMemberUid(), membership.getMemberCallsign(),
                    membership.getUpdatedAt());
            clearLocalTeam(false);
            return true;
        }
        return false;
    }

    private boolean applyActiveMembership(
            DittoTeamMembershipSnapshot membership) {
        if (hasNewerInactiveMembership(membership.getTeamId(),
                membership.getMemberUid(), membership.getMemberCallsign(),
                membership.getUpdatedAt()))
            return false;
        forgetInactiveMembership(membership.getTeamId(),
                membership.getMemberUid(), membership.getMemberCallsign(),
                membership.getUpdatedAt());
        AtakTeamContactDataSource.ContactSnapshot contact = findContact(
                membership.getMemberUid(), membership.getMemberCallsign());
        SearchTeamMember.TeamRole role = matchesMember(
                membership.getMemberUid(), membership.getMemberCallsign(),
                membership.getLeaderUid(), membership.getLeaderCallsign())
                        ? SearchTeamMember.TeamRole.TEAM_LEADER
                        : SearchTeamMember.TeamRole.SEARCHER;
        assignmentManager.unblockRosterMember(membership.getMemberUid(),
                membership.getMemberCallsign());
        SearchTeamMember member = contact == null
                ? assignmentManager.addConfirmedRosterMember(
                        membership.getMemberUid(),
                        membership.getMemberCallsign(), role)
                : assignmentManager.addTeamMember(contact,
                        getAvailableSelfPoint(), converter);
        if (member == null)
            return false;
        member.setRole(role);
        rememberRosterJoin(membership.getMemberUid(),
                membership.getMemberCallsign(), membership.getUpdatedAt());
        return true;
    }

    private boolean applyInactiveMembership(
            DittoTeamMembershipSnapshot membership) {
        Long joinedAt = getRosterJoinTime(membership.getMemberUid(),
                membership.getMemberCallsign());
        if (joinedAt != null && membership.getUpdatedAt() < joinedAt)
            return false;
        rememberInactiveMembership(membership.getTeamId(),
                membership.getMemberUid(), membership.getMemberCallsign(),
                membership.getUpdatedAt());
        return assignmentManager.removeTeamMemberByIdentity(
                membership.getMemberUid(), membership.getMemberCallsign());
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
        for (DittoDeviceSnapshot snapshot
                : dittoSyncManager.getDeviceSnapshots()) {
            if (targetUid.length() > 0 && targetUid.equals(snapshot.getUid()))
                return AtakTeamContactDataSource.ContactSnapshot
                        .fromDitto(snapshot);
            if (targetCallsign.length() > 0
                    && targetCallsign.equalsIgnoreCase(snapshot
                            .getCallsign()))
                return AtakTeamContactDataSource.ContactSnapshot
                        .fromDitto(snapshot);
        }
        return null;
    }

    private List<SearchTeamCotMessage> getTeamAdvertisementsFromDittoDevices() {
        java.util.ArrayList<SearchTeamCotMessage> teams =
                new java.util.ArrayList<>();
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        String selfUid = identity == null ? assignmentManager.getSelfMemberId()
                : identity.getUid();
        for (DittoDeviceSnapshot snapshot
                : dittoSyncManager.getDeviceSnapshots()) {
            if (!snapshot.isTeamCreated())
                continue;
            if (snapshot.getTeamId().length() == 0
                    || snapshot.getTeamName().length() == 0)
                continue;
            if (snapshot.getLeaderUid().length() == 0)
                continue;
            if (selfUid != null && selfUid.equals(snapshot.getLeaderUid()))
                continue;
            teams.add(new SearchTeamCotMessage(
                    "sartak-team-advertise-device-" + snapshot.getTeamId()
                            + "-" + snapshot.getUid(),
                    SearchTeamCotMessage.ACTION_ADVERTISE,
                    snapshot.getTeamId(), snapshot.getTeamName(),
                    snapshot.getLeaderUid(), snapshot.getLeaderCallsign(),
                    snapshot.getUid(), snapshot.getCallsign(), "", "",
                    snapshot.getUpdatedAt(), snapshot.getTeamColorName(),
                    snapshot.getTeamColorArgb(), snapshot.getMemberColorName(),
                    snapshot.getMemberColorArgb(), snapshot.getRole(),
                    snapshot.getOperationId()));
        }
        return teams;
    }

    private boolean containsContact(
            List<AtakTeamContactDataSource.ContactSnapshot> contacts,
            String uid, String callsign) {
        if (contacts == null)
            return false;
        for (AtakTeamContactDataSource.ContactSnapshot contact : contacts) {
            if (matchesMember(uid, callsign, contact.getUid(),
                    contact.getCallsign()))
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

    private void rememberRosterJoin(String uid, String callsign) {
        rememberRosterJoin(uid, callsign, System.currentTimeMillis());
    }

    private void rememberRosterJoin(String uid, String callsign, long joinedAt) {
        long now = joinedAt > 0L ? joinedAt : System.currentTimeMillis();
        String uidKey = memberKey(uid);
        if (uidKey.length() > 0)
            rosterJoinTimes.put(uidKey, now);
        String callsignKey = memberKey(callsign);
        if (callsignKey.length() > 0)
            rosterJoinTimes.put(callsignKey, now);
    }

    private void rememberInactiveMembership(String teamId, String uid,
            String callsign, long inactiveAt) {
        long timestamp = inactiveAt > 0L ? inactiveAt
                : System.currentTimeMillis();
        String uidKey = membershipKey(teamId, uid);
        if (uidKey.length() > 0)
            inactiveMembershipTimes.put(uidKey, timestamp);
        String callsignKey = membershipKey(teamId, callsign);
        if (callsignKey.length() > 0)
            inactiveMembershipTimes.put(callsignKey, timestamp);
    }

    private boolean hasNewerInactiveMembership(String teamId, String uid,
            String callsign, long activeAt) {
        long activeTimestamp = activeAt > 0L ? activeAt
                : System.currentTimeMillis();
        Long inactive = getInactiveMembershipTime(teamId, uid, callsign);
        return inactive != null && inactive >= activeTimestamp;
    }

    private void forgetInactiveMembership(String teamId, String uid,
            String callsign, long activeAt) {
        long activeTimestamp = activeAt > 0L ? activeAt
                : System.currentTimeMillis();
        removeInactiveMembershipIfOlder(teamId, uid, activeTimestamp);
        removeInactiveMembershipIfOlder(teamId, callsign, activeTimestamp);
    }

    private Long getInactiveMembershipTime(String teamId, String uid,
            String callsign) {
        Long byUid = inactiveMembershipTimes.get(membershipKey(teamId, uid));
        Long byCallsign = inactiveMembershipTimes.get(membershipKey(teamId,
                callsign));
        if (byUid == null)
            return byCallsign;
        if (byCallsign == null)
            return byUid;
        return Math.max(byUid, byCallsign);
    }

    private void removeInactiveMembershipIfOlder(String teamId, String value,
            long activeAt) {
        String key = membershipKey(teamId, value);
        if (key.length() == 0)
            return;
        Long inactive = inactiveMembershipTimes.get(key);
        if (inactive != null && inactive < activeAt)
            inactiveMembershipTimes.remove(key);
    }

    private Long getRosterJoinTime(String uid, String callsign) {
        String uidKey = memberKey(uid);
        if (uidKey.length() > 0 && rosterJoinTimes.containsKey(uidKey))
            return rosterJoinTimes.get(uidKey);
        String callsignKey = memberKey(callsign);
        if (callsignKey.length() > 0)
            return rosterJoinTimes.get(callsignKey);
        return null;
    }

    private String membershipKey(String teamId, String value) {
        String safeTeamId = memberKey(teamId);
        String safeValue = memberKey(value);
        if (safeTeamId.length() == 0 || safeValue.length() == 0)
            return "";
        return safeTeamId + "|" + safeValue;
    }

    private String memberKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(
                java.util.Locale.US);
    }

    private double sanitizeSpeed(double speedMetersPerSecond) {
        if (Double.isNaN(speedMetersPerSecond)
                || Double.isInfinite(speedMetersPerSecond)
                || speedMetersPerSecond < 0.0
                || speedMetersPerSecond
                        > MAX_REASONABLE_SEARCH_SPEED_METERS_PER_SECOND)
            return 0.0;
        return speedMetersPerSecond;
    }

    private boolean matchesMember(String firstUid, String firstCallsign,
            String secondUid, String secondCallsign) {
        String leftUid = firstUid == null ? "" : firstUid.trim();
        String rightUid = secondUid == null ? "" : secondUid.trim();
        if (leftUid.length() > 0 && leftUid.equals(rightUid))
            return true;
        String leftCallsign = firstCallsign == null ? ""
                : firstCallsign.trim();
        String rightCallsign = secondCallsign == null ? ""
                : secondCallsign.trim();
        return leftCallsign.length() > 0
                && leftCallsign.equalsIgnoreCase(rightCallsign);
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

    private DittoCredentialProfile getOperationCredentialProfile() {
        DittoCredentialProfile selected = dittoCredentialStore
                .getSelectedProfile();
        if (selected != null && selected.isSdkProfile()
                && selected.isComplete())
            return selected;
        if (dittoSyncManager.canCreateOperationFromBuildConfig()) {
            return new DittoCredentialProfile("build-config",
                    "Developer local.properties", BuildConfig.DITTO_APP_ID,
                    BuildConfig.DITTO_AUTH_URL,
                    BuildConfig.DITTO_PLAYGROUND_TOKEN, 0L, 0L);
        }
        return null;
    }

    private DittoCredentialProfile findDittoCredentialProfile(
            String profileId) {
        if (profileId == null || profileId.trim().length() == 0)
            return null;
        for (DittoCredentialProfile profile : dittoCredentialStore
                .getProfiles()) {
            if (profile.getId().equals(profileId))
                return profile;
        }
        return null;
    }

    private void saveCredentialsFromOperationProfile(OperationProfile profile) {
        if (profile == null || !profile.hasDittoCredentials())
            return;
        DittoCredentialProfile existing =
                findDittoCredentialProfileByConnection(profile
                        .getDittoDatabaseId(), profile.getDittoAuthUrl());
        String label = profile.getOperationName().length() == 0
                ? "Imported Operation Profile"
                : profile.getOperationName();
        DittoCredentialProfile credentialProfile = existing == null
                ? DittoCredentialProfile.create(label,
                        DittoCredentialProfile.CONNECTION_SDK,
                        profile.getDittoDatabaseId(),
                        profile.getDittoAuthUrl(),
                        profile.getDittoDevelopmentToken())
                : existing.updated(label, DittoCredentialProfile.CONNECTION_SDK,
                        profile.getDittoDatabaseId(), profile.getDittoAuthUrl(),
                        profile.getDittoDevelopmentToken());
        dittoCredentialStore.saveProfile(credentialProfile);
    }

    private DittoCredentialProfile findDittoCredentialProfileByConnection(
            String databaseId, String authUrl) {
        String targetDatabaseId = databaseId == null ? "" : databaseId.trim();
        String targetAuthUrl = authUrl == null ? "" : authUrl.trim();
        for (DittoCredentialProfile profile : dittoCredentialStore
                .getProfiles()) {
            if (profile.getDatabaseId().equals(targetDatabaseId)
                    && profile.getAuthUrl().equals(targetAuthUrl))
                return profile;
        }
        return null;
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

    private class DeviceBuilder {
        private final String uid;
        private final String callsign;
        private boolean atak;
        private boolean ditto;
        private long lastAtakUpdate;
        private long lastDittoUpdate;
        private String teamName = "";
        private String teamId = "";
        private String role = "";
        private String atakGroupName = "";

        DeviceBuilder(String uid, String callsign) {
            this.uid = uid == null ? "" : uid.trim();
            this.callsign = callsign == null || callsign.trim().length() == 0
                    ? this.uid : callsign.trim();
        }

        DeviceConnectivitySnapshot build(boolean self) {
            long latest = Math.max(lastAtakUpdate, lastDittoUpdate);
            return new DeviceConnectivitySnapshot(uid,
                    self ? callsign + " (you)" : callsign,
                    connectionSummary(), latest <= 0L ? "No updates yet"
                            : "Last update " + formatAge(latest),
                    teamSummary(), role.length() == 0 ? "Unknown role" : role,
                    atakGroupName.length() == 0 ? "No ATAK group"
                            : atakGroupName,
                    self);
        }

        private String connectionSummary() {
            long now = System.currentTimeMillis();
            boolean dittoFresh = ditto && lastDittoUpdate > 0L
                    && now - lastDittoUpdate <= 30000L;
            boolean dittoStale = ditto && !dittoFresh;
            if (atak && dittoFresh)
                return "ATAK CoT/contact + Ditto mesh";
            if (atak && dittoStale)
                return "ATAK CoT/contact + Ditto stale";
            if (atak)
                return "ATAK CoT/contact";
            if (dittoFresh)
                return "Ditto mesh";
            if (dittoStale)
                return "Ditto stale";
            return "Unknown";
        }

        private String teamSummary() {
            if (teamName.length() > 0)
                return teamName + " | " + teamId;
            if (teamId.length() > 0)
                return teamId;
            return "Unassigned";
        }
    }

    private String getNetworkConnectivitySummary() {
        try {
            ConnectivityManager manager = (ConnectivityManager) mapView
                    .getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo active = manager == null ? null
                    : manager.getActiveNetworkInfo();
            if (active == null || !active.isConnected())
                return "no internet/network reported by Android";
            return active.getTypeName() + " connected";
        } catch (Exception exception) {
            return "unknown";
        }
    }
}
