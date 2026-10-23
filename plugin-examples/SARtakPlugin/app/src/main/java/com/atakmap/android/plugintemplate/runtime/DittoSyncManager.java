package com.atakmap.android.plugintemplate.runtime;

import android.os.SystemClock;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.plugintemplate.grid.SearchGridCell;
import com.atakmap.android.plugintemplate.grid.SearchGridStateStore;
import com.atakmap.android.plugintemplate.grid.SearchGridStatus;
import com.atakmap.android.plugintemplate.grid.SearchLineColorOption;
import com.atakmap.android.plugintemplate.grid.SearchLineDirection;
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
    private static final String PERF_TAG = "SARtakPerf";
    private static final long PUBLISH_INTERVAL_MS = 5000L;
    private static final long AUTH_RETRY_INTERVAL_MS = 30000L;
    private static final long PEER_ACTIVE_WINDOW_MS = 30000L;
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
    private final DittoWriteQueue.FailureListener writeFailureListener =
            new DittoWriteQueue.FailureListener() {
                @Override
                public void onWriteFailed(Throwable throwable) {
                    Log.w(TAG, "Ditto write task failed", throwable);
                }
            };

    /** An open store and the subscriptions and observers registered on it. */
    private static final class Connection {
        final Ditto ditto;
        final List<DittoSyncSubscription> subscriptions = new ArrayList<>();
        final List<DittoStoreObserver> observers = new ArrayList<>();

        Connection(Ditto ditto) {
            this.ditto = ditto;
        }
    }

    // Opening the store, every write to it and closing it all run on the
    // session's writer thread, so none of it blocks the main thread.
    private final Object lifecycleLock = new Object();
    private volatile DittoSession<Connection> session;
    private volatile DittoSession<Connection> previousSession;
    // The open store. Null while it is still opening after a switch.
    private volatile Ditto ditto;
    private volatile boolean started;
    private volatile boolean sharedMarkerSnapshotLoaded;
    private volatile boolean configured;
    private volatile boolean authAttemptInFlight;
    private volatile String status = "Ditto: not started";
    private volatile long lastPublishTime;
    private final PeerHeartbeats peerHeartbeats = new PeerHeartbeats();
    private long lastAuthAttemptTime;
    private volatile String lastAuthFailure = "";
    private volatile OperationProfile operationProfile;

    public DittoSyncManager(MapView mapView,
            IdentityManager identityManager) {
        this.mapView = mapView;
        this.identityManager = identityManager;
    }

    /**
     * Starts sync for the current operation. Returns straight away: the store
     * opens on the session's writer thread once the previous operation's
     * store has closed, so {@link #isStarted()} is true while it is still
     * opening, and writes queued meanwhile wait for it.
     */
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

        final OperationProfile profile = operationProfile;
        try {
            DittoSdkBridge.initialize(mapView.getContext());
        } catch (Throwable throwable) {
            Log.w(TAG, "Ditto startup failed", throwable);
            reportStartFailure(throwable);
            return;
        }
        final long queuedAt = SystemClock.elapsedRealtime();
        final DittoSession<Connection> opening =
                new DittoSession<>(writeFailureListener);
        DittoSession<Connection> previous;
        synchronized (lifecycleLock) {
            previous = previousSession;
            session = opening;
            started = true;
        }
        status = "Ditto: waiting to start for " + profile.getOperationName();
        opening.open(previous, new DittoSession.Opener<Connection>() {
            @Override
            public Connection open() throws Throwable {
                return openConnection(opening, profile, queuedAt);
            }
        }, new DittoSession.OpenListener<Connection>() {
            @Override
            public void onOpened(Connection connection) {
                onConnectionOpened(opening, connection);
            }

            @Override
            public void onOpenFailed(Throwable throwable) {
                onConnectionFailed(opening, throwable);
            }
        });
    }

    /** Runs on the session's writer thread. */
    private Connection openConnection(DittoSession<Connection> owner,
            OperationProfile profile, long queuedAt) throws Throwable {
        long openingAt = SystemClock.elapsedRealtime();
        Ditto created = DittoSdkBridge.createDitto(profile.getDittoDatabaseId(),
                profile.getDittoAuthUrl());
        long createdAt = SystemClock.elapsedRealtime();
        Connection connection = new Connection(created);
        try {
            DittoSdkBridge.setupAuthHandler(created,
                    profile.getDittoDevelopmentToken());
            // Before any insert; they all run on this thread after it.
            String strictModeFailure = DittoSdkBridge
                    .disableStrictModeSafely(created);
            if (strictModeFailure.length() > 0)
                Log.w(TAG, "Ditto strict mode setup failed: "
                        + strictModeFailure);
            register(owner, connection, OPERATION_STATUS_QUERY,
                    new java.util.function.Consumer<List<String>>() {
                        @Override
                        public void accept(List<String> jsonDocuments) {
                            updateOperationStatuses(jsonDocuments);
                        }
                    });
            register(owner, connection, DEVICE_QUERY,
                    new java.util.function.Consumer<List<String>>() {
                        @Override
                        public void accept(List<String> jsonDocuments) {
                            updateSnapshots(jsonDocuments);
                        }
                    });
            register(owner, connection, TEAM_EVENT_QUERY,
                    new java.util.function.Consumer<List<String>>() {
                        @Override
                        public void accept(List<String> jsonDocuments) {
                            updateTeamEvents(jsonDocuments);
                        }
                    });
            register(owner, connection, TEAM_MEMBERSHIP_QUERY,
                    new java.util.function.Consumer<List<String>>() {
                        @Override
                        public void accept(List<String> jsonDocuments) {
                            updateTeamMemberships(jsonDocuments);
                        }
                    });
            register(owner, connection, GRID_STATUS_QUERY,
                    new java.util.function.Consumer<List<String>>() {
                        @Override
                        public void accept(List<String> jsonDocuments) {
                            updateGridStatuses(jsonDocuments);
                        }
                    });
            register(owner, connection, SEARCH_LINE_QUERY,
                    new java.util.function.Consumer<List<String>>() {
                        @Override
                        public void accept(List<String> jsonDocuments) {
                            updateSearchLines(jsonDocuments);
                        }
                    });
            register(owner, connection, ROUTE_PLAN_QUERY,
                    new java.util.function.Consumer<List<String>>() {
                        @Override
                        public void accept(List<String> jsonDocuments) {
                            updateRoutePlans(jsonDocuments);
                        }
                    });
            register(owner, connection, SHARED_MARKER_QUERY,
                    new java.util.function.Consumer<List<String>>() {
                        @Override
                        public void accept(List<String> jsonDocuments) {
                            updateSharedMapMarkers(jsonDocuments);
                        }
                    });
            register(owner, connection, ALERT_QUERY,
                    new java.util.function.Consumer<List<String>>() {
                        @Override
                        public void accept(List<String> jsonDocuments) {
                            updateAlerts(jsonDocuments);
                        }
                    });
            register(owner, connection, AREA_ASSIGNMENT_QUERY,
                    new java.util.function.Consumer<List<String>>() {
                        @Override
                        public void accept(List<String> jsonDocuments) {
                            updateAreaAssignments(jsonDocuments);
                        }
                    });
        } catch (Throwable throwable) {
            closeConnection(connection, SystemClock.elapsedRealtime());
            throw throwable;
        }
        if (BuildConfig.DEBUG)
            Log.d(PERF_TAG, "ditto start ms waited=" + (openingAt - queuedAt)
                    + " create=" + (createdAt - openingAt)
                    + " observers=" + (SystemClock.elapsedRealtime()
                            - createdAt)
                    + " thread=" + Thread.currentThread().getName());
        return connection;
    }

    /**
     * Registers a subscription and observer for {@code query}. The observers
     * of an operation being switched away from can still fire until its
     * session closes, so their documents are ignored once it isn't current.
     */
    private void register(final DittoSession<Connection> owner,
            Connection connection, String query,
            final java.util.function.Consumer<List<String>> consumer) {
        connection.subscriptions.add(DittoSdkBridge.registerSubscription(
                connection.ditto, query));
        connection.observers.add(DittoSdkBridge.registerJsonObserver(
                connection.ditto, query,
                new java.util.function.Consumer<List<String>>() {
                    @Override
                    public void accept(List<String> jsonDocuments) {
                        if (owner == session)
                            consumer.accept(jsonDocuments);
                    }
                }));
    }

    /** Runs on the session's writer thread. */
    private void onConnectionOpened(DittoSession<Connection> owner,
            Connection connection) {
        synchronized (lifecycleLock) {
            // Stopped while opening: the queued close releases it.
            if (owner != session)
                return;
            ditto = connection.ditto;
        }
        status = "Ditto: starting for " + operationName();
        attemptAuthNow();
        String syncFailure = DittoSdkBridge.startSyncSafely(connection.ditto);
        if (owner != session)
            return;
        if (syncFailure.length() > 0)
            status = "Ditto: waiting to start sync - " + syncFailure;
        else
            updateReadyStatus();
    }

    /** Runs on the session's writer thread. */
    private void onConnectionFailed(DittoSession<Connection> owner,
            Throwable throwable) {
        Log.w(TAG, "Ditto startup failed", throwable);
        synchronized (lifecycleLock) {
            if (owner != session)
                return;
            session = null;
            ditto = null;
            started = false;
            previousSession = owner;
        }
        reportStartFailure(throwable);
        // Nothing is open; this just lets the next start stop waiting on it.
        owner.close(new DittoSession.Closer<Connection>() {
            @Override
            public void close(Connection connection) {
            }
        });
    }

    private void reportStartFailure(Throwable throwable) {
        if (isNativePluginReloadFailure(throwable))
            status = "Ditto: restart ATAK required after plugin reload";
        else
            status = "Ditto: unavailable - " + describeFailure(throwable);
    }

    private String operationName() {
        OperationProfile profile = operationProfile;
        return profile == null ? "" : profile.getOperationName();
    }

    private boolean isNativePluginReloadFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (current instanceof UnsatisfiedLinkError
                    || (message != null && message.contains("already opened by ClassLoader")))
                return true;
            current = current.getCause();
        }
        return false;
    }

    /**
     * Stops sync and queues closing the store. Returns straight away: writes
     * already queued finish first, then the store closes on its writer
     * thread. It took up to ~2.7 s on the main thread (finding T2-1).
     */
    public void stop() {
        DittoSession<Connection> closing;
        synchronized (lifecycleLock) {
            closing = session;
            session = null;
            ditto = null;
            started = false;
            if (closing != null)
                previousSession = closing;
        }
        sharedMarkerSnapshotLoaded = false;
        if (closing != null) {
            final long queuedAt = SystemClock.elapsedRealtime();
            closing.close(new DittoSession.Closer<Connection>() {
                @Override
                public void close(Connection connection) {
                    closeConnection(connection, queuedAt);
                }
            });
        }
        status = configured ? "Ditto: stopped"
                : (operationProfile == null ? "Ditto: no operation selected"
                        : "Ditto: not configured ("
                                + missingCredentialSummary() + ")");
    }

    /**
     * Waits up to {@code timeoutMs} for the last stopped store to finish
     * closing. Only for plugin unload, where nothing may be left running.
     */
    public boolean awaitStopped(long timeoutMs) {
        DittoSession<Connection> last = previousSession;
        return last == null || last.awaitClosed(timeoutMs);
    }

    /** Runs on the session's writer thread, after its queued writes. */
    private void closeConnection(Connection connection, long queuedAt) {
        long closingAt = SystemClock.elapsedRealtime();
        for (DittoStoreObserver observer : connection.observers)
            DittoSdkBridge.closeObserver(observer);
        for (DittoSyncSubscription subscription : connection.subscriptions)
            DittoSdkBridge.closeSubscription(subscription);
        long closed = SystemClock.elapsedRealtime();
        DittoSdkBridge.releaseDitto(connection.ditto);
        if (BuildConfig.DEBUG)
            Log.d(PERF_TAG, "ditto stop ms waited=" + (closingAt - queuedAt)
                    + " observers=" + (closed - closingAt)
                    + " release=" + (SystemClock.elapsedRealtime() - closed)
                    + " thread=" + Thread.currentThread().getName());
    }

    public void useOperationProfile(OperationProfile profile) {
        boolean shouldStart = started || profile != null;
        if (started)
            stop();
        operationProfile = profile;
        clearLocalCaches();
        sharedMarkerSnapshotLoaded = false;
        if (shouldStart)
            start();
    }

    public OperationProfile getOperationProfile() {
        return operationProfile;
    }

    /**
     * The running Ditto instance, or null when sync is stopped. Used by
     * features that manage their own collection, such as shared photos.
     */
    public Ditto getActiveDitto() {
        return started ? ditto : null;
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
        sharedMarkerSnapshotLoaded = false;
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

    public void publishDeviceStateIfDue(boolean teamCreated, String teamId,
            String teamName, String leaderUid, String leaderCallsign,
            String roleLabel, String teamColorName, int teamColorArgb,
            SearchTeamMember selfMember, SearchGridCell selectedCell,
            AtakLocationStatus.Snapshot location) {
        if (!started || session == null)
            return;
        long now = System.currentTimeMillis();
        if (now - lastPublishTime < PUBLISH_INTERVAL_MS)
            return;
        publishDeviceState(teamCreated, teamId, teamName, leaderUid,
                leaderCallsign, roleLabel, teamColorName, teamColorArgb,
                selfMember, selectedCell, location, now);
    }

    /** Other devices heard from in the last 30 s, not just still stored. */
    public int getRecentPeerCount() {
        return peerHeartbeats.freshCount(System.currentTimeMillis());
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

    /**
     * True once the shared marker observer has delivered the local store at
     * least once since Ditto started for this operation.
     */
    public boolean hasSharedMapMarkerSnapshot() {
        return started && sharedMarkerSnapshotLoaded;
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
        if (!started || session == null || operationProfile == null)
            return;
        String operationId = getOperationId();
        if (operationId.length() == 0)
            return;
        Map<String, Object> document = new HashMap<>();
        document.put("_id", "operation-status-" + operationId);
        document.put("operationId", operationId);
        document.put("status", safe(operationStatus));
        document.put("updatedAt", System.currentTimeMillis());
        queueInsert(OPERATION_STATUS_COLLECTION, "operationStatus", document,
                "operation status publish", null);
    }

    public void publishDeviceStateNow(boolean teamCreated, String teamId,
            String teamName, String leaderUid, String leaderCallsign,
            String roleLabel, String teamColorName, int teamColorArgb,
            SearchTeamMember selfMember, SearchGridCell selectedCell,
            AtakLocationStatus.Snapshot location) {
        if (!started || session == null)
            return;
        publishDeviceState(teamCreated, teamId, teamName, leaderUid,
                leaderCallsign, roleLabel, teamColorName, teamColorArgb,
                selfMember, selectedCell, location,
                System.currentTimeMillis());
    }

    public void publishTeamEvent(SearchTeamCotMessage message) {
        if (!started || session == null || message == null)
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

        queueInsert(TEAM_EVENT_COLLECTION, "event", document,
                "team event publish", null);
    }

    public void publishTeamMembership(String teamId, String teamName,
            String leaderUid, String leaderCallsign, String memberUid,
            String memberCallsign, String membershipStatus, String roleLabel) {
        if (!started || session == null)
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

        queueInsert(TEAM_MEMBERSHIP_COLLECTION, "membership", document,
                "membership publish", null);
    }

    public void publishSearchGridStatus(SearchGridCotMessage message) {
        if (!started || session == null || message == null)
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

        queueInsert(GRID_STATUS_COLLECTION, "grid", document,
                "grid publish", null);
    }

    public void publishSearchLine(SearchLineCotMessage message) {
        if (!started || session == null || message == null)
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
        document.put("direction", message.getDirection().name());
        document.put("lineOffset", message.getLineOffset());
        // Kept under the old name for peers that predate directional lines.
        document.put("lineNorthing", message.getLineOffset());
        document.put("color", message.getColorOption().name());
        document.put("tolerance", message.getToleranceMeters());
        document.put("created", message.getCreated());

        queueInsert(SEARCH_LINE_COLLECTION, "line", document,
                "line publish", null);
    }

    public void publishSearchRoutePlan(SearchRoutePlan routePlan) {
        if (!started || session == null || routePlan == null)
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
        // Runs keep a large-area route to a few KB; see RouteCellRuns. Both
        // fields are always written because ON ID CONFLICT DO UPDATE keeps
        // any field left out, which would leave a stale list behind.
        document.put("cellRuns", routePlan.getCellRunsJson());
        document.put("cellRunsUpdatedAt", routePlan.getUpdatedAt());
        document.put("cellIds", routePlan.getLegacyCellIdsJson());

        queueInsert(ROUTE_PLAN_COLLECTION, "routePlan", document,
                "route publish", null);
    }

    public void publishAreaAssignment(SearchAreaAssignment assignment) {
        if (!started || session == null || assignment == null
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

        queueInsert(AREA_ASSIGNMENT_COLLECTION, "assignment", document,
                "area assignment publish", null);
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
        peerHeartbeats.clear();
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
        // Also called from the writer and auth threads, so read the profile
        // once: an operation change can clear it at any time.
        OperationProfile profile = operationProfile;
        if (!started || ditto == null || profile == null)
            return;
        if (isAuthenticated())
            status = "Ditto: active for " + profile.getOperationName();
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
        return publishSharedMapMarker(message, null);
    }

    /**
     * @param onFailed run on the writer thread if the queued write later
     *                 throws, so the caller can try again
     */
    public boolean publishSharedMapMarker(SharedMapMarkerMessage message,
            Runnable onFailed) {
        if (!started || session == null || message == null)
            return false;
        return queueInsert(SHARED_MARKER_COLLECTION, "marker",
                message.toDocument(getOperationId()), "marker publish", null,
                onFailed);
    }

    /**
     * Writes only the tombstone fields. Ditto merges per field, so later
     * ordinary updates (which never write {@code deleted}) cannot undo it.
     * If an update of the same marker is still queued, the queue replaces
     * it with this tombstone, which is the outcome we want anyway.
     */
    public boolean publishSharedMapMarkerDeletion(
            SharedMapMarkerMessage tombstone) {
        return publishSharedMapMarkerDeletion(tombstone, null);
    }

    /** @param onFailed as for {@link #publishSharedMapMarker(SharedMapMarkerMessage, Runnable)} */
    public boolean publishSharedMapMarkerDeletion(
            SharedMapMarkerMessage tombstone, Runnable onFailed) {
        if (!started || session == null || tombstone == null
                || tombstone.getMarkerUid().length() == 0)
            return false;
        return queueInsert(SHARED_MARKER_COLLECTION, "marker",
                tombstone.toDeletionDocument(getOperationId()),
                "marker delete", null, onFailed);
    }

    public boolean publishAlert(SearchAlertMessage message) {
        if (!started || session == null || message == null)
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

        return queueInsert(ALERT_COLLECTION, "alert", document,
                "alert publish", null);
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

    public String getWriteQueueSummary() {
        DittoSession<Connection> current = session;
        return current == null ? "writes: Ditto not started"
                : current.getSummary();
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
        int peers = getRecentPeerCount();
        return status + " (" + active + ") | peers " + peers
                + " | auth " + auth
                + " | sent " + formatAge(lastPublishTime)
                + " | peer heard " + formatAge(peerHeartbeats.lastHeard());
    }

    private int getActivePeerCount() {
        IdentityManager.Identity identity = identityManager
                .getCurrentIdentity();
        String selfUid = identity == null ? "" : identity.getUid();
        long now = System.currentTimeMillis();
        int peers = 0;
        for (DittoDeviceSnapshot snapshot : getDeviceSnapshots()) {
            if (snapshot.getUid().equals(selfUid))
                continue;
            if (snapshot.getUpdatedAt() > 0L
                    && now - snapshot.getUpdatedAt()
                            <= PEER_ACTIVE_WINDOW_MS)
                peers++;
        }
        return peers;
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

        final long publishedAt = now;
        queueInsert(DEVICE_COLLECTION, "device", document, "publish",
                new Runnable() {
                    @Override
                    public void run() {
                        lastPublishTime = publishedAt;
                    }
                });
    }

    /**
     * Serialises the document on the caller's thread, from values it has
     * already captured, and hands only the blocking store write to the
     * writer thread. A queued write to the same document that has not run
     * yet is replaced, not repeated.
     *
     * @return false if nothing was queued; a write that fails later is
     *         reported through the status line and the log, and to
     *         {@code onFailed} when given
     */
    private boolean queueInsert(final String collection,
            final String argumentName, Map<String, Object> document,
            final String failureLabel, final Runnable onWritten) {
        return queueInsert(collection, argumentName, document, failureLabel,
                onWritten, null);
    }

    private boolean queueInsert(final String collection,
            final String argumentName, Map<String, Object> document,
            final String failureLabel, final Runnable onWritten,
            final Runnable onFailed) {
        final DittoSession<Connection> target = session;
        if (target == null)
            return false;
        final String json;
        try {
            json = toDittoJson(document);
        } catch (Throwable throwable) {
            reportWriteFailure(target, failureLabel, throwable);
            return false;
        }
        Object documentId = document.get("_id");
        // Runs after the store opens and before it closes, even across an
        // operation switch, so it reaches the operation it was made for.
        return target.submit(documentId == null ? null
                : collection + "/" + documentId,
                new DittoSession.Write<Connection>() {
                    @Override
                    public void run(Connection connection) {
                        try {
                            insertDocument(connection.ditto, collection,
                                    argumentName, json);
                        } catch (Throwable throwable) {
                            reportWriteFailure(target, failureLabel,
                                    throwable);
                            if (onFailed != null)
                                onFailed.run();
                            return;
                        }
                        if (onWritten != null)
                            onWritten.run();
                        if (target == session)
                            updateReadyStatus();
                    }
                });
    }

    private void reportWriteFailure(DittoSession<Connection> target,
            String failureLabel, Throwable throwable) {
        if (target == session)
            status = "Ditto: " + failureLabel + " failed - "
                    + describeFailure(throwable);
        Log.w(TAG, "Ditto " + failureLabel + " failed", throwable);
    }

    private void insertDocument(Ditto target, String collection,
            String argumentName, String json) throws Throwable {
        DittoSdkBridge.executeJsonArgument(target, "INSERT INTO " + collection
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
    }

    private void updateGridStatuses(List<String> jsonDocuments) {
        if (jsonDocuments == null)
            return;
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
    }

    private void updateRoutePlans(List<String> jsonDocuments) {
        if (jsonDocuments == null)
            return;
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
    }

    private void updateAreaAssignments(List<String> jsonDocuments) {
        if (jsonDocuments == null)
            return;
        LinkedHashMap<String, SearchAreaAssignment> next =
                new LinkedHashMap<>();
        for (String json : jsonDocuments) {
            try {
                if (!isCurrentOperation(json))
                    continue;
                SearchAreaAssignment assignment =
                        SearchAreaAssignment.fromJson(json);
                if (assignment.getAssignmentId().length() == 0)
                    continue;
                String assignmentId = assignment.getAssignmentId();
                next.put(assignmentId, SearchAreaAssignment.newest(
                        next.get(assignmentId), assignment));
            } catch (JSONException exception) {
                Log.w(TAG, "Ignoring invalid Ditto area assignment document",
                        exception);
            }
        }
        synchronized (areaAssignments) {
            areaAssignments.clear();
            areaAssignments.putAll(next);
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
                object.optDouble("lineOffset",
                        object.optDouble("lineNorthing", 0.0)),
                lineColorValue(object.optString("color", "")),
                object.optDouble("tolerance", 0.0),
                object.optLong("created", 0L),
                object.optString("operationId", ""),
                SearchLineDirection.fromName(
                        object.optString("direction", "")));
    }

    private void updateSharedMapMarkers(List<String> jsonDocuments) {
        if (jsonDocuments == null)
            return;
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
        sharedMarkerSnapshotLoaded = true;
    }

    private SharedMapMarkerMessage sharedMapMarkerFromJson(String json)
            throws JSONException {
        return SharedMapMarkerMessage.fromJson(new JSONObject(json));
    }

    private void updateAlerts(List<String> jsonDocuments) {
        if (jsonDocuments == null)
            return;
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
        long now = System.currentTimeMillis();
        for (DittoDeviceSnapshot snapshot : next.values()) {
            if (!snapshot.getUid().equals(selfUid))
                peerHeartbeats.observe(snapshot.getUid(),
                        snapshot.getUpdatedAt(), now);
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
