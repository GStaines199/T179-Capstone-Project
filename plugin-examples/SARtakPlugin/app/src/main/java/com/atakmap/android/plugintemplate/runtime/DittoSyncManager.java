package com.atakmap.android.plugintemplate.runtime;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.plugintemplate.grid.SearchGridCell;
import com.atakmap.android.plugintemplate.grid.SearchGridStateStore;
import com.atakmap.android.plugintemplate.grid.SearchGridStatus;
import com.atakmap.android.plugintemplate.grid.SearchLineColorOption;
import com.atakmap.android.plugintemplate.grid.SearchTeamMember;
import com.atakmap.android.plugintemplate.plugin.BuildConfig;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.ditto.kotlin.Ditto;
import com.ditto.kotlin.DittoStoreObserver;
import com.ditto.kotlin.DittoSyncSubscription;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DittoSyncManager {

    private static final String TAG = "SARtakDittoSync";
    private static final long PUBLISH_INTERVAL_MS = 5000L;
    private static final long AUTH_RETRY_INTERVAL_MS = 30000L;
    private static final double MIN_HEADING_SPEED_METERS_PER_SECOND = 0.4;
    private static final double MAX_REASONABLE_SEARCH_SPEED_METERS_PER_SECOND =
            12.0;
    private static final String DEVICE_COLLECTION = "sartak_devices";
    private static final String DEVICE_QUERY = "SELECT * FROM "
            + DEVICE_COLLECTION;
    private static final String OPERATION_STATUS_COLLECTION =
            "sartak_operation_status";
    private static final String OPERATION_STATUS_QUERY = "SELECT * FROM "
            + OPERATION_STATUS_COLLECTION;
    private static final String TEAM_EVENT_COLLECTION = "sartak_team_events";
    private static final String TEAM_EVENT_QUERY = "SELECT * FROM "
            + TEAM_EVENT_COLLECTION;
    private static final String TEAM_MEMBERSHIP_COLLECTION =
            "sartak_team_memberships";
    private static final String TEAM_MEMBERSHIP_QUERY = "SELECT * FROM "
            + TEAM_MEMBERSHIP_COLLECTION;
    private static final String GRID_STATUS_COLLECTION =
            "sartak_grid_status";
    private static final String GRID_STATUS_QUERY = "SELECT * FROM "
            + GRID_STATUS_COLLECTION;
    private static final String SEARCH_LINE_COLLECTION =
            "sartak_search_lines";
    private static final String SEARCH_LINE_QUERY = "SELECT * FROM "
            + SEARCH_LINE_COLLECTION;
    private static final String ROUTE_PLAN_COLLECTION =
            "sartak_route_plans";
    private static final String ROUTE_PLAN_QUERY = "SELECT * FROM "
            + ROUTE_PLAN_COLLECTION;
    private static final String SHARED_MARKER_COLLECTION =
            "sartak_shared_markers";
    private static final String SHARED_MARKER_QUERY = "SELECT * FROM "
            + SHARED_MARKER_COLLECTION;
    private static final String ALERT_COLLECTION = "sartak_alerts";
    private static final String ALERT_QUERY = "SELECT * FROM "
            + ALERT_COLLECTION;
    private static final String AREA_ASSIGNMENT_COLLECTION =
            "sartak_area_assignments";
    private static final String AREA_ASSIGNMENT_QUERY = "SELECT * FROM "
            + AREA_ASSIGNMENT_COLLECTION;

    private final MapView mapView;
    private final IdentityManager identityManager;
    private final Map<String, DittoDeviceSnapshot> devices =
            Collections.synchronizedMap(new LinkedHashMap<String,
                    DittoDeviceSnapshot>());
    private final Map<String, SearchTeamCotMessage> teamEvents =
            Collections.synchronizedMap(new LinkedHashMap<String,
                    SearchTeamCotMessage>());
    private final Map<String, DittoTeamMembershipSnapshot> teamMemberships =
            Collections.synchronizedMap(new LinkedHashMap<String,
                    DittoTeamMembershipSnapshot>());
    private final Map<String, SearchGridCotMessage> gridStatuses =
            Collections.synchronizedMap(new LinkedHashMap<String,
                    SearchGridCotMessage>());
    private final Map<String, SearchLineCotMessage> searchLines =
            Collections.synchronizedMap(new LinkedHashMap<String,
                    SearchLineCotMessage>());
    private final Map<String, SearchRoutePlan> routePlans =
            Collections.synchronizedMap(new LinkedHashMap<String,
                    SearchRoutePlan>());
    private final Map<String, SharedMapMarkerMessage> sharedMapMarkers =
            Collections.synchronizedMap(new LinkedHashMap<String,
                    SharedMapMarkerMessage>());
    private final Map<String, SearchAlertMessage> alerts =
            Collections.synchronizedMap(new LinkedHashMap<String,
                    SearchAlertMessage>());
    private final Map<String, SearchAreaAssignment> areaAssignments =
            Collections.synchronizedMap(new LinkedHashMap<String,
                    SearchAreaAssignment>());
    private final Map<String, String> operationStatuses =
            Collections.synchronizedMap(new LinkedHashMap<String, String>());

    private Ditto ditto;
    private DittoSyncSubscription operationStatusSubscription;
    private DittoSyncSubscription deviceSubscription;
    private DittoSyncSubscription teamEventSubscription;
    private DittoSyncSubscription teamMembershipSubscription;
    private DittoSyncSubscription gridStatusSubscription;
    private DittoSyncSubscription searchLineSubscription;
    private DittoSyncSubscription routePlanSubscription;
    private DittoSyncSubscription sharedMarkerSubscription;
    private DittoSyncSubscription alertSubscription;
    private DittoSyncSubscription areaAssignmentSubscription;
    private DittoStoreObserver deviceObserver;
    private DittoStoreObserver operationStatusObserver;
    private DittoStoreObserver teamEventObserver;
    private DittoStoreObserver teamMembershipObserver;
    private DittoStoreObserver gridStatusObserver;
    private DittoStoreObserver searchLineObserver;
    private DittoStoreObserver routePlanObserver;
    private DittoStoreObserver sharedMarkerObserver;
    private DittoStoreObserver alertObserver;
    private DittoStoreObserver areaAssignmentObserver;
    private volatile boolean started;
    private volatile boolean configured;
    private volatile boolean authAttemptInFlight;
    private volatile String status = "Ditto: not started";
    private long lastPublishTime;
    private long lastReceiveTime;
    private long lastAuthAttemptTime;
    private String lastAuthFailure = "";
    private OperationProfile operationProfile;

    public DittoSyncManager(MapView mapView,
            IdentityManager identityManager) {
        this.mapView = mapView;
        this.identityManager = identityManager;
    }

    public void start() {
        if (started)
            return;
        configured = hasDittoCredentials();
        if (!configured) {
            status = operationProfile == null
                    ? "Ditto: no operation selected"
                    : "Ditto: not configured (" + missingCredentialSummary()
                            + ")";
            return;
        }

        try {
            DittoSdkBridge.initialize(mapView.getContext());
            ditto = DittoSdkBridge.createDitto(operationProfile
                    .getDittoDatabaseId(), operationProfile.getDittoAuthUrl());
            DittoSdkBridge.setupAuthHandler(ditto,
                    operationProfile.getDittoDevelopmentToken());
            String strictModeFailure = DittoSdkBridge.disableStrictModeSafely(
                    ditto);
            if (strictModeFailure.length() > 0)
                Log.w(TAG, "Ditto strict mode setup failed: "
                        + strictModeFailure);
            operationStatusSubscription = DittoSdkBridge
                    .registerSubscription(ditto, OPERATION_STATUS_QUERY);
            operationStatusObserver = DittoSdkBridge.registerJsonObserver(
                    ditto, OPERATION_STATUS_QUERY,
                    new java.util.function.Consumer<List<String>>() {
                        @Override
                        public void accept(List<String> jsonDocuments) {
                            updateOperationStatuses(jsonDocuments);
                        }
                    });
            deviceSubscription = DittoSdkBridge.registerSubscription(ditto,
                    DEVICE_QUERY);
            deviceObserver = DittoSdkBridge.registerJsonObserver(ditto,
                    DEVICE_QUERY, new java.util.function.Consumer<List<String>>() {
                        @Override
                        public void accept(List<String> jsonDocuments) {
                            updateSnapshots(jsonDocuments);
                        }
                    });
            teamEventSubscription = DittoSdkBridge.registerSubscription(ditto,
                    TEAM_EVENT_QUERY);
            teamEventObserver = DittoSdkBridge.registerJsonObserver(ditto,
                    TEAM_EVENT_QUERY,
                    new java.util.function.Consumer<List<String>>() {
                        @Override
                        public void accept(List<String> jsonDocuments) {
                            updateTeamEvents(jsonDocuments);
                        }
                    });
            teamMembershipSubscription = DittoSdkBridge.registerSubscription(
                    ditto, TEAM_MEMBERSHIP_QUERY);
            teamMembershipObserver = DittoSdkBridge.registerJsonObserver(ditto,
                    TEAM_MEMBERSHIP_QUERY,
                    new java.util.function.Consumer<List<String>>() {
                        @Override
                        public void accept(List<String> jsonDocuments) {
                            updateTeamMemberships(jsonDocuments);
                        }
                    });
            gridStatusSubscription = DittoSdkBridge.registerSubscription(ditto,
                    GRID_STATUS_QUERY);
            gridStatusObserver = DittoSdkBridge.registerJsonObserver(ditto,
                    GRID_STATUS_QUERY,
                    new java.util.function.Consumer<List<String>>() {
                        @Override
                        public void accept(List<String> jsonDocuments) {
                            updateGridStatuses(jsonDocuments);
                        }
                    });
            searchLineSubscription = DittoSdkBridge.registerSubscription(ditto,
                    SEARCH_LINE_QUERY);
            searchLineObserver = DittoSdkBridge.registerJsonObserver(ditto,
                    SEARCH_LINE_QUERY,
                    new java.util.function.Consumer<List<String>>() {
                        @Override
                        public void accept(List<String> jsonDocuments) {
                            updateSearchLines(jsonDocuments);
                        }
                    });
            routePlanSubscription = DittoSdkBridge.registerSubscription(ditto,
                    ROUTE_PLAN_QUERY);
            routePlanObserver = DittoSdkBridge.registerJsonObserver(ditto,
                    ROUTE_PLAN_QUERY,
                    new java.util.function.Consumer<List<String>>() {
                        @Override
                        public void accept(List<String> jsonDocuments) {
                            updateRoutePlans(jsonDocuments);
                        }
                    });
            sharedMarkerSubscription = DittoSdkBridge.registerSubscription(
                    ditto, SHARED_MARKER_QUERY);
            sharedMarkerObserver = DittoSdkBridge.registerJsonObserver(ditto,
                    SHARED_MARKER_QUERY,
                    new java.util.function.Consumer<List<String>>() {
                        @Override
                        public void accept(List<String> jsonDocuments) {
                            updateSharedMapMarkers(jsonDocuments);
                        }
                    });
            alertSubscription = DittoSdkBridge.registerSubscription(ditto,
                    ALERT_QUERY);
            alertObserver = DittoSdkBridge.registerJsonObserver(ditto,
                    ALERT_QUERY,
                    new java.util.function.Consumer<List<String>>() {
                        @Override
                        public void accept(List<String> jsonDocuments) {
                            updateAlerts(jsonDocuments);
                        }
                    });
            areaAssignmentSubscription = DittoSdkBridge.registerSubscription(
                    ditto, AREA_ASSIGNMENT_QUERY);
            areaAssignmentObserver = DittoSdkBridge.registerJsonObserver(ditto,
                    AREA_ASSIGNMENT_QUERY,
                    new java.util.function.Consumer<List<String>>() {
                        @Override
                        public void accept(List<String> jsonDocuments) {
                            updateAreaAssignments(jsonDocuments);
                        }
                    });
            started = true;
            status = "Ditto: starting for " + operationProfile
                    .getOperationName();
            attemptAuthNow();
            String syncFailure = DittoSdkBridge.startSyncSafely(ditto);
            if (syncFailure.length() > 0)
                status = "Ditto: waiting to start sync - " + syncFailure;
            else
                updateReadyStatus();
        } catch (Throwable throwable) {
            status = "Ditto: unavailable - " + describeFailure(throwable);
            cleanupDittoResources();
            Log.w(TAG, "Ditto startup failed", throwable);
        }
    }

    public void stop() {
        if (ditto == null) {
            started = false;
            status = configured ? "Ditto: stopped"
                    : (operationProfile == null
                            ? "Ditto: no operation selected"
                            : "Ditto: not configured ("
                                    + missingCredentialSummary() + ")");
            return;
        }
        cleanupDittoResources();
        started = false;
        status = configured ? "Ditto: stopped"
                : (operationProfile == null ? "Ditto: no operation selected"
                        : "Ditto: not configured ("
                                + missingCredentialSummary() + ")");
    }

    private void cleanupDittoResources() {
        DittoSdkBridge.closeObserver(operationStatusObserver);
        DittoSdkBridge.closeObserver(deviceObserver);
        DittoSdkBridge.closeObserver(teamEventObserver);
        DittoSdkBridge.closeObserver(teamMembershipObserver);
        DittoSdkBridge.closeObserver(gridStatusObserver);
        DittoSdkBridge.closeObserver(searchLineObserver);
        DittoSdkBridge.closeObserver(routePlanObserver);
        DittoSdkBridge.closeObserver(sharedMarkerObserver);
        DittoSdkBridge.closeObserver(alertObserver);
        DittoSdkBridge.closeObserver(areaAssignmentObserver);
        DittoSdkBridge.closeSubscription(operationStatusSubscription);
        DittoSdkBridge.closeSubscription(deviceSubscription);
        DittoSdkBridge.closeSubscription(teamEventSubscription);
        DittoSdkBridge.closeSubscription(teamMembershipSubscription);
        DittoSdkBridge.closeSubscription(gridStatusSubscription);
        DittoSdkBridge.closeSubscription(searchLineSubscription);
        DittoSdkBridge.closeSubscription(routePlanSubscription);
        DittoSdkBridge.closeSubscription(sharedMarkerSubscription);
        DittoSdkBridge.closeSubscription(alertSubscription);
        DittoSdkBridge.closeSubscription(areaAssignmentSubscription);
        if (ditto != null)
            DittoSdkBridge.releaseDitto(ditto);
        operationStatusObserver = null;
        operationStatusSubscription = null;
        deviceObserver = null;
        deviceSubscription = null;
        teamEventObserver = null;
        teamEventSubscription = null;
        teamMembershipObserver = null;
        teamMembershipSubscription = null;
        gridStatusObserver = null;
        gridStatusSubscription = null;
        searchLineObserver = null;
        searchLineSubscription = null;
        routePlanObserver = null;
        routePlanSubscription = null;
        sharedMarkerObserver = null;
        sharedMarkerSubscription = null;
        alertObserver = null;
        alertSubscription = null;
        areaAssignmentObserver = null;
        areaAssignmentSubscription = null;
        ditto = null;
    }

    public void useOperationProfile(OperationProfile profile) {
        boolean shouldStart = started || profile != null;
        if (started)
            stop();
        operationProfile = profile;
        clearLocalCaches();
        if (shouldStart)
            start();
    }

    public OperationProfile getOperationProfile() {
        return operationProfile;
    }

    /**
     * Restores operation credentials without touching the native Ditto
     * runtime. ATAK constructs plugin components on its UI thread, so native
     * store creation and subscription registration are deferred until core
     * startup has settled.
     */
    public void prepareOperationProfile(OperationProfile profile) {
        if (started)
            stop();
        operationProfile = profile;
        configured = hasDittoCredentials();
        clearLocalCaches();
        status = profile == null ? "Ditto: no operation selected"
                : configured ? "Ditto: waiting to start"
                        : "Ditto: not configured ("
                                + missingCredentialSummary() + ")";
    }

    public String getRemoteOperationStatus(String operationId) {
        synchronized (operationStatuses) {
            String value = operationStatuses.get(safe(operationId));
            return value == null ? "" : value;
        }
    }

    public boolean isStarted() {
        return started;
    }

    public boolean isConfigured() {
        return configured;
    }

    public boolean canCreateOperationFromBuildConfig() {
        return notEmpty(BuildConfig.DITTO_APP_ID)
                && notEmpty(BuildConfig.DITTO_PLAYGROUND_TOKEN)
                && notEmpty(BuildConfig.DITTO_AUTH_URL);
    }

    public void publishDeviceStateIfDue(boolean teamCreated, String teamId,
            String teamName, String leaderUid, String leaderCallsign,
            String roleLabel, String teamColorName, int teamColorArgb,
            SearchTeamMember selfMember, SearchGridCell selectedCell,
            AtakLocationStatus.Snapshot location) {
        if (!started || ditto == null)
            return;
        long now = System.currentTimeMillis();
        if (now - lastPublishTime < PUBLISH_INTERVAL_MS)
            return;
        publishDeviceState(teamCreated, teamId, teamName, leaderUid,
                leaderCallsign, roleLabel, teamColorName, teamColorArgb,
                selfMember, selectedCell, location, now);
    }

    public List<DittoDeviceSnapshot> getDeviceSnapshots() {
        synchronized (devices) {
            return new ArrayList<>(devices.values());
        }
    }

    public List<SearchTeamCotMessage> getTeamEvents() {
        synchronized (teamEvents) {
            return new ArrayList<>(teamEvents.values());
        }
    }

    public List<DittoTeamMembershipSnapshot> getTeamMemberships() {
        synchronized (teamMemberships) {
            return new ArrayList<>(teamMemberships.values());
        }
    }

    public List<SearchGridCotMessage> getSearchGridMessages() {
        synchronized (gridStatuses) {
            return new ArrayList<>(gridStatuses.values());
        }
    }

    public List<SearchLineCotMessage> getSearchLineMessages() {
        synchronized (searchLines) {
            return new ArrayList<>(searchLines.values());
        }
    }

    public List<SearchRoutePlan> getRoutePlans() {
        synchronized (routePlans) {
            return new ArrayList<>(routePlans.values());
        }
    }

    public SearchRoutePlan getRoutePlan(String teamId) {
        synchronized (routePlans) {
            return routePlans.get(SearchRoutePlan.routePlanId(
                    getOperationId(), teamId));
        }
    }

    public List<SharedMapMarkerMessage> getSharedMapMarkers() {
        synchronized (sharedMapMarkers) {
            return new ArrayList<>(sharedMapMarkers.values());
        }
    }

    public List<SearchAlertMessage> getAlertMessages() {
        synchronized (alerts) {
            return new ArrayList<>(alerts.values());
        }
    }

    public List<SearchAreaAssignment> getAreaAssignments() {
        synchronized (areaAssignments) {
            return new ArrayList<>(areaAssignments.values());
        }
    }

    public void publishOperationStatus(String operationStatus) {
        if (!started || ditto == null || operationProfile == null)
            return;
        String operationId = getOperationId();
        if (operationId.length() == 0)
            return;
        Map<String, Object> document = new HashMap<>();
        document.put("_id", "operation-status-" + operationId);
        document.put("operationId", operationId);
        document.put("status", safe(operationStatus));
        document.put("updatedAt", System.currentTimeMillis());
        try {
            insertDocument(OPERATION_STATUS_COLLECTION, "operationStatus",
                    document);
            updateReadyStatus();
        } catch (Throwable throwable) {
            status = "Ditto: operation status publish failed - "
                    + describeFailure(throwable);
            Log.w(TAG, "Ditto operation status publish failed", throwable);
        }
    }

    public void publishDeviceStateNow(boolean teamCreated, String teamId,
            String teamName, String leaderUid, String leaderCallsign,
            String roleLabel, String teamColorName, int teamColorArgb,
            SearchTeamMember selfMember, SearchGridCell selectedCell,
            AtakLocationStatus.Snapshot location) {
        if (!started || ditto == null)
            return;
        publishDeviceState(teamCreated, teamId, teamName, leaderUid,
                leaderCallsign, roleLabel, teamColorName, teamColorArgb,
                selfMember, selectedCell, location,
                System.currentTimeMillis());
    }

    public void publishTeamEvent(SearchTeamCotMessage message) {
        if (!started || ditto == null || message == null)
            return;
        Map<String, Object> document = new HashMap<>();
        document.put("_id", documentIdFor(message));
        document.put("operationId", getOperationId());
        document.put("uid", safe(message.getUid()));
        document.put("action", safe(message.getAction()));
        document.put("teamId", safe(message.getTeamId()));
        document.put("teamName", safe(message.getTeamName()));
        document.put("leaderUid", safe(message.getLeaderUid()));
        document.put("leaderCallsign", safe(message.getLeaderCallsign()));
        document.put("senderUid", safe(message.getSenderUid()));
        document.put("senderCallsign", safe(message.getSenderCallsign()));
        document.put("targetUid", safe(message.getTargetUid()));
        document.put("targetCallsign", safe(message.getTargetCallsign()));
        document.put("created", message.getCreated());
        document.put("teamColorName", safe(message.getTeamColorName()));
        document.put("teamColorArgb", message.getTeamColorArgb());
        document.put("memberColorName", safe(message.getMemberColorName()));
        document.put("memberColorArgb", message.getMemberColorArgb());
        document.put("memberRole", safe(message.getMemberRole()));

        try {
            insertDocument(TEAM_EVENT_COLLECTION, "event", document);
            updateReadyStatus();
        } catch (Throwable throwable) {
            status = "Ditto: team event publish failed - "
                    + describeFailure(throwable);
            Log.w(TAG, "Ditto team event publish failed", throwable);
        }
    }

    public void publishTeamMembership(String teamId, String teamName,
            String leaderUid, String leaderCallsign, String memberUid,
            String memberCallsign, String membershipStatus, String roleLabel) {
        if (!started || ditto == null)
            return;
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        if (identity == null || !identity.isResolved()) {
            status = "Ditto: waiting for ATAK identity";
            return;
        }

        long now = System.currentTimeMillis();
        Map<String, Object> document = new HashMap<>();
        document.put("_id", membershipDocumentId(teamId, memberUid));
        document.put("operationId", getOperationId());
        document.put("teamId", safe(teamId));
        document.put("teamName", safe(teamName));
        document.put("leaderUid", safe(leaderUid));
        document.put("leaderCallsign", safe(leaderCallsign));
        document.put("memberUid", safe(memberUid));
        document.put("memberCallsign", safe(memberCallsign));
        document.put("status", safe(membershipStatus));
        document.put("role", safe(roleLabel));
        document.put("updatedByUid", identity.getUid());
        document.put("updatedByCallsign", identity.getCallsign());
        document.put("updatedAt", now);

        try {
            insertDocument(TEAM_MEMBERSHIP_COLLECTION, "membership",
                    document);
            updateReadyStatus();
        } catch (Throwable throwable) {
            status = "Ditto: membership publish failed - "
                    + describeFailure(throwable);
            Log.w(TAG, "Ditto team membership publish failed", throwable);
        }
    }

    public void publishSearchGridStatus(SearchGridCotMessage message) {
        if (!started || ditto == null || message == null)
            return;
        Map<String, Object> document = new HashMap<>();
        document.put("_id", "grid-status-" + getOperationId() + "-"
                + safe(message.getTeamId()) + "-" + safe(message.getCellId()));
        document.put("operationId", getOperationId());
        document.put("uid", safe(message.getUid()));
        document.put("teamId", safe(message.getTeamId()));
        document.put("senderUid", safe(message.getSenderUid()));
        document.put("senderCallsign", safe(message.getSenderCallsign()));
        document.put("cellId", safe(message.getCellId()));
        document.put("status", message.getStatus().name());
        document.put("created", message.getCreated());

        try {
            insertDocument(GRID_STATUS_COLLECTION, "grid", document);
            updateReadyStatus();
        } catch (Throwable throwable) {
            status = "Ditto: grid publish failed - "
                    + describeFailure(throwable);
            Log.w(TAG, "Ditto grid status publish failed", throwable);
        }
    }

    public void publishSearchLine(SearchLineCotMessage message) {
        if (!started || ditto == null || message == null)
            return;
        SearchGridCell cell = message.toCell();
        Map<String, Object> document = new HashMap<>();
        document.put("_id", "search-line-" + getOperationId() + "-"
                + safe(message.getTeamId()));
        document.put("operationId", getOperationId());
        document.put("uid", safe(message.getUid()));
        document.put("action", safe(message.getAction()));
        document.put("teamId", safe(message.getTeamId()));
        document.put("senderUid", safe(message.getSenderUid()));
        document.put("senderCallsign", safe(message.getSenderCallsign()));
        document.put("zone", safe(message.getZoneDescriptor()));
        document.put("aggregateId", cell == null ? ""
                : safe(cell.getAggregateId()));
        document.put("cellId", cell == null ? "" : safe(cell.getId()));
        document.put("row", cell == null ? 0 : cell.getRow());
        document.put("column", cell == null ? 0 : cell.getColumn());
        document.put("west", cell == null ? 0.0 : cell.getWest());
        document.put("south", cell == null ? 0.0 : cell.getSouth());
        document.put("east", cell == null ? 0.0 : cell.getEast());
        document.put("north", cell == null ? 0.0 : cell.getNorth());
        document.put("lineNorthing", message.getLineNorthing());
        document.put("color", message.getColorOption().name());
        document.put("tolerance", message.getToleranceMeters());
        document.put("created", message.getCreated());

        try {
            insertDocument(SEARCH_LINE_COLLECTION, "line", document);
            updateReadyStatus();
        } catch (Throwable throwable) {
            status = "Ditto: line publish failed - "
                    + describeFailure(throwable);
            Log.w(TAG, "Ditto search line publish failed", throwable);
        }
    }

    public void publishSearchRoutePlan(SearchRoutePlan routePlan) {
        if (!started || ditto == null || routePlan == null)
            return;
        Map<String, Object> document = new HashMap<>();
        document.put("_id", safe(routePlan.getPlanId()));
        document.put("planId", safe(routePlan.getPlanId()));
        document.put("operationId", getOperationId());
        document.put("teamId", safe(routePlan.getTeamId()));
        document.put("teamName", safe(routePlan.getTeamName()));
        document.put("teamColorName", safe(routePlan.getTeamColorName()));
        document.put("teamColorArgb", routePlan.getTeamColorArgb());
        document.put("createdByUid", safe(routePlan.getCreatedByUid()));
        document.put("createdByCallsign", safe(routePlan
                .getCreatedByCallsign()));
        document.put("updatedAt", routePlan.getUpdatedAt());
        JSONArray cells = new JSONArray();
        for (String cellId : routePlan.getCellIds())
            cells.put(cellId);
        document.put("cellIds", cells);

        try {
            insertDocument(ROUTE_PLAN_COLLECTION, "routePlan", document);
            updateReadyStatus();
        } catch (Throwable throwable) {
            status = "Ditto: route publish failed - "
                    + describeFailure(throwable);
            Log.w(TAG, "Ditto route plan publish failed", throwable);
        }
    }

    public void publishAreaAssignment(SearchAreaAssignment assignment) {
        if (!started || ditto == null || assignment == null
                || assignment.getArea() == null)
            return;
        SearchGridStateStore.PlannedAreaRecord area = assignment.getArea();
        Map<String, Object> document = new HashMap<>();
        document.put("_id", assignment.getAssignmentId());
        document.put("assignmentId", assignment.getAssignmentId());
        document.put("operationId", getOperationId());
        document.put("teamId", safe(assignment.getTeamId()));
        document.put("teamName", safe(assignment.getTeamName()));
        document.put("leaderUid", safe(assignment.getLeaderUid()));
        document.put("leaderCallsign", safe(assignment.getLeaderCallsign()));
        document.put("assignedByUid", safe(assignment.getAssignedByUid()));
        document.put("assignedByCallsign", safe(assignment
                .getAssignedByCallsign()));
        document.put("status", safe(assignment.getStatus()));
        document.put("updatedAt", assignment.getUpdatedAt());
        document.put("shape", safe(area.shape));
        document.put("zone", safe(area.zone));
        document.put("centerEasting", area.centerEasting);
        document.put("centerNorthing", area.centerNorthing);
        document.put("west", area.west);
        document.put("south", area.south);
        document.put("east", area.east);
        document.put("north", area.north);
        document.put("radiusMeters", area.radiusMeters);
        document.put("widthMeters", area.widthMeters);
        document.put("heightMeters", area.heightMeters);

        try {
            insertDocument(AREA_ASSIGNMENT_COLLECTION, "assignment",
                    document);
            updateReadyStatus();
        } catch (Throwable throwable) {
            status = "Ditto: area assignment publish failed - "
                    + describeFailure(throwable);
            Log.w(TAG, "Ditto area assignment publish failed", throwable);
        }
    }

    public void clearLocalCaches() {
        synchronized (devices) {
            devices.clear();
        }
        synchronized (operationStatuses) {
            operationStatuses.clear();
        }
        synchronized (teamEvents) {
            teamEvents.clear();
        }
        synchronized (teamMemberships) {
            teamMemberships.clear();
        }
        synchronized (gridStatuses) {
            gridStatuses.clear();
        }
        synchronized (searchLines) {
            searchLines.clear();
        }
        synchronized (routePlans) {
            routePlans.clear();
        }
        synchronized (areaAssignments) {
            areaAssignments.clear();
        }
        synchronized (sharedMapMarkers) {
            sharedMapMarkers.clear();
        }
        synchronized (alerts) {
            alerts.clear();
        }
        lastReceiveTime = 0L;
        lastPublishTime = 0L;
    }

    public void maintainConnection() {
        if (!started || !configured || ditto == null)
            return;
        if (isAuthenticated()) {
            if (!isSyncActive())
                startSyncIfPossible();
            updateReadyStatus();
            return;
        }
        long now = System.currentTimeMillis();
        if (!authAttemptInFlight
                && now - lastAuthAttemptTime >= AUTH_RETRY_INTERVAL_MS)
            attemptAuthNow();
    }

    private void attemptAuthNow() {
        if (ditto == null || operationProfile == null || authAttemptInFlight)
            return;
        final Ditto targetDitto = ditto;
        final String token = operationProfile.getDittoDevelopmentToken();
        if (!notEmpty(token))
            return;
        authAttemptInFlight = true;
        lastAuthAttemptTime = System.currentTimeMillis();
        status = "Ditto: authenticating";
        DittoSdkBridge.loginAsync(targetDitto, token,
                new java.util.function.Consumer<String>() {
                    @Override
                    public void accept(String failure) {
                        authAttemptInFlight = false;
                        if (targetDitto != ditto)
                            return;
                        if (failure == null || failure.trim().length() == 0) {
                            lastAuthFailure = "";
                            startSyncIfPossible();
                            updateReadyStatus();
                        } else {
                            lastAuthFailure = failure.trim();
                            status = "Ditto: auth failed, retrying - "
                                    + lastAuthFailure;
                        }
                    }
                });
    }

    private void startSyncIfPossible() {
        if (ditto == null)
            return;
        String failure = DittoSdkBridge.startSyncSafely(ditto);
        if (failure.length() > 0)
            status = "Ditto: waiting to start sync - " + failure;
    }

    private void updateReadyStatus() {
        if (!started || ditto == null)
            return;
        if (isAuthenticated())
            status = "Ditto: active for " + operationProfile
                    .getOperationName();
        else if (lastAuthFailure.length() > 0)
            status = "Ditto: auth failed, retrying - " + lastAuthFailure;
        else
            status = "Ditto: waiting for authentication";
    }

    private boolean isAuthenticated() {
        if (ditto == null)
            return false;
        try {
            return DittoSdkBridge.isAuthenticated(ditto);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public boolean publishSharedMapMarker(SharedMapMarkerMessage message) {
        if (!started || ditto == null || message == null)
            return false;
        Map<String, Object> document = new HashMap<>();
        document.put("_id", "shared-marker-" + getOperationId() + "-"
                + safe(message.getMarkerUid()));
        document.put("operationId", getOperationId());
        document.put("uid", safe(message.getUid()));
        document.put("markerUid", safe(message.getMarkerUid()));
        document.put("markerType", safe(message.getMarkerType()));
        document.put("title", safe(message.getTitle()));
        document.put("latitude", message.getLatitude());
        document.put("longitude", message.getLongitude());
        document.put("altitude", message.getAltitude());
        document.put("senderUid", safe(message.getSenderUid()));
        document.put("senderCallsign", safe(message.getSenderCallsign()));
        document.put("teamId", safe(message.getTeamId()));
        document.put("updatedAt", message.getUpdatedAt());

        try {
            insertDocument(SHARED_MARKER_COLLECTION, "marker", document);
            updateReadyStatus();
            return true;
        } catch (Throwable throwable) {
            status = "Ditto: marker publish failed - "
                    + describeFailure(throwable);
            Log.w(TAG, "Ditto shared marker publish failed", throwable);
            return false;
        }
    }

    public boolean publishAlert(SearchAlertMessage message) {
        if (!started || ditto == null || message == null)
            return false;
        Map<String, Object> document = new HashMap<>();
        document.put("_id", "alert-" + getOperationId() + "-"
                + safe(message.getUid()));
        document.put("operationId", getOperationId());
        document.put("uid", safe(message.getUid()));
        document.put("action", safe(message.getAction()));
        document.put("alertId", safe(message.getAlertId()));
        document.put("alertType", safe(message.getAlertType()));
        document.put("title", safe(message.getTitle()));
        document.put("message", safe(message.getMessage()));
        document.put("teamId", safe(message.getTeamId()));
        document.put("teamName", safe(message.getTeamName()));
        document.put("senderUid", safe(message.getSenderUid()));
        document.put("senderCallsign", safe(message.getSenderCallsign()));
        document.put("ackUid", safe(message.getAckUid()));
        document.put("ackCallsign", safe(message.getAckCallsign()));
        document.put("latitude", message.getLatitude());
        document.put("longitude", message.getLongitude());
        document.put("requiresHalt", message.requiresHalt());
        document.put("created", message.getCreated());

        try {
            insertDocument(ALERT_COLLECTION, "alert", document);
            updateReadyStatus();
            return true;
        } catch (Throwable throwable) {
            status = "Ditto: alert publish failed - "
                    + describeFailure(throwable);
            Log.w(TAG, "Ditto alert publish failed", throwable);
            return false;
        }
    }

    private boolean isSyncActive() {
        if (ditto == null)
            return false;
        try {
            return DittoSdkBridge.isSyncActive(ditto);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public String getDiagnosticsSummary() {
        return getSummary()
                + "\nOperation: " + getOperationSummary()
                + "\nDocs: " + getDeviceSnapshots().size()
                + " devices, " + getTeamEvents().size()
                + " team events, " + getTeamMemberships().size()
                + " memberships, " + getSearchGridMessages().size()
                + " grid states, " + getSearchLineMessages().size()
                + " search lines, " + getRoutePlans().size()
                + " route plans, " + getAreaAssignments().size()
                + " area assignments, " + getAlertMessages().size()
                + " alerts";
    }

    public String getSummary() {
        if (!configured)
            return operationProfile == null ? "Ditto: no operation selected"
                    : "Ditto: not configured (" + missingCredentialSummary()
                            + ")";
        String active = "unknown";
        String auth = "unknown";
        if (ditto != null) {
            try {
                active = DittoSdkBridge.isSyncActive(ditto) ? "active"
                        : "inactive";
            } catch (Throwable ignored) {
                active = "unknown";
            }
            try {
                auth = DittoSdkBridge.isAuthenticated(ditto) ? "yes" : "no";
            } catch (Throwable ignored) {
                auth = "unknown";
            }
        }
        int peers = Math.max(0, getDeviceSnapshots().size() - 1);
        return status + " (" + active + ") | peers " + peers
                + " | auth " + auth
                + " | sent " + formatAge(lastPublishTime)
                + " | received " + formatAge(lastReceiveTime);
    }

    private void publishDeviceState(boolean teamCreated, String teamId,
            String teamName, String leaderUid, String leaderCallsign,
            String roleLabel, String teamColorName, int teamColorArgb,
            SearchTeamMember selfMember, SearchGridCell selectedCell,
            AtakLocationStatus.Snapshot location, long now) {
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        if (identity == null || !identity.isResolved()) {
            status = "Ditto: waiting for ATAK identity";
            return;
        }

        Map<String, Object> document = new HashMap<>();
        document.put("_id", "device-" + getOperationId() + "-"
                + identity.getUid());
        document.put("operationId", getOperationId());
        document.put("uid", identity.getUid());
        document.put("callsign", identity.getCallsign());
        document.put("teamCreated", teamCreated);
        document.put("teamId", safe(teamId));
        document.put("teamName", safe(teamName));
        document.put("leaderUid", safe(leaderUid));
        document.put("leaderCallsign", safe(leaderCallsign));
        document.put("role", safe(roleLabel));
        document.put("teamColorName", safe(teamColorName));
        document.put("teamColorArgb", teamColorArgb);
        document.put("memberColorName", selfMember == null ? ""
                : safe(selfMember.getColorName()));
        document.put("memberColorArgb", selfMember == null ? 0
                : selfMember.getDisplayColor());
        document.put("updatedAt", now);

        boolean hasLocation = location != null && location.isAvailable();
        document.put("hasLocation", hasLocation);
        if (hasLocation) {
            GeoPoint point = location.getPoint();
            document.put("latitude", point.getLatitude());
            document.put("longitude", point.getLongitude());
            document.put("altitude", point.isAltitudeValid()
                    ? point.getAltitude() : 0.0);
            document.put("accuracy", point.getCE());
            document.put("source", location.getSource());
        }
        double speed = selfMember == null ? 0.0
                : sanitizeSpeed(selfMember.getSpeedMetersPerSecond());
        document.put("heading", selfMember == null ? 0.0
                : selfMember.getHeadingDegrees());
        document.put("speed", speed);
        document.put("headingReliable", selfMember != null
                && selfMember.hasReliableHeading()
                && speed > MIN_HEADING_SPEED_METERS_PER_SECOND);
        document.put("gridCellId", selectedCell == null ? ""
                : selectedCell.getId());

        try {
            insertDocument(DEVICE_COLLECTION, "device", document);
            lastPublishTime = now;
            updateReadyStatus();
        } catch (Throwable throwable) {
            status = "Ditto: publish failed - " + describeFailure(throwable);
            Log.w(TAG, "Ditto publish failed", throwable);
        }
    }

    private void insertDocument(String collection, String argumentName,
            Map<String, Object> document) throws Throwable {
        String json = toDittoJson(document);
        DittoSdkBridge.executeJsonArgument(ditto, "INSERT INTO " + collection
                + " DOCUMENTS (deserialize_json(:" + argumentName
                + ")) ON ID CONFLICT DO UPDATE", argumentName, json);
    }

    private String toDittoJson(Map<String, Object> document)
            throws JSONException {
        JSONObject object = new JSONObject();
        for (Map.Entry<String, Object> entry : document.entrySet()) {
            object.put(entry.getKey(), jsonSafeValue(entry.getValue()));
        }
        String json = object.toString();
        if (json == null)
            throw new JSONException("Unable to serialize Ditto document");
        return json;
    }

    private Object jsonSafeValue(Object value) {
        if (value == null)
            return JSONObject.NULL;
        if (value instanceof Double) {
            double number = (Double) value;
            return Double.isNaN(number) || Double.isInfinite(number)
                    ? JSONObject.NULL : value;
        }
        if (value instanceof Float) {
            float number = (Float) value;
            return Float.isNaN(number) || Float.isInfinite(number)
                    ? JSONObject.NULL : value;
        }
        return value;
    }

    private void updateTeamEvents(List<String> jsonDocuments) {
        if (jsonDocuments == null)
            return;
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        String selfUid = identity == null ? "" : identity.getUid();
        LinkedHashMap<String, SearchTeamCotMessage> next =
                new LinkedHashMap<>();
        for (String json : jsonDocuments) {
            try {
                if (!isCurrentOperation(json))
                    continue;
                SearchTeamCotMessage message = teamEventFromJson(json);
                if (message.getUid().length() == 0
                        || message.getAction().length() == 0)
                    continue;
                next.put(message.getUid(), message);
            } catch (JSONException exception) {
                Log.w(TAG, "Ignoring invalid Ditto team event document",
                        exception);
            }
        }
        synchronized (teamEvents) {
            teamEvents.clear();
            teamEvents.putAll(next);
        }
        for (SearchTeamCotMessage message : next.values()) {
            if (!message.getSenderUid().equals(selfUid)) {
                lastReceiveTime = System.currentTimeMillis();
                break;
            }
        }
    }

    private void updateOperationStatuses(List<String> jsonDocuments) {
        if (jsonDocuments == null)
            return;
        LinkedHashMap<String, String> next = new LinkedHashMap<>();
        for (String json : jsonDocuments) {
            try {
                if (!isCurrentOperation(json))
                    continue;
                JSONObject object = new JSONObject(json);
                String operationId = object.optString("operationId", "");
                String operationStatus = object.optString("status", "");
                if (operationId.length() == 0 || operationStatus.length() == 0)
                    continue;
                next.put(operationId, operationStatus);
            } catch (JSONException exception) {
                Log.w(TAG, "Ignoring invalid Ditto operation status document",
                        exception);
            }
        }
        synchronized (operationStatuses) {
            operationStatuses.clear();
            operationStatuses.putAll(next);
        }
        if (!next.isEmpty())
            lastReceiveTime = System.currentTimeMillis();
    }

    private SearchTeamCotMessage teamEventFromJson(String json)
            throws JSONException {
        JSONObject object = new JSONObject(json);
        return new SearchTeamCotMessage(
                object.optString("uid", ""),
                object.optString("action", ""),
                object.optString("teamId", ""),
                object.optString("teamName", ""),
                object.optString("leaderUid", ""),
                object.optString("leaderCallsign", ""),
                object.optString("senderUid", ""),
                object.optString("senderCallsign", ""),
                object.optString("targetUid", ""),
                object.optString("targetCallsign", ""),
                object.optLong("created", 0L),
                object.optString("teamColorName", ""),
                object.optInt("teamColorArgb", 0),
                object.optString("memberColorName", ""),
                object.optInt("memberColorArgb", 0),
                object.optString("memberRole", ""),
                object.optString("operationId", ""));
    }

    private void updateTeamMemberships(List<String> jsonDocuments) {
        if (jsonDocuments == null)
            return;
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        String selfUid = identity == null ? "" : identity.getUid();
        LinkedHashMap<String, DittoTeamMembershipSnapshot> next =
                new LinkedHashMap<>();
        for (String json : jsonDocuments) {
            try {
                if (!isCurrentOperation(json))
                    continue;
                DittoTeamMembershipSnapshot membership =
                        DittoTeamMembershipSnapshot.fromJson(json);
                if (membership.getTeamId().length() == 0
                        || membership.getMemberUid().length() == 0)
                    continue;
                next.put(membershipDocumentId(membership.getTeamId(),
                        membership.getMemberUid()), membership);
            } catch (JSONException exception) {
                Log.w(TAG, "Ignoring invalid Ditto membership document",
                        exception);
            }
        }
        synchronized (teamMemberships) {
            teamMemberships.clear();
            teamMemberships.putAll(next);
        }
        for (DittoTeamMembershipSnapshot membership : next.values()) {
            if (!membership.getUpdatedByUid().equals(selfUid)) {
                lastReceiveTime = System.currentTimeMillis();
                break;
            }
        }
    }

    private void updateGridStatuses(List<String> jsonDocuments) {
        if (jsonDocuments == null)
            return;
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        String selfUid = identity == null ? "" : identity.getUid();
        LinkedHashMap<String, SearchGridCotMessage> next =
                new LinkedHashMap<>();
        for (String json : jsonDocuments) {
            try {
                if (!isCurrentOperation(json))
                    continue;
                SearchGridCotMessage message = gridStatusFromJson(json);
                if (message.getUid().length() == 0
                        || message.getCellId().length() == 0)
                    continue;
                next.put(message.getUid(), message);
            } catch (JSONException exception) {
                Log.w(TAG, "Ignoring invalid Ditto grid status document",
                        exception);
            }
        }
        synchronized (gridStatuses) {
            gridStatuses.clear();
            gridStatuses.putAll(next);
        }
        for (SearchGridCotMessage message : next.values()) {
            if (!message.getSenderUid().equals(selfUid)) {
                lastReceiveTime = System.currentTimeMillis();
                break;
            }
        }
    }

    private SearchGridCotMessage gridStatusFromJson(String json)
            throws JSONException {
        JSONObject object = new JSONObject(json);
        return new SearchGridCotMessage(
                object.optString("uid", ""),
                object.optString("teamId", ""),
                object.optString("senderUid", ""),
                object.optString("senderCallsign", ""),
                object.optString("cellId", ""),
                statusValue(object.optString("status", "")),
                object.optLong("created", 0L),
                object.optString("operationId", ""));
    }

    private void updateSearchLines(List<String> jsonDocuments) {
        if (jsonDocuments == null)
            return;
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        String selfUid = identity == null ? "" : identity.getUid();
        LinkedHashMap<String, SearchLineCotMessage> next =
                new LinkedHashMap<>();
        for (String json : jsonDocuments) {
            try {
                if (!isCurrentOperation(json))
                    continue;
                SearchLineCotMessage message = searchLineFromJson(json);
                if (message.getUid().length() == 0
                        || message.getTeamId().length() == 0)
                    continue;
                next.put(message.getUid(), message);
            } catch (JSONException exception) {
                Log.w(TAG, "Ignoring invalid Ditto search line document",
                        exception);
            }
        }
        synchronized (searchLines) {
            searchLines.clear();
            searchLines.putAll(next);
        }
        for (SearchLineCotMessage message : next.values()) {
            if (!message.getSenderUid().equals(selfUid)) {
                lastReceiveTime = System.currentTimeMillis();
                break;
            }
        }
    }

    private void updateRoutePlans(List<String> jsonDocuments) {
        if (jsonDocuments == null)
            return;
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        String selfUid = identity == null ? "" : identity.getUid();
        LinkedHashMap<String, SearchRoutePlan> next = new LinkedHashMap<>();
        for (String json : jsonDocuments) {
            try {
                if (!isCurrentOperation(json))
                    continue;
                SearchRoutePlan plan = SearchRoutePlan.fromJson(json);
                if (plan.getPlanId().length() == 0
                        || plan.getTeamId().length() == 0)
                    continue;
                next.put(plan.getPlanId(), plan);
            } catch (JSONException exception) {
                Log.w(TAG, "Ignoring invalid Ditto route plan document",
                        exception);
            }
        }
        synchronized (routePlans) {
            routePlans.clear();
            routePlans.putAll(next);
        }
        for (SearchRoutePlan plan : next.values()) {
            if (!plan.getCreatedByUid().equals(selfUid)) {
                lastReceiveTime = System.currentTimeMillis();
                break;
            }
        }
    }

    private void updateAreaAssignments(List<String> jsonDocuments) {
        if (jsonDocuments == null)
            return;
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        String selfUid = identity == null ? "" : identity.getUid();
        LinkedHashMap<String, SearchAreaAssignment> next =
                new LinkedHashMap<>();
        for (String json : jsonDocuments) {
            try {
                if (!isCurrentOperation(json))
                    continue;
                SearchAreaAssignment assignment =
                        SearchAreaAssignment.fromJson(json);
                if (assignment.getAssignmentId().length() == 0
                        || assignment.getTeamId().length() == 0)
                    continue;
                next.put(assignment.getAssignmentId(), assignment);
            } catch (JSONException exception) {
                Log.w(TAG, "Ignoring invalid Ditto area assignment document",
                        exception);
            }
        }
        synchronized (areaAssignments) {
            areaAssignments.clear();
            areaAssignments.putAll(next);
        }
        for (SearchAreaAssignment assignment : next.values()) {
            if (!assignment.getAssignedByUid().equals(selfUid)) {
                lastReceiveTime = System.currentTimeMillis();
                break;
            }
        }
    }

    private SearchLineCotMessage searchLineFromJson(String json)
            throws JSONException {
        JSONObject object = new JSONObject(json);
        return new SearchLineCotMessage(
                object.optString("uid", ""),
                object.optString("action", ""),
                object.optString("teamId", ""),
                object.optString("senderUid", ""),
                object.optString("senderCallsign", ""),
                object.optString("zone", ""),
                object.optString("aggregateId", ""),
                object.optString("cellId", ""),
                object.optInt("row", 0),
                object.optInt("column", 0),
                object.optDouble("west", 0.0),
                object.optDouble("south", 0.0),
                object.optDouble("east", 0.0),
                object.optDouble("north", 0.0),
                object.optDouble("lineNorthing", 0.0),
                lineColorValue(object.optString("color", "")),
                object.optDouble("tolerance", 0.0),
                object.optLong("created", 0L),
                object.optString("operationId", ""));
    }

    private void updateSharedMapMarkers(List<String> jsonDocuments) {
        if (jsonDocuments == null)
            return;
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        String selfUid = identity == null ? "" : identity.getUid();
        LinkedHashMap<String, SharedMapMarkerMessage> next =
                new LinkedHashMap<>();
        for (String json : jsonDocuments) {
            try {
                if (!isCurrentOperation(json))
                    continue;
                SharedMapMarkerMessage message = sharedMapMarkerFromJson(json);
                if (message.getMarkerUid().length() == 0)
                    continue;
                next.put(message.getMarkerUid(), message);
            } catch (JSONException exception) {
                Log.w(TAG, "Ignoring invalid Ditto shared marker document",
                        exception);
            }
        }
        synchronized (sharedMapMarkers) {
            sharedMapMarkers.clear();
            sharedMapMarkers.putAll(next);
        }
        for (SharedMapMarkerMessage message : next.values()) {
            if (!message.getSenderUid().equals(selfUid)) {
                lastReceiveTime = System.currentTimeMillis();
                break;
            }
        }
    }

    private SharedMapMarkerMessage sharedMapMarkerFromJson(String json)
            throws JSONException {
        JSONObject object = new JSONObject(json);
        return new SharedMapMarkerMessage(
                object.optString("uid", ""),
                object.optString("markerUid", ""),
                object.optString("markerType", ""),
                object.optString("title", ""),
                object.optDouble("latitude", Double.NaN),
                object.optDouble("longitude", Double.NaN),
                object.optDouble("altitude", 0.0),
                object.optString("senderUid", ""),
                object.optString("senderCallsign", ""),
                object.optString("teamId", ""),
                object.optString("operationId", ""),
                object.optLong("updatedAt", 0L));
    }

    private void updateAlerts(List<String> jsonDocuments) {
        if (jsonDocuments == null)
            return;
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        String selfUid = identity == null ? "" : identity.getUid();
        LinkedHashMap<String, SearchAlertMessage> next =
                new LinkedHashMap<>();
        for (String json : jsonDocuments) {
            try {
                if (!isCurrentOperation(json))
                    continue;
                SearchAlertMessage message = alertFromJson(json);
                if (message.getUid().length() == 0
                        || message.getAlertId().length() == 0)
                    continue;
                next.put(message.getUid(), message);
            } catch (JSONException exception) {
                Log.w(TAG, "Ignoring invalid Ditto alert document",
                        exception);
            }
        }
        synchronized (alerts) {
            alerts.clear();
            alerts.putAll(next);
        }
        for (SearchAlertMessage message : next.values()) {
            if (!message.getSenderUid().equals(selfUid)
                    && !message.getAckUid().equals(selfUid)) {
                lastReceiveTime = System.currentTimeMillis();
                break;
            }
        }
    }

    private SearchAlertMessage alertFromJson(String json)
            throws JSONException {
        JSONObject object = new JSONObject(json);
        return new SearchAlertMessage(
                object.optString("uid", ""),
                object.optString("action", ""),
                object.optString("alertId", ""),
                object.optString("alertType", ""),
                object.optString("title", ""),
                object.optString("message", ""),
                object.optString("teamId", ""),
                object.optString("teamName", ""),
                object.optString("senderUid", ""),
                object.optString("senderCallsign", ""),
                object.optString("ackUid", ""),
                object.optString("ackCallsign", ""),
                object.optDouble("latitude", Double.NaN),
                object.optDouble("longitude", Double.NaN),
                object.optBoolean("requiresHalt", false),
                object.optLong("created", 0L),
                object.optString("operationId", ""));
    }

    private String documentIdFor(SearchTeamCotMessage message) {
        String action = safe(message.getAction());
        if (SearchTeamCotMessage.ACTION_ADVERTISE.equals(action)
                || SearchTeamCotMessage.ACTION_PRESENCE.equals(action)) {
            return "team-event-" + getOperationId() + "-" + action + "-"
                    + safe(message.getSenderUid());
        }
        return "team-event-" + getOperationId() + "-"
                + safe(message.getUid());
    }

    private String membershipDocumentId(String teamId, String memberUid) {
        return "team-membership-" + getOperationId() + "-" + safe(teamId)
                + "-" + safe(memberUid);
    }

    private void updateSnapshots(List<String> jsonDocuments) {
        if (jsonDocuments == null)
            return;
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        String selfUid = identity == null ? "" : identity.getUid();
        LinkedHashMap<String, DittoDeviceSnapshot> next =
                new LinkedHashMap<>();
        for (String json : jsonDocuments) {
            try {
                if (!isCurrentOperation(json))
                    continue;
                DittoDeviceSnapshot snapshot = DittoDeviceSnapshot
                        .fromJson(json);
                if (snapshot.getUid().length() == 0)
                    continue;
                next.put(snapshot.getUid(), snapshot);
            } catch (JSONException exception) {
                Log.w(TAG, "Ignoring invalid Ditto device document",
                        exception);
            }
        }
        synchronized (devices) {
            devices.clear();
            devices.putAll(next);
        }
        for (DittoDeviceSnapshot snapshot : next.values()) {
            if (!snapshot.getUid().equals(selfUid)) {
                lastReceiveTime = System.currentTimeMillis();
                break;
            }
        }
    }

    private boolean hasDittoCredentials() {
        return operationProfile != null
                && operationProfile.hasDittoCredentials();
    }

    private String missingCredentialSummary() {
        if (operationProfile == null)
            return "no operation selected";
        List<String> missing = new ArrayList<>();
        if (!notEmpty(operationProfile.getDittoDatabaseId()))
            missing.add("database ID");
        if (!notEmpty(operationProfile.getDittoAuthUrl()))
            missing.add("auth URL");
        if (!notEmpty(operationProfile.getDittoDevelopmentToken()))
            missing.add("development token");
        if (missing.isEmpty())
            return "credentials loaded, restart required";
        StringBuilder builder = new StringBuilder("missing ");
        for (int i = 0; i < missing.size(); i++) {
            if (i > 0)
                builder.append(", ");
            builder.append(missing.get(i));
        }
        return builder.toString();
    }

    private String describeFailure(Throwable throwable) {
        if (throwable == null)
            return "unknown";
        StringBuilder builder = new StringBuilder(throwable.getClass()
                .getSimpleName());
        String message = throwable.getMessage();
        if (message != null && message.trim().length() > 0)
            builder.append(": ").append(message.trim());
        Throwable cause = throwable.getCause();
        if (cause != null) {
            builder.append(" | cause ").append(cause.getClass()
                    .getSimpleName());
            String causeMessage = cause.getMessage();
            if (causeMessage != null && causeMessage.trim().length() > 0)
                builder.append(": ").append(causeMessage.trim());
        }
        return builder.toString();
    }

    private boolean notEmpty(String value) {
        return value != null && value.trim().length() > 0;
    }

    private SearchGridStatus statusValue(String value) {
        try {
            return SearchGridStatus.valueOf(value);
        } catch (Exception ignored) {
            return SearchGridStatus.NOT_STARTED;
        }
    }

    private SearchLineColorOption lineColorValue(String value) {
        try {
            return SearchLineColorOption.valueOf(value);
        } catch (Exception ignored) {
            return SearchLineColorOption.CYAN;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String getOperationId() {
        return operationProfile == null ? "" : operationProfile
                .getOperationId();
    }

    private String getOperationSummary() {
        return operationProfile == null ? "No active operation selected"
                : operationProfile.getOperationName() + " | "
                        + operationProfile.getOperationId();
    }

    private boolean isCurrentOperation(String json) throws JSONException {
        String activeOperationId = getOperationId();
        if (activeOperationId.length() == 0)
            return false;
        JSONObject object = new JSONObject(json);
        return activeOperationId.equals(object.optString("operationId", ""));
    }

    private String formatAge(long timestamp) {
        if (timestamp <= 0L)
            return "never";
        long seconds = Math.max(0L, (System.currentTimeMillis() - timestamp)
                / 1000L);
        return seconds <= 1L ? "now" : seconds + " sec ago";
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
}
