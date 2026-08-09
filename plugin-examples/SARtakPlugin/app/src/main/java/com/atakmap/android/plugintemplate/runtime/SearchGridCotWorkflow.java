package com.atakmap.android.plugintemplate.runtime;

import android.os.Bundle;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.plugintemplate.grid.SearchGridStatus;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.comms.CotServiceRemote;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SearchGridCotWorkflow {

    static final String DETAIL_NAME = "__sartak_grid";
    private static final long GRID_MESSAGE_MAX_AGE_MS = 10 * 60 * 1000L;

    private final MapView mapView;
    private final IdentityManager identityManager;
    private final Map<String, SearchGridCotMessage> messages =
            Collections.synchronizedMap(new LinkedHashMap<String,
                    SearchGridCotMessage>());
    private final CotServiceRemote.CotEventListener cotEventListener =
            new CotServiceRemote.CotEventListener() {
                @Override
                public void onCotEvent(CotEvent event, Bundle extras) {
                    receiveCotEvent(event);
                }
            };

    public SearchGridCotWorkflow(MapView mapView,
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

    public void publishStatus(String teamId, String cellId,
            SearchGridStatus status) {
        if (teamId == null || teamId.length() == 0 || cellId == null
                || cellId.length() == 0 || status == null)
            return;
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        long created = System.currentTimeMillis();
        SearchGridCotMessage message = new SearchGridCotMessage(
                "sartak-grid-" + teamId + "-" + cellId + "-"
                        + identity.getUid() + "-" + created,
                teamId, identity.getUid(), identity.getCallsign(), cellId,
                status, created);
        messages.put(message.getUid(), message);
        CotEvent event = createCotEvent(message);
        if (event != null)
            CotMapComponent.getExternalDispatcher().dispatchToBroadcast(event);
    }

    public List<SearchGridCotMessage> consumeMessagesForTeam(String teamId) {
        List<SearchGridCotMessage> result = new ArrayList<>();
        if (teamId == null || teamId.length() == 0)
            return result;
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        synchronized (messages) {
            for (SearchGridCotMessage message : messages.values()) {
                if (!teamId.equals(message.getTeamId()) || isExpired(message))
                    continue;
                if (identity != null && identity.getUid().equals(
                        message.getSenderUid()))
                    continue;
                result.add(message);
            }
        }
        return result;
    }

    private CotEvent createCotEvent(SearchGridCotMessage message) {
        CoordinatedTime now = new CoordinatedTime();
        CotDetail root = new CotDetail();
        CotDetail detail = new CotDetail(DETAIL_NAME);
        for (Map.Entry<String, String> attribute : toAttributes(message)
                .entrySet())
            detail.setAttribute(attribute.getKey(), attribute.getValue());
        root.addChild(detail);

        CotEvent event = new CotEvent();
        event.setUID(message.getSenderUid());
        event.setType("a-f-G-U-C");
        event.setTime(now);
        event.setStart(now);
        event.setStale(now.addMinutes(5));
        event.setHow(CotEvent.HOW_MACHINE_GENERATED);
        event.setPoint(new CotPoint(getPublishPoint()));
        event.setDetail(root);
        return event;
    }

    /**
     * Maps a message onto the exact set of CotDetail attribute name/value
     * pairs that createCotEvent writes into the "__sartak_grid" detail
     * element. Kept ATAK-type-free (plain Map<String, String>, no CotDetail)
     * so the CoT schema mapping is unit-testable outside Android - see
     * SearchGridCotWorkflowSchemaTest.
     */
    static Map<String, String> toAttributes(SearchGridCotMessage message) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("messageUid", message.getUid());
        attributes.put("action", SearchGridCotMessage.ACTION_GRID_STATUS);
        attributes.put("teamId", message.getTeamId());
        attributes.put("senderUid", message.getSenderUid());
        attributes.put("senderCallsign", message.getSenderCallsign());
        attributes.put("cellId", message.getCellId());
        attributes.put("status", message.getStatus().name());
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
        SearchGridCotMessage message = fromCotEvent(event);
        if (message == null || isExpired(message))
            return false;
        messages.put(message.getUid(), message);
        return true;
    }

    private SearchGridCotMessage fromCotEvent(CotEvent event) {
        if (event == null)
            return null;
        CotDetail detail = event.findDetail(DETAIL_NAME);
        if (detail == null)
            return null;
        Map<String, String> attributes = new LinkedHashMap<>();
        for (String key : new String[] { "messageUid", "action", "teamId",
                "senderUid", "senderCallsign", "cellId", "status",
                "created" }) {
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
     * unparsable numbers, unknown status). ATAK-type-free and unit-tested in
     * SearchGridCotWorkflowSchemaTest.
     */
    static SearchGridCotMessage fromAttributes(Map<String, String> attributes,
            String eventUid) {
        long created = longValue(attributes, "created",
                System.currentTimeMillis());
        String messageUid = value(attributes, "messageUid");
        if (messageUid.length() == 0)
            messageUid = eventUid + "-grid-" + created;
        return new SearchGridCotMessage(messageUid,
                value(attributes, "teamId"), value(attributes, "senderUid"),
                value(attributes, "senderCallsign"),
                value(attributes, "cellId"),
                statusValue(value(attributes, "status")), created);
    }

    private boolean isExpired(SearchGridCotMessage message) {
        return System.currentTimeMillis() - message.getCreated()
                > GRID_MESSAGE_MAX_AGE_MS;
    }

    private GeoPoint getPublishPoint() {
        AtakLocationStatus.Snapshot snapshot = AtakLocationStatus.from(mapView);
        if (snapshot.isAvailable())
            return snapshot.getPoint();
        return mapView.getSelfMarker() != null
                ? mapView.getSelfMarker().getPoint()
                : new GeoPoint(0.0, 0.0);
    }

    static SearchGridStatus statusValue(String value) {
        try {
            return SearchGridStatus.valueOf(value);
        } catch (Exception ignored) {
            return SearchGridStatus.NOT_STARTED;
        }
    }

    static String value(Map<String, String> attributes, String key) {
        String value = attributes.get(key);
        return value == null ? "" : value;
    }

    static long longValue(Map<String, String> attributes, String key,
            long fallback) {
        try {
            return Long.parseLong(value(attributes, key));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
