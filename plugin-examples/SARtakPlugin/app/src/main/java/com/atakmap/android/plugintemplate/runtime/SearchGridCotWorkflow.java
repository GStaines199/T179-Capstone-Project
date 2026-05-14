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

    private static final String DETAIL_NAME = "__sartak_grid";
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
        detail.setAttribute("messageUid", message.getUid());
        detail.setAttribute("action", SearchGridCotMessage.ACTION_GRID_STATUS);
        detail.setAttribute("teamId", message.getTeamId());
        detail.setAttribute("senderUid", message.getSenderUid());
        detail.setAttribute("senderCallsign", message.getSenderCallsign());
        detail.setAttribute("cellId", message.getCellId());
        detail.setAttribute("status", message.getStatus().name());
        detail.setAttribute("created", String.valueOf(message.getCreated()));
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
        long created = longValue(detail, "created", System.currentTimeMillis());
        String messageUid = value(detail, "messageUid");
        if (messageUid.length() == 0)
            messageUid = event.getUID() + "-grid-" + created;
        return new SearchGridCotMessage(messageUid, value(detail, "teamId"),
                value(detail, "senderUid"), value(detail, "senderCallsign"),
                value(detail, "cellId"), statusValue(value(detail, "status")),
                created);
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

    private SearchGridStatus statusValue(String value) {
        try {
            return SearchGridStatus.valueOf(value);
        } catch (Exception ignored) {
            return SearchGridStatus.NOT_STARTED;
        }
    }

    private String value(CotDetail detail, String key) {
        String value = detail.getAttribute(key);
        return value == null ? "" : value;
    }

    private long longValue(CotDetail detail, String key, long fallback) {
        try {
            return Long.parseLong(value(detail, key));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
