package com.atakmap.android.plugintemplate.runtime;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.plugintemplate.grid.SearchGridCell;
import com.atakmap.android.plugintemplate.grid.SearchGridStatus;
import com.atakmap.android.plugintemplate.grid.SearchLineColorOption;
import com.atakmap.android.plugintemplate.grid.SearchTeamMember;
import com.atakmap.android.plugintemplate.plugin.BuildConfig;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.ditto.kotlin.Ditto;
import com.ditto.kotlin.DittoStoreObserver;
import com.ditto.kotlin.DittoSyncSubscription;

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
    private static final double MIN_HEADING_SPEED_METERS_PER_SECOND = 0.4;
    private static final double MAX_REASONABLE_SEARCH_SPEED_METERS_PER_SECOND =
            12.0;
    private static final String DEVICE_COLLECTION = "sartak_devices";
    private static final String DEVICE_QUERY = "SELECT * FROM "
            + DEVICE_COLLECTION;
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

    private Ditto ditto;
    private DittoSyncSubscription deviceSubscription;
    private DittoSyncSubscription teamEventSubscription;
    private DittoSyncSubscription teamMembershipSubscription;
    private DittoSyncSubscription gridStatusSubscription;
    private DittoSyncSubscription searchLineSubscription;
    private DittoStoreObserver deviceObserver;
    private DittoStoreObserver teamEventObserver;
    private DittoStoreObserver teamMembershipObserver;
    private DittoStoreObserver gridStatusObserver;
    private DittoStoreObserver searchLineObserver;
    private boolean started;
    private boolean configured;
    private String status = "Ditto: not started";
    private long lastPublishTime;
    private long lastReceiveTime;
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
            DittoSdkBridge.setupAuth(ditto,
                    operationProfile.getDittoDevelopmentToken());
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
            DittoSdkBridge.startSync(ditto);
            started = true;
            status = "Ditto: active for " + operationProfile
                    .getOperationName();
        } catch (Throwable throwable) {
            status = "Ditto: unavailable - " + describeFailure(throwable);
            Log.w(TAG, "Ditto startup failed", throwable);
        }
    }

    public void stop() {
        if (ditto == null)
            return;
        try {
            DittoSdkBridge.stopSync(ditto);
        } catch (Throwable ignored) {
        }
        started = false;
        status = configured ? "Ditto: stopped"
                : (operationProfile == null ? "Ditto: no operation selected"
                        : "Ditto: not configured ("
                                + missingCredentialSummary() + ")");
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

        Map<String, Object> args = Collections.singletonMap("event",
                (Object) document);
        try {
            DittoSdkBridge.execute(ditto, "INSERT INTO "
                    + TEAM_EVENT_COLLECTION
                    + " DOCUMENTS (:event) ON ID CONFLICT DO UPDATE", args);
            status = "Ditto: active";
        } catch (Throwable throwable) {
            status = "Ditto: team event publish failed - "
                    + throwable.getClass().getSimpleName();
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

        Map<String, Object> args = Collections.singletonMap("membership",
                (Object) document);
        try {
            DittoSdkBridge.execute(ditto, "INSERT INTO "
                    + TEAM_MEMBERSHIP_COLLECTION
                    + " DOCUMENTS (:membership) ON ID CONFLICT DO UPDATE",
                    args);
            status = "Ditto: active";
        } catch (Throwable throwable) {
            status = "Ditto: membership publish failed - "
                    + throwable.getClass().getSimpleName();
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

        Map<String, Object> args = Collections.singletonMap("grid",
                (Object) document);
        try {
            DittoSdkBridge.execute(ditto, "INSERT INTO "
                    + GRID_STATUS_COLLECTION
                    + " DOCUMENTS (:grid) ON ID CONFLICT DO UPDATE", args);
            status = "Ditto: active";
        } catch (Throwable throwable) {
            status = "Ditto: grid publish failed - "
                    + throwable.getClass().getSimpleName();
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

        Map<String, Object> args = Collections.singletonMap("line",
                (Object) document);
        try {
            DittoSdkBridge.execute(ditto, "INSERT INTO "
                    + SEARCH_LINE_COLLECTION
                    + " DOCUMENTS (:line) ON ID CONFLICT DO UPDATE", args);
            status = "Ditto: active";
        } catch (Throwable throwable) {
            status = "Ditto: line publish failed - "
                    + throwable.getClass().getSimpleName();
            Log.w(TAG, "Ditto search line publish failed", throwable);
        }
    }

    public void clearLocalCaches() {
        synchronized (devices) {
            devices.clear();
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
        lastReceiveTime = 0L;
        lastPublishTime = 0L;
    }

    public String getDiagnosticsSummary() {
        return getSummary()
                + "\nOperation: " + getOperationSummary()
                + "\nDocs: " + getDeviceSnapshots().size()
                + " devices, " + getTeamEvents().size()
                + " team events, " + getTeamMemberships().size()
                + " memberships, " + getSearchGridMessages().size()
                + " grid states, " + getSearchLineMessages().size()
                + " search lines";
    }

    public String getSummary() {
        if (!configured)
            return operationProfile == null ? "Ditto: no operation selected"
                    : "Ditto: not configured (" + missingCredentialSummary()
                            + ")";
        String active = "unknown";
        if (ditto != null) {
            try {
                active = DittoSdkBridge.isSyncActive(ditto) ? "active"
                        : "inactive";
            } catch (Throwable ignored) {
                active = "unknown";
            }
        }
        int peers = Math.max(0, getDeviceSnapshots().size() - 1);
        return status + " (" + active + ") | peers " + peers
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

        Map<String, Object> args = Collections.singletonMap("device",
                (Object) document);
        try {
            DittoSdkBridge.execute(ditto, "INSERT INTO " + DEVICE_COLLECTION
                    + " DOCUMENTS (:device) ON ID CONFLICT DO UPDATE", args);
            lastPublishTime = now;
            status = "Ditto: active";
        } catch (Throwable throwable) {
            status = "Ditto: publish failed - " + throwable.getClass()
                    .getSimpleName();
            Log.w(TAG, "Ditto publish failed", throwable);
        }
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
