package com.atakmap.android.plugintemplate.runtime;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.maps.MapData;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.plugintemplate.grid.SearchGridCell;
import com.atakmap.android.plugintemplate.grid.SearchLineColorOption;
import com.atakmap.android.plugintemplate.grid.SearchLineManager;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.comms.CotServiceRemote;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class SearchLineCotWorkflow {

    private static final String ATAK_CIV_PACKAGE = "com.atakmap.app.civ";
    static final String DETAIL_NAME = "__sartak_line";
    private static final long LINE_MESSAGE_MAX_AGE_MS = 30000L;
    private static final long UPDATE_INTERVAL_MS = 5000L;

    private final MapView mapView;
    private final IdentityManager identityManager;
    private final Map<String, SearchLineCotMessage> messages =
            Collections.synchronizedMap(new LinkedHashMap<String,
                    SearchLineCotMessage>());
    private final CotServiceRemote.CotEventListener cotEventListener =
            new CotServiceRemote.CotEventListener() {
                @Override
                public void onCotEvent(CotEvent event, Bundle extras) {
                    receiveCotEvent(event);
                }
            };
    private long lastUpdatePublishTime;

    public SearchLineCotWorkflow(MapView mapView,
            IdentityManager identityManager) {
        this.mapView = mapView;
        this.identityManager = identityManager;
        registerDirectCotProcessor();
    }

    public void dispose() {
        try {
            CommsMapComponent.getInstance()
                    .removeOnCotEventListener(cotEventListener);
        } catch (Exception ignored) {
        }
    }

    public void publishNow(String action, String teamId,
            SearchLineManager manager) {
        publish(action, teamId, manager);
        if (SearchLineCotMessage.ACTION_UPDATE.equals(action))
            lastUpdatePublishTime = System.currentTimeMillis();
    }

    public void publishUpdateIfDue(String teamId,
            SearchLineManager manager) {
        long now = System.currentTimeMillis();
        if (now - lastUpdatePublishTime < UPDATE_INTERVAL_MS)
            return;
        publish(manager.isPaused() ? SearchLineCotMessage.ACTION_PAUSE
                : SearchLineCotMessage.ACTION_UPDATE, teamId, manager);
        lastUpdatePublishTime = now;
    }

    public SearchLineCotMessage getLatestForTeam(String teamId) {
        if (teamId == null || teamId.length() == 0)
            return null;
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        SearchLineCotMessage latest = null;
        synchronized (messages) {
            for (SearchLineCotMessage message : messages.values()) {
                if (!teamId.equals(message.getTeamId()))
                    continue;
                if (identity != null && identity.getUid().equals(
                        message.getSenderUid()))
                    continue;
                if (isExpired(message))
                    continue;
                if (latest == null || message.getCreated()
                        > latest.getCreated())
                    latest = message;
            }
        }
        return latest;
    }

    private void publish(String action, String teamId,
            SearchLineManager manager) {
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        long created = System.currentTimeMillis();
        SearchGridCell cell = manager.getActiveCell();
        SearchLineCotMessage message = new SearchLineCotMessage(
                "sartak-line-" + action + "-" + teamId + "-"
                        + identity.getUid() + "-" + created,
                action, teamId, identity.getUid(), identity.getCallsign(),
                cell == null ? "" : cell.getZoneDescriptor(),
                cell == null ? "" : cell.getAggregateId(),
                cell == null ? "" : cell.getId(),
                cell == null ? 0 : cell.getRow(),
                cell == null ? 0 : cell.getColumn(),
                cell == null ? 0.0 : cell.getWest(),
                cell == null ? 0.0 : cell.getSouth(),
                cell == null ? 0.0 : cell.getEast(),
                cell == null ? 0.0 : cell.getNorth(),
                manager.getLineNorthing(), manager.getColorOption(),
                manager.getReturnMarkToleranceMeters(), created);
        messages.put(message.getUid(), message);
        CotEvent event = createCotEvent(message);
        if (event != null)
            CotMapComponent.getExternalDispatcher().dispatchToBroadcast(event);
    }

    private CotEvent createCotEvent(SearchLineCotMessage message) {
        CoordinatedTime now = new CoordinatedTime();
        CotDetail root = new CotDetail();
        CotDetail detail = new CotDetail(DETAIL_NAME);
        for (Map.Entry<String, String> attribute : toAttributes(message)
                .entrySet())
            detail.setAttribute(attribute.getKey(), attribute.getValue());
        root.addChild(detail);
        addStandardContactDetails(root, message.getSenderCallsign());

        CotEvent event = new CotEvent();
        event.setUID(message.getSenderUid());
        event.setType(getSelfCotType());
        event.setTime(now);
        event.setStart(now);
        event.setStale(now.addMinutes(1));
        event.setHow(CotEvent.HOW_MACHINE_GENERATED);
        event.setPoint(new CotPoint(getPublishPoint()));
        event.setDetail(root);
        return event;
    }

    /**
     * Maps a message onto the exact set of CotDetail attribute name/value
     * pairs that createCotEvent writes into the "__sartak_line" detail
     * element (the contact/uid/__group details added separately by
     * addStandardContactDetails are one-way ATAK/WinTAK presentation only -
     * fromCotEvent never reads them back, so they are out of scope for this
     * schema mapping). Kept ATAK-type-free so it is unit-testable outside
     * Android - see SearchLineCotWorkflowSchemaTest.
     */
    static Map<String, String> toAttributes(SearchLineCotMessage message) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("messageUid", message.getUid());
        attributes.put("action", message.getAction());
        attributes.put("teamId", message.getTeamId());
        attributes.put("senderUid", message.getSenderUid());
        attributes.put("senderCallsign", message.getSenderCallsign());
        attributes.put("zone", message.getZoneDescriptor());
        SearchGridCell cell = message.toCell();
        if (cell != null) {
            attributes.put("aggregateId", cell.getAggregateId());
            attributes.put("cellId", cell.getId());
            attributes.put("row", String.valueOf(cell.getRow()));
            attributes.put("column", String.valueOf(cell.getColumn()));
            attributes.put("west", String.valueOf(cell.getWest()));
            attributes.put("south", String.valueOf(cell.getSouth()));
            attributes.put("east", String.valueOf(cell.getEast()));
            attributes.put("north", String.valueOf(cell.getNorth()));
        }
        attributes.put("lineNorthing", String.valueOf(
                message.getLineNorthing()));
        attributes.put("color", message.getColorOption().name());
        attributes.put("tolerance", String.valueOf(
                message.getToleranceMeters()));
        attributes.put("created", String.valueOf(message.getCreated()));
        return attributes;
    }

    private void registerDirectCotProcessor() {
        try {
            CommsMapComponent comms = CommsMapComponent.getInstance();
            comms.registerDirectProcessor(
                    new CommsMapComponent.DirectCotProcessor() {
                        @Override
                        public CommsMapComponent.ImportResult processCotEvent(
                                CotEvent event, Bundle extras) {
                            return receiveCotEvent(event)
                                    ? CommsMapComponent.ImportResult.SUCCESS
                                    : CommsMapComponent.ImportResult.IGNORE;
                        }
                    });
            comms.addOnCotEventListener(cotEventListener);
        } catch (Exception ignored) {
        }
    }

    private boolean receiveCotEvent(CotEvent event) {
        SearchLineCotMessage message = fromCotEvent(event);
        if (message == null || isExpired(message))
            return false;
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        if (identity == null || !identity.getUid().equals(
                message.getSenderUid()))
            messages.put(message.getUid(), message);
        return true;
    }

    private SearchLineCotMessage fromCotEvent(CotEvent event) {
        if (event == null)
            return null;
        CotDetail detail = event.findDetail(DETAIL_NAME);
        if (detail == null)
            return null;
        Map<String, String> attributes = new LinkedHashMap<>();
        for (String key : new String[] { "messageUid", "action", "teamId",
                "senderUid", "senderCallsign", "zone", "aggregateId",
                "cellId", "row", "column", "west", "south", "east", "north",
                "lineNorthing", "color", "tolerance", "created" }) {
            String value = detail.getAttribute(key);
            if (value != null)
                attributes.put(key, value);
        }
        return fromAttributes(attributes, event.getUID());
    }

    /**
     * Inverse of toAttributes: reconstructs a message from the CotDetail
     * attribute name/value pairs read off a received event, applying the
     * same defensive fallbacks fromCotEvent always has (missing messageUid,
     * unparsable numbers, unknown color). ATAK-type-free and unit-tested in
     * SearchLineCotWorkflowSchemaTest.
     */
    static SearchLineCotMessage fromAttributes(Map<String, String> attributes,
            String eventUid) {
        long created = longValue(attributes, "created",
                System.currentTimeMillis());
        String messageUid = value(attributes, "messageUid");
        if (messageUid.length() == 0)
            messageUid = eventUid + "-line-" + created;
        return new SearchLineCotMessage(messageUid,
                value(attributes, "action"), value(attributes, "teamId"),
                value(attributes, "senderUid"),
                value(attributes, "senderCallsign"),
                value(attributes, "zone"), value(attributes, "aggregateId"),
                value(attributes, "cellId"), intValue(attributes, "row"),
                intValue(attributes, "column"),
                doubleValue(attributes, "west"),
                doubleValue(attributes, "south"),
                doubleValue(attributes, "east"),
                doubleValue(attributes, "north"),
                doubleValue(attributes, "lineNorthing"),
                colorValue(value(attributes, "color")),
                doubleValue(attributes, "tolerance"), created);
    }

    private void addStandardContactDetails(CotDetail root, String callsign) {
        String safeCallsign = firstNonEmpty(callsign, getSelfCallsign());
        if (safeCallsign.length() > 0) {
            CotDetail contact = new CotDetail("contact");
            contact.setAttribute("callsign", safeCallsign);
            root.addChild(contact);

            CotDetail uid = new CotDetail("uid");
            uid.setAttribute("Droid", safeCallsign);
            root.addChild(uid);
        }

        String teamName = getSelfTeamName();
        if (teamName.length() == 0)
            return;
        CotDetail group = new CotDetail("__group");
        group.setAttribute("name", teamName);
        group.setAttribute("role", getSelfTeamRole());
        root.addChild(group);
    }

    private boolean isExpired(SearchLineCotMessage message) {
        long age = System.currentTimeMillis() - message.getCreated();
        return age > LINE_MESSAGE_MAX_AGE_MS;
    }

    private GeoPoint getPublishPoint() {
        AtakLocationStatus.Snapshot snapshot = AtakLocationStatus.from(mapView);
        if (snapshot.isAvailable())
            return snapshot.getPoint();
        return mapView.getSelfMarker() != null
                ? mapView.getSelfMarker().getPoint()
                : new GeoPoint(0.0, 0.0);
    }

    private String getSelfCotType() {
        Marker self = mapView == null ? null : mapView.getSelfMarker();
        String type = self == null ? "" : self.getType();
        if (type != null && type.startsWith("a-"))
            return type;
        return "a-f-G-U-C";
    }

    private String getSelfCallsign() {
        Marker self = mapView == null ? null : mapView.getSelfMarker();
        if (self == null)
            return "";
        return firstNonEmpty(self.getMetaString("callsign", ""),
                self.getTitle(), mapView.getDeviceCallsign());
    }

    private String getSelfTeamName() {
        Marker self = mapView == null ? null : mapView.getSelfMarker();
        String fromSelf = self == null ? "" : firstNonEmpty(
                self.getMetaString("__groupName", ""),
                self.getMetaString("team", ""),
                self.getMetaString("atakTeam", ""),
                self.getMetaString("teamColor", ""),
                self.getMetaString("groupName", ""),
                self.getMetaString("locationTeam", ""));
        if (fromSelf.length() > 0)
            return fromSelf;

        MapData data = mapView == null ? null : mapView.getMapData();
        if (data != null) {
            String fromMapData = firstNonEmpty(
                    data.getString("__groupName", ""),
                    data.getString("team", ""),
                    data.getString("atakTeam", ""),
                    data.getString("teamColor", ""),
                    data.getString("groupName", ""),
                    data.getString("locationTeam", ""));
            if (fromMapData.length() > 0)
                return fromMapData;
        }

        return firstNonEmpty(getPreferenceValue("locationTeam"),
                getPreferenceValue("__groupName"),
                getPreferenceValue("team"),
                getPreferenceValue("atakTeam"),
                getPreferenceValue("teamColor"),
                getPreferenceValue("groupName"));
    }

    private String getSelfTeamRole() {
        Marker self = mapView == null ? null : mapView.getSelfMarker();
        String fromSelf = self == null ? "" : firstNonEmpty(
                self.getMetaString("__groupRole", ""),
                self.getMetaString("atakRoleType", ""),
                self.getMetaString("atakRole", ""),
                self.getMetaString("teamRole", ""),
                self.getMetaString("role", ""));
        if (fromSelf.length() > 0)
            return fromSelf;
        return firstNonEmpty(getPreferenceValue("atakRoleType"),
                getPreferenceValue("atakRole"),
                getPreferenceValue("teamRole"),
                getPreferenceValue("__groupRole"),
                getPreferenceValue("role"), "Team Member");
    }

    private String getPreferenceValue(String key) {
        Context context = mapView == null ? null : mapView.getContext();
        String value = getPreferenceValue(context, key);
        if (value.length() > 0)
            return value;
        try {
            Context atakContext = context == null ? null
                    : context.createPackageContext(ATAK_CIV_PACKAGE,
                            Context.CONTEXT_IGNORE_SECURITY);
            return getPreferenceValue(atakContext, key);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String getPreferenceValue(Context context, String key) {
        if (context == null)
            return "";
        try {
            SharedPreferences preferences = PreferenceManager
                    .getDefaultSharedPreferences(context);
            return safe(preferences.getString(key, ""));
        } catch (Exception ignored) {
            return "";
        }
    }

    static String value(Map<String, String> attributes, String key) {
        String value = attributes.get(key);
        return value == null ? "" : value;
    }

    static int intValue(Map<String, String> attributes, String key) {
        try {
            return Integer.parseInt(value(attributes, key));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    static long longValue(Map<String, String> attributes, String key,
            long fallback) {
        try {
            return Long.parseLong(value(attributes, key));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    static double doubleValue(Map<String, String> attributes, String key) {
        try {
            return Double.parseDouble(value(attributes, key));
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    static SearchLineColorOption colorValue(String value) {
        try {
            return SearchLineColorOption.valueOf(value);
        } catch (Exception ignored) {
            return SearchLineColorOption.CYAN;
        }
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            String safe = safe(value);
            if (safe.length() > 0)
                return safe;
        }
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
