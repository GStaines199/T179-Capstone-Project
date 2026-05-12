package com.atakmap.android.plugintemplate.runtime;

import android.os.Bundle;

import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.comms.CotServiceRemote;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SearchTeamCotWorkflow {

    private static final String GROUP_NAME = "SARtak Team CoT";
    private static final long ADVERTISE_INTERVAL_MS = 10000L;

    private final MapView mapView;
    private final IdentityManager identityManager;
    private final Map<String, SearchTeamCotMessage> directMessages =
            Collections.synchronizedMap(new LinkedHashMap<String,
                    SearchTeamCotMessage>());
    private final CotServiceRemote.CotEventListener cotEventListener =
            new CotServiceRemote.CotEventListener() {
                @Override
                public void onCotEvent(CotEvent event, Bundle extras) {
                    receiveCotEvent(event);
                }
            };
    private MapGroup messageGroup;
    private long lastAdvertiseTime;

    public SearchTeamCotWorkflow(MapView mapView,
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

    public void advertiseTeam(String teamId, String teamName) {
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        publish(SearchTeamCotMessage.ACTION_ADVERTISE, teamId, teamName,
                identity.getUid(), identity.getCallsign(), "", "");
        lastAdvertiseTime = System.currentTimeMillis();
    }

    public void advertiseTeamIfDue(String teamId, String teamName) {
        long now = System.currentTimeMillis();
        if (now - lastAdvertiseTime < ADVERTISE_INTERVAL_MS)
            return;
        advertiseTeam(teamId, teamName);
    }

    public void requestJoin(SearchTeamCotMessage team) {
        publish(SearchTeamCotMessage.ACTION_JOIN_REQUEST, team.getTeamId(),
                team.getTeamName(), team.getLeaderUid(),
                team.getLeaderCallsign(), team.getLeaderUid(),
                team.getLeaderCallsign());
    }

    public void cancelJoinRequest(SearchTeamCotMessage request) {
        publish(SearchTeamCotMessage.ACTION_JOIN_CANCEL, request.getTeamId(),
                request.getTeamName(), request.getLeaderUid(),
                request.getLeaderCallsign(), request.getLeaderUid(),
                request.getLeaderCallsign());
    }

    public void inviteMember(String teamId, String teamName, String targetUid,
            String targetCallsign) {
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        publish(SearchTeamCotMessage.ACTION_INVITE, teamId, teamName,
                identity.getUid(), identity.getCallsign(), targetUid,
                targetCallsign);
    }

    public void cancelInvite(SearchTeamCotMessage invite) {
        publish(SearchTeamCotMessage.ACTION_INVITE_CANCEL,
                invite.getTeamId(), invite.getTeamName(),
                invite.getLeaderUid(), invite.getLeaderCallsign(),
                invite.getTargetUid(), invite.getTargetCallsign());
    }

    public void respondToInvite(SearchTeamCotMessage invite,
            boolean accepted) {
        publish(accepted ? SearchTeamCotMessage.ACTION_INVITE_ACCEPT
                        : SearchTeamCotMessage.ACTION_INVITE_DECLINE,
                invite.getTeamId(), invite.getTeamName(),
                invite.getLeaderUid(), invite.getLeaderCallsign(),
                invite.getLeaderUid(), invite.getLeaderCallsign());
    }

    public void respondToJoin(SearchTeamCotMessage request, boolean accepted) {
        publish(accepted ? SearchTeamCotMessage.ACTION_JOIN_ACCEPT
                        : SearchTeamCotMessage.ACTION_JOIN_DECLINE,
                request.getTeamId(), request.getTeamName(),
                request.getLeaderUid(), request.getLeaderCallsign(),
                request.getSenderUid(), request.getSenderCallsign());
    }

    public List<SearchTeamCotMessage> getTeamAdvertisements() {
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        List<SearchTeamCotMessage> filtered = new ArrayList<>();
        for (SearchTeamCotMessage message : getMessages(
                SearchTeamCotMessage.ACTION_ADVERTISE, "")) {
            if (!identity.getUid().equals(message.getLeaderUid()))
                filtered.add(message);
        }
        return dedupeByTeam(filtered);
    }

    public List<SearchTeamCotMessage> getJoinRequests(String teamId) {
        List<SearchTeamCotMessage> filtered = new ArrayList<>();
        for (SearchTeamCotMessage message : getMessages(
                SearchTeamCotMessage.ACTION_JOIN_REQUEST, "")) {
            if (teamId.equals(message.getTeamId())
                    && !isCancelled(SearchTeamCotMessage.ACTION_JOIN_CANCEL,
                            message))
                filtered.add(message);
        }
        return dedupeBySender(filtered);
    }

    public List<SearchTeamCotMessage> getJoinResponsesForMe() {
        List<SearchTeamCotMessage> messages = new ArrayList<>();
        messages.addAll(scanForMe(SearchTeamCotMessage.ACTION_JOIN_ACCEPT));
        messages.addAll(scanForMe(SearchTeamCotMessage.ACTION_JOIN_DECLINE));
        return messages;
    }

    public List<SearchTeamCotMessage> getInvitesForMe() {
        List<SearchTeamCotMessage> filtered = new ArrayList<>();
        for (SearchTeamCotMessage invite : scanForMe(
                SearchTeamCotMessage.ACTION_INVITE)) {
            if (!isCancelled(SearchTeamCotMessage.ACTION_INVITE_CANCEL, invite))
                filtered.add(invite);
        }
        return dedupeByTarget(filtered);
    }

    public List<SearchTeamCotMessage> getInviteResponsesForLeader() {
        List<SearchTeamCotMessage> messages = new ArrayList<>();
        messages.addAll(scanForMe(SearchTeamCotMessage.ACTION_INVITE_ACCEPT));
        messages.addAll(scanForMe(SearchTeamCotMessage.ACTION_INVITE_DECLINE));
        return messages;
    }

    public List<SearchTeamCotMessage> getOutgoingInvites(String teamId) {
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        List<SearchTeamCotMessage> filtered = new ArrayList<>();
        for (SearchTeamCotMessage message : getMessages(
                SearchTeamCotMessage.ACTION_INVITE, "")) {
            if (teamId.equals(message.getTeamId())
                    && identity.getUid().equals(message.getSenderUid())
                    && !isCancelled(SearchTeamCotMessage.ACTION_INVITE_CANCEL,
                            message))
                filtered.add(message);
        }
        return dedupeByTarget(filtered);
    }

    public List<SearchTeamCotMessage> getOutgoingJoinRequests() {
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        List<SearchTeamCotMessage> filtered = new ArrayList<>();
        for (SearchTeamCotMessage message : getMessages(
                SearchTeamCotMessage.ACTION_JOIN_REQUEST, "")) {
            if (identity.getUid().equals(message.getSenderUid())
                    && !isCancelled(SearchTeamCotMessage.ACTION_JOIN_CANCEL,
                            message))
                filtered.add(message);
        }
        return dedupeByTeam(filtered);
    }

    private void publish(String action, String teamId, String teamName,
            String leaderUid, String leaderCallsign, String targetUid,
            String targetCallsign) {
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        SearchTeamCotMessage message = new SearchTeamCotMessage(
                "sartak-team-" + action + "-" + teamId + "-"
                        + identity.getUid() + "-" + System.currentTimeMillis(),
                action, teamId, teamName, leaderUid, leaderCallsign,
                identity.getUid(), identity.getCallsign(), targetUid,
                targetCallsign);
        directMessages.put(message.getUid(), message);
        addLocalMessageMarker(message);
        CotEvent event = createCotEvent(message);
        if (event != null) {
            CotMapComponent.getExternalDispatcher().dispatchToBroadcast(event);
            dispatchToTargetContact(event, targetUid, targetCallsign);
        }
    }

    private void addLocalMessageMarker(SearchTeamCotMessage message) {
        Marker marker = new Marker(getPublishPoint(), message.getUid());
        marker.setType("b-m-p-s-p-loc");
        marker.setTitle("SARtak " + message.getAction());
        marker.setMetaString("entry", "sartak");
        marker.setMetaString("sartak.kind", "team-cot-message");
        set(marker, "action", message.getAction());
        set(marker, "teamId", message.getTeamId());
        set(marker, "teamName", message.getTeamName());
        set(marker, "leaderUid", message.getLeaderUid());
        set(marker, "leaderCallsign", message.getLeaderCallsign());
        set(marker, "senderUid", message.getSenderUid());
        set(marker, "senderCallsign", message.getSenderCallsign());
        set(marker, "targetUid", message.getTargetUid());
        set(marker, "targetCallsign", message.getTargetCallsign());
        set(marker, "created", String.valueOf(System.currentTimeMillis()));
        marker.setMetaBoolean("archive", false);
        marker.setMetaBoolean("removable", true);
        ensureGroup().addItem(marker);
    }

    private List<SearchTeamCotMessage> getMessages(String action,
            String targetUid) {
        LinkedHashMap<String, SearchTeamCotMessage> messages =
                new LinkedHashMap<>();
        synchronized (directMessages) {
            for (SearchTeamCotMessage message : directMessages.values()) {
                if (action.equals(message.getAction()))
                    messages.put(message.getUid(), message);
            }
        }
        for (SearchTeamCotMessage message : scanMapMessages(action, targetUid))
            messages.put(message.getUid(), message);

        List<SearchTeamCotMessage> filtered = new ArrayList<>();
        for (SearchTeamCotMessage message : messages.values()) {
            if (targetUid != null && targetUid.length() > 0
                    && !targetUid.equals(message.getTargetUid()))
                continue;
            filtered.add(message);
        }
        return filtered;
    }

    private List<SearchTeamCotMessage> scanMapMessages(String action,
            String targetUid) {
        List<SearchTeamCotMessage> messages = new ArrayList<>();
        Collection<MapItem> items = mapView.getRootGroup().getItemsRecursive();
        if (items == null)
            return messages;
        for (MapItem item : items) {
            if (!(item instanceof Marker))
                continue;
            String itemAction = item.getMetaString(meta("action"), "");
            if (!action.equals(itemAction))
                continue;
            String itemTarget = item.getMetaString(meta("targetUid"), "");
            if (targetUid != null && targetUid.length() > 0
                    && !targetUid.equals(itemTarget))
                continue;
            messages.add(new SearchTeamCotMessage(item.getUID(), itemAction,
                    item.getMetaString(meta("teamId"), ""),
                    item.getMetaString(meta("teamName"), ""),
                    item.getMetaString(meta("leaderUid"), ""),
                    item.getMetaString(meta("leaderCallsign"), ""),
                    item.getMetaString(meta("senderUid"), ""),
                    item.getMetaString(meta("senderCallsign"), ""),
                    itemTarget,
                    item.getMetaString(meta("targetCallsign"), "")));
        }
        return messages;
    }

    private List<SearchTeamCotMessage> scanForMe(String action) {
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        List<SearchTeamCotMessage> filtered = new ArrayList<>();
        for (SearchTeamCotMessage message : getMessages(action, "")) {
            if (matchesMe(message, identity))
                filtered.add(message);
        }
        return filtered;
    }

    private boolean matchesMe(SearchTeamCotMessage message,
            IdentityManager.Identity identity) {
        String uid = identity.getUid() == null ? "" : identity.getUid();
        String callsign = identity.getCallsign() == null ? ""
                : identity.getCallsign();
        return uid.equals(message.getTargetUid())
                || callsign.equalsIgnoreCase(message.getTargetCallsign());
    }

    private boolean isCancelled(String cancelAction,
            SearchTeamCotMessage original) {
        for (SearchTeamCotMessage cancel : getMessages(cancelAction, "")) {
            if (isMatchingCancel(cancelAction, original, cancel))
                return true;
        }
        return false;
    }

    private boolean isMatchingCancel(String cancelAction,
            SearchTeamCotMessage original, SearchTeamCotMessage cancel) {
        if (!original.getTeamId().equals(cancel.getTeamId()))
            return false;
        if (SearchTeamCotMessage.ACTION_INVITE_CANCEL.equals(cancelAction)) {
            return sameMember(original.getTargetUid(),
                    original.getTargetCallsign(), cancel.getTargetUid(),
                    cancel.getTargetCallsign())
                    && sameMember(original.getSenderUid(),
                            original.getSenderCallsign(),
                            cancel.getSenderUid(),
                            cancel.getSenderCallsign());
        }
        if (SearchTeamCotMessage.ACTION_JOIN_CANCEL.equals(cancelAction)) {
            return sameMember(original.getSenderUid(),
                    original.getSenderCallsign(), cancel.getSenderUid(),
                    cancel.getSenderCallsign());
        }
        return false;
    }

    private boolean sameMember(String firstUid, String firstCallsign,
            String secondUid, String secondCallsign) {
        return (firstUid != null && firstUid.length() > 0
                && firstUid.equals(secondUid))
                || (firstCallsign != null && firstCallsign.length() > 0
                && firstCallsign.equalsIgnoreCase(secondCallsign));
    }

    private CotEvent createCotEvent(SearchTeamCotMessage message) {
        CoordinatedTime now = new CoordinatedTime();
        CotDetail root = new CotDetail();
        CotDetail detail = new CotDetail(SearchTeamCotDetailHandler.DETAIL_NAME);
        detail.setAttribute("action", message.getAction());
        detail.setAttribute("teamId", message.getTeamId());
        detail.setAttribute("teamName", message.getTeamName());
        detail.setAttribute("leaderUid", message.getLeaderUid());
        detail.setAttribute("leaderCallsign", message.getLeaderCallsign());
        detail.setAttribute("senderUid", message.getSenderUid());
        detail.setAttribute("senderCallsign", message.getSenderCallsign());
        detail.setAttribute("targetUid", message.getTargetUid());
        detail.setAttribute("targetCallsign", message.getTargetCallsign());
        detail.setAttribute("created", String.valueOf(System.currentTimeMillis()));
        root.addChild(detail);

        CotEvent event = new CotEvent();
        event.setUID(message.getUid());
        event.setType("b-m-p-s-p-loc");
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
            // ATAK comms can still be initialising when the plugin object is
            // created. Local marker fallback keeps the UI usable until ATAK
            // brings the CoT pipeline up.
        }
    }

    private boolean receiveCotEvent(CotEvent event) {
        SearchTeamCotMessage message = fromCotEvent(event);
        if (message == null)
            return false;
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        if (!identity.getUid().equals(message.getSenderUid()))
            directMessages.put(message.getUid(), message);
        return true;
    }

    private void dispatchToTargetContact(CotEvent event, String targetUid,
            String targetCallsign) {
        Contact contact = findContact(targetUid, targetCallsign);
        if (contact == null)
            return;
        try {
            CotMapComponent.getExternalDispatcher().dispatchToContact(event,
                    contact);
        } catch (Exception ignored) {
            // Broadcast dispatch above remains the fallback path if ATAK cannot
            // directly address the contact.
        }
    }

    private Contact findContact(String uid, String callsign) {
        try {
            Contacts contacts = Contacts.getInstance();
            if (uid != null && uid.length() > 0) {
                Contact byUid = contacts.getContactByUuid(uid);
                if (byUid != null)
                    return byUid;
            }
            if (callsign != null && callsign.length() > 0)
                return contacts.getFirstContactWithCallsign(callsign);
        } catch (Exception ignored) {
        }
        return null;
    }

    private SearchTeamCotMessage fromCotEvent(CotEvent event) {
        if (event == null)
            return null;
        CotDetail detail = event.findDetail(
                SearchTeamCotDetailHandler.DETAIL_NAME);
        if (detail == null)
            return null;
        return new SearchTeamCotMessage(event.getUID(),
                value(detail, "action"), value(detail, "teamId"),
                value(detail, "teamName"), value(detail, "leaderUid"),
                value(detail, "leaderCallsign"), value(detail, "senderUid"),
                value(detail, "senderCallsign"), value(detail, "targetUid"),
                value(detail, "targetCallsign"));
    }

    private String value(CotDetail detail, String key) {
        String value = detail.getAttribute(key);
        return value == null ? "" : value;
    }

    private GeoPoint getPublishPoint() {
        AtakLocationStatus.Snapshot snapshot = AtakLocationStatus.from(mapView);
        if (snapshot.isAvailable())
            return snapshot.getPoint();
        return mapView.getSelfMarker() != null
                ? mapView.getSelfMarker().getPoint()
                : new GeoPoint(0.0, 0.0);
    }

    private void set(Marker marker, String key, String value) {
        marker.setMetaString(meta(key), value == null ? "" : value);
    }

    private String meta(String key) {
        return SearchTeamCotDetailHandler.META_PREFIX + key;
    }

    private MapGroup ensureGroup() {
        if (messageGroup != null)
            return messageGroup;
        messageGroup = mapView.getRootGroup().findMapGroup(GROUP_NAME);
        if (messageGroup == null)
            messageGroup = mapView.getRootGroup().addGroup(GROUP_NAME);
        messageGroup.setVisible(false);
        messageGroup.setMetaBoolean("addToObjList", false);
        return messageGroup;
    }

    private List<SearchTeamCotMessage> dedupeByTarget(
            List<SearchTeamCotMessage> messages) {
        LinkedHashMap<String, SearchTeamCotMessage> byTarget =
                new LinkedHashMap<>();
        for (SearchTeamCotMessage message : messages) {
            String key = message.getTargetUid() == null
                    || message.getTargetUid().length() == 0
                            ? message.getTargetCallsign()
                            : message.getTargetUid();
            byTarget.put(key, message);
        }
        return new ArrayList<>(byTarget.values());
    }

    private List<SearchTeamCotMessage> dedupeBySender(
            List<SearchTeamCotMessage> messages) {
        LinkedHashMap<String, SearchTeamCotMessage> bySender =
                new LinkedHashMap<>();
        for (SearchTeamCotMessage message : messages) {
            String key = message.getSenderUid() == null
                    || message.getSenderUid().length() == 0
                            ? message.getSenderCallsign()
                            : message.getSenderUid();
            bySender.put(key, message);
        }
        return new ArrayList<>(bySender.values());
    }

    private List<SearchTeamCotMessage> dedupeByTeam(
            List<SearchTeamCotMessage> messages) {
        LinkedHashMap<String, SearchTeamCotMessage> byTeam =
                new LinkedHashMap<>();
        for (SearchTeamCotMessage message : messages)
            byTeam.put(message.getTeamId(), message);
        return new ArrayList<>(byTeam.values());
    }
}
