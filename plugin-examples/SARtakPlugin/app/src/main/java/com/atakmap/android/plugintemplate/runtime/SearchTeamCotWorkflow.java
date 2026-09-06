package com.atakmap.android.plugintemplate.runtime;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.maps.MapData;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.comms.CotServiceRemote;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SearchTeamCotWorkflow {

    private static final String ATAK_CIV_PACKAGE = "com.atakmap.app.civ";
    private static final String GROUP_NAME = "SARtak Team CoT";
    private static final long ADVERTISE_INTERVAL_MS = 10000L;
    private static final long PRESENCE_INTERVAL_MS = 10000L;
    private static final long STYLE_INTERVAL_MS = 45000L;
    private static final long PENDING_REPUBLISH_INTERVAL_MS = 5000L;
    private static final long ADVERTISEMENT_MAX_AGE_MS = 30000L;
    private static final long PRESENCE_MAX_AGE_MS = 45000L;
    private static final long PENDING_MESSAGE_MAX_AGE_MS = 60000L;
    private static final long TEAM_REMOVED_MAX_AGE_MS = 10 * 60 * 1000L;

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
    private long lastPresenceTime;
    private long lastStyleTime;
    private long lastPendingRepublishTime;

    public SearchTeamCotWorkflow(MapView mapView,
            IdentityManager identityManager) {
        this.mapView = mapView;
        this.identityManager = identityManager;
        registerDirectCotProcessor();
        purgeLegacyMessageMarkers();
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

    public void removeTeam(String teamId, String teamName) {
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        publish(SearchTeamCotMessage.ACTION_TEAM_REMOVED, teamId, teamName,
                identity.getUid(), identity.getCallsign(), "", "");
        clearLocalMessagesForTeam(teamId);
    }

    public void publishPresenceIfDue(String teamId, String teamName,
            String leaderUid, String leaderCallsign, String teamColorName,
            int teamColorArgb, String memberColorName, int memberColorArgb,
            String memberRole) {
        long now = System.currentTimeMillis();
        if (now - lastPresenceTime < PRESENCE_INTERVAL_MS)
            return;
        boolean includeStyle = now - lastStyleTime >= STYLE_INTERVAL_MS;
        publishPresence(teamId, teamName, leaderUid, leaderCallsign,
                includeStyle ? teamColorName : "",
                includeStyle ? teamColorArgb : 0,
                includeStyle ? memberColorName : "",
                includeStyle ? memberColorArgb : 0,
                includeStyle ? memberRole : "");
        if (includeStyle)
            lastStyleTime = now;
    }

    public void publishPresence(String teamId, String teamName,
            String leaderUid, String leaderCallsign, String teamColorName,
            int teamColorArgb, String memberColorName, int memberColorArgb,
            String memberRole) {
        publish(SearchTeamCotMessage.ACTION_PRESENCE, safe(teamId),
                safe(teamName), safe(leaderUid), safe(leaderCallsign),
                "", "", teamColorName, teamColorArgb, memberColorName,
                memberColorArgb, memberRole);
        lastPresenceTime = System.currentTimeMillis();
        if (teamColorArgb != 0 || memberColorArgb != 0
                || safe(teamColorName).length() > 0
                || safe(memberColorName).length() > 0)
            lastStyleTime = lastPresenceTime;
    }

    public void republishPendingMessagesIfDue() {
        long now = System.currentTimeMillis();
        if (now - lastPendingRepublishTime < PENDING_REPUBLISH_INTERVAL_MS)
            return;
        lastPendingRepublishTime = now;

        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        List<SearchTeamCotMessage> toRepublish = new ArrayList<>();
        synchronized (directMessages) {
            for (SearchTeamCotMessage message : directMessages.values()) {
                if (!identity.getUid().equals(message.getSenderUid()))
                    continue;
                if (SearchTeamCotMessage.ACTION_JOIN_REQUEST.equals(
                        message.getAction())
                        || SearchTeamCotMessage.ACTION_INVITE.equals(
                                message.getAction())) {
                    toRepublish.add(message);
                }
            }
        }
        for (SearchTeamCotMessage message : toRepublish) {
            if (SearchTeamCotMessage.ACTION_JOIN_REQUEST.equals(
                    message.getAction())
                    && isCancelled(SearchTeamCotMessage.ACTION_JOIN_CANCEL,
                            message))
                continue;
            if (SearchTeamCotMessage.ACTION_INVITE.equals(message.getAction())
                    && isCancelled(SearchTeamCotMessage.ACTION_INVITE_CANCEL,
                            message))
                continue;
            dispatch(message);
        }
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
        List<SearchTeamCotMessage> active = new ArrayList<>();
        for (SearchTeamCotMessage message : dedupeByTeam(filtered)) {
            if (!isTeamRemoved(message))
                active.add(message);
        }
        return active;
    }

    public List<SearchTeamCotMessage> getJoinRequests(String teamId) {
        List<SearchTeamCotMessage> filtered = new ArrayList<>();
        for (SearchTeamCotMessage message : getMessages(
                SearchTeamCotMessage.ACTION_JOIN_REQUEST, "")) {
            if (teamId.equals(message.getTeamId())
                    && !isCancelled(SearchTeamCotMessage.ACTION_JOIN_CANCEL,
                            message)
                    && !isJoinRequestResolved(message))
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
                if (!isInviteResolved(invite))
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
                            message)
                    && !isInviteResolved(message))
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
                            message)
                    && !isJoinRequestResolved(message))
                filtered.add(message);
        }
        return dedupeByTeam(filtered);
    }

    public List<SearchTeamCotMessage> getPresenceForTeam(String teamId) {
        List<SearchTeamCotMessage> filtered = new ArrayList<>();
        if (teamId == null || teamId.length() == 0)
            return filtered;
        for (SearchTeamCotMessage message : getMessages(
                SearchTeamCotMessage.ACTION_PRESENCE, "")) {
            if (teamId.equals(message.getTeamId()))
                filtered.add(message);
        }
        return dedupeBySender(filtered);
    }

    public long getLastPresenceSentTime() {
        return lastPresenceTime;
    }

    public long getLatestPresenceReceivedTime(String teamId) {
        long latest = 0L;
        for (SearchTeamCotMessage message : getPresenceForTeam(teamId)) {
            IdentityManager.Identity identity = identityManager
                    .getCurrentIdentity();
            if (identity != null && identity.getUid().equals(
                    message.getSenderUid()))
                continue;
            latest = Math.max(latest, message.getCreated());
        }
        return latest;
    }

    private void publish(String action, String teamId, String teamName,
            String leaderUid, String leaderCallsign, String targetUid,
            String targetCallsign) {
        publish(action, teamId, teamName, leaderUid, leaderCallsign,
                targetUid, targetCallsign, "", 0, "", 0, "");
    }

    private void publish(String action, String teamId, String teamName,
            String leaderUid, String leaderCallsign, String targetUid,
            String targetCallsign, String teamColorName, int teamColorArgb,
            String memberColorName, int memberColorArgb, String memberRole) {
        IdentityManager.Identity identity = identityManager.getCurrentIdentity();
        long created = System.currentTimeMillis();
        SearchTeamCotMessage message = new SearchTeamCotMessage(
                "sartak-team-" + action + "-" + teamId + "-"
                        + identity.getUid() + "-" + created,
                action, teamId, teamName, leaderUid, leaderCallsign,
                identity.getUid(), identity.getCallsign(), targetUid,
                targetCallsign, created, teamColorName, teamColorArgb,
                memberColorName, memberColorArgb, memberRole);
        directMessages.put(message.getUid(), message);
        dispatch(message);
    }

    private void dispatch(SearchTeamCotMessage message) {
        CotEvent event = createCotEvent(message);
        if (event != null) {
            CotMapComponent.getExternalDispatcher().dispatchToBroadcast(event);
            dispatchToTargetContact(event, message.getTargetUid(),
                    message.getTargetCallsign());
        }
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
            if (isExpired(message))
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
            String created = item.getMetaString(meta("created"), "");
            if (isExpired(itemAction, created, item.getUID()))
                continue;
            messages.add(new SearchTeamCotMessage(item.getUID(), itemAction,
                    item.getMetaString(meta("teamId"), ""),
                    item.getMetaString(meta("teamName"), ""),
                    item.getMetaString(meta("leaderUid"), ""),
                    item.getMetaString(meta("leaderCallsign"), ""),
                    item.getMetaString(meta("senderUid"), ""),
                    item.getMetaString(meta("senderCallsign"), ""),
                    itemTarget,
                    item.getMetaString(meta("targetCallsign"), ""),
                    parseTimestamp(created),
                    item.getMetaString(meta("teamColorName"), ""),
                    parseColor(item.getMetaString(meta("teamColorArgb"), "")),
                    item.getMetaString(meta("memberColorName"), ""),
                    parseColor(item.getMetaString(meta("memberColorArgb"), "")),
                    item.getMetaString(meta("memberRole"), "")));
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

    private boolean isJoinRequestResolved(SearchTeamCotMessage request) {
        return hasMatchingJoinResponse(SearchTeamCotMessage.ACTION_JOIN_ACCEPT,
                request)
                || hasMatchingJoinResponse(
                        SearchTeamCotMessage.ACTION_JOIN_DECLINE, request);
    }

    private boolean hasMatchingJoinResponse(String responseAction,
            SearchTeamCotMessage request) {
        for (SearchTeamCotMessage response : getMessages(responseAction, "")) {
            if (request.getTeamId().equals(response.getTeamId())
                    && sameMember(request.getSenderUid(),
                            request.getSenderCallsign(),
                            response.getTargetUid(),
                            response.getTargetCallsign())
                    && sameMember(request.getLeaderUid(),
                            request.getLeaderCallsign(),
                            response.getSenderUid(),
                            response.getSenderCallsign()))
                return true;
        }
        return false;
    }

    private boolean isInviteResolved(SearchTeamCotMessage invite) {
        return hasMatchingInviteResponse(
                SearchTeamCotMessage.ACTION_INVITE_ACCEPT, invite)
                || hasMatchingInviteResponse(
                        SearchTeamCotMessage.ACTION_INVITE_DECLINE, invite);
    }

    private boolean hasMatchingInviteResponse(String responseAction,
            SearchTeamCotMessage invite) {
        for (SearchTeamCotMessage response : getMessages(responseAction, "")) {
            if (invite.getTeamId().equals(response.getTeamId())
                    && sameMember(invite.getTargetUid(),
                            invite.getTargetCallsign(),
                            response.getSenderUid(),
                            response.getSenderCallsign())
                    && sameMember(invite.getSenderUid(),
                            invite.getSenderCallsign(),
                            response.getTargetUid(),
                            response.getTargetCallsign()))
                return true;
        }
        return false;
    }

    private boolean isTeamRemoved(SearchTeamCotMessage team) {
        for (SearchTeamCotMessage removed : getMessages(
                SearchTeamCotMessage.ACTION_TEAM_REMOVED, "")) {
            if (team.getTeamId().equals(removed.getTeamId())
                    && sameMember(team.getLeaderUid(),
                            team.getLeaderCallsign(),
                            removed.getLeaderUid(),
                            removed.getLeaderCallsign()))
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
        detail.setAttribute("messageUid", message.getUid());
        detail.setAttribute("action", message.getAction());
        detail.setAttribute("teamId", message.getTeamId());
        detail.setAttribute("teamName", message.getTeamName());
        detail.setAttribute("leaderUid", message.getLeaderUid());
        detail.setAttribute("leaderCallsign", message.getLeaderCallsign());
        detail.setAttribute("senderUid", message.getSenderUid());
        detail.setAttribute("senderCallsign", message.getSenderCallsign());
        detail.setAttribute("targetUid", message.getTargetUid());
        detail.setAttribute("targetCallsign", message.getTargetCallsign());
        detail.setAttribute("teamColorName", message.getTeamColorName());
        detail.setAttribute("teamColorArgb", String.valueOf(
                message.getTeamColorArgb()));
        detail.setAttribute("memberColorName", message.getMemberColorName());
        detail.setAttribute("memberColorArgb", String.valueOf(
                message.getMemberColorArgb()));
        detail.setAttribute("memberRole", message.getMemberRole());
        detail.setAttribute("created", String.valueOf(message.getCreated()));
        root.addChild(detail);
        addStandardContactDetails(root, message);

        CotEvent event = new CotEvent();
        // Use the sender device UID as the CoT event UID. FreeTAKServer treats
        // new CoT UIDs as trackable contacts/connections, so putting SARtak's
        // per-message UID here creates ghost "devices" on the server. The
        // stable ATAK device remains the contact; the SARtak message id lives
        // inside the custom detail instead.
        event.setUID(message.getSenderUid());
        event.setType(getSelfCotType());
        event.setTime(now);
        event.setStart(now);
        event.setStale(now.addMinutes(5));
        event.setHow(CotEvent.HOW_MACHINE_GENERATED);
        event.setPoint(CotPublishPoint.forSnapshot(
                AtakLocationStatus.from(mapView)));
        event.setDetail(root);
        return event;
    }

    private void addStandardContactDetails(CotDetail root,
            SearchTeamCotMessage message) {
        String callsign = firstNonEmpty(message.getSenderCallsign(),
                getSelfCallsign());
        if (callsign.length() > 0) {
            CotDetail contact = new CotDetail("contact");
            contact.setAttribute("callsign", callsign);
            root.addChild(contact);

            // ATAK/WinTAK use this Droid value on normal SA/PPLI messages when
            // presenting contacts in team/contact views.
            CotDetail uid = new CotDetail("uid");
            uid.setAttribute("Droid", callsign);
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
        if (isExpired(message))
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
        String messageUid = value(detail, "messageUid");
        return new SearchTeamCotMessage(messageUid.length() == 0
                ? event.getUID() : messageUid,
                value(detail, "action"), value(detail, "teamId"),
                value(detail, "teamName"), value(detail, "leaderUid"),
                value(detail, "leaderCallsign"), value(detail, "senderUid"),
                value(detail, "senderCallsign"), value(detail, "targetUid"),
                value(detail, "targetCallsign"),
                parseTimestamp(value(detail, "created")),
                value(detail, "teamColorName"),
                parseColor(value(detail, "teamColorArgb")),
                value(detail, "memberColorName"),
                parseColor(value(detail, "memberColorArgb")),
                value(detail, "memberRole"));
    }

    private String value(CotDetail detail, String key) {
        String value = detail.getAttribute(key);
        return value == null ? "" : value;
    }

    private boolean isExpired(SearchTeamCotMessage message) {
        return isExpired(message.getAction(), String.valueOf(
                message.getCreated()), message.getUid());
    }

    private boolean isExpired(String action, String createdValue, String uid) {
        long created = parseTimestamp(createdValue);
        if (created <= 0L)
            created = parseTimestampFromUid(uid);
        if (created <= 0L)
            return false;
        long age = System.currentTimeMillis() - created;
        if (SearchTeamCotMessage.ACTION_ADVERTISE.equals(action))
            return age > ADVERTISEMENT_MAX_AGE_MS;
        if (SearchTeamCotMessage.ACTION_PRESENCE.equals(action))
            return age > PRESENCE_MAX_AGE_MS;
        if (SearchTeamCotMessage.ACTION_JOIN_REQUEST.equals(action)
                || SearchTeamCotMessage.ACTION_INVITE.equals(action))
            return age > PENDING_MESSAGE_MAX_AGE_MS;
        if (SearchTeamCotMessage.ACTION_TEAM_REMOVED.equals(action))
            return age > TEAM_REMOVED_MAX_AGE_MS;
        return false;
    }

    private long parseTimestamp(String value) {
        try {
            return value == null || value.length() == 0
                    ? 0L : Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private int parseColor(String value) {
        try {
            return value == null || value.length() == 0
                    ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private long parseTimestampFromUid(String uid) {
        if (uid == null)
            return 0L;
        int index = uid.lastIndexOf('-');
        if (index < 0 || index + 1 >= uid.length())
            return 0L;
        return parseTimestamp(uid.substring(index + 1));
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

        MapData data = mapView == null ? null : mapView.getMapData();
        if (data != null) {
            String fromMapData = firstNonEmpty(
                    data.getString("__groupRole", ""),
                    data.getString("atakRoleType", ""),
                    data.getString("atakRole", ""),
                    data.getString("teamRole", ""),
                    data.getString("role", ""));
            if (fromMapData.length() > 0)
                return fromMapData;
        }

        String fromPrefs = firstNonEmpty(getPreferenceValue("atakRoleType"),
                getPreferenceValue("atakRole"),
                getPreferenceValue("teamRole"),
                getPreferenceValue("__groupRole"),
                getPreferenceValue("role"));
        return fromPrefs.length() == 0 ? "Team Member" : fromPrefs;
    }

    private String getPreferenceValue(String key) {
        Context context = mapView == null ? null : mapView.getContext();
        String value = getPreferenceValue(context, key);
        if (value.length() > 0)
            return value;
        Context applicationContext = context == null ? null
                : context.getApplicationContext();
        if (applicationContext != null && applicationContext != context) {
            value = getPreferenceValue(applicationContext, key);
            if (value.length() > 0)
                return value;
        }
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
        if (context == null || key == null || key.length() == 0)
            return "";
        try {
            SharedPreferences preferences = PreferenceManager
                    .getDefaultSharedPreferences(context);
            return safe(preferences.getString(key, ""));
        } catch (Exception ignored) {
            return "";
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

    private void clearLocalMessagesForTeam(String teamId) {
        if (teamId == null || teamId.length() == 0)
            return;
        MapGroup group = ensureGroup();
        Collection<MapItem> items = group.getItems();
        if (items == null)
            return;
        List<MapItem> toRemove = new ArrayList<>();
        for (MapItem item : items) {
            if (teamId.equals(item.getMetaString(meta("teamId"), "")))
                toRemove.add(item);
        }
        for (MapItem item : toRemove)
            group.removeItem(item);
    }

    private void purgeLegacyMessageMarkers() {
        if (mapView == null || mapView.getRootGroup() == null)
            return;
        Collection<MapItem> items = mapView.getRootGroup().getItemsRecursive();
        if (items == null)
            return;
        List<MapItem> toRemove = new ArrayList<>();
        for (MapItem item : items) {
            String uid = item.getUID() == null ? "" : item.getUID();
            String title = item instanceof Marker
                    && ((Marker) item).getTitle() != null
                            ? ((Marker) item).getTitle() : "";
            if (uid.startsWith("sartak-team-")
                    || title.startsWith("SARtak ")
                    || "team-cot-message".equals(item.getMetaString(
                            "sartak.kind", ""))) {
                toRemove.add(item);
            }
        }
        for (MapItem item : toRemove)
            removeFromMap(item);
    }

    private void removeFromMap(MapItem item) {
        try {
            Method method = item.getClass().getMethod("removeFromGroup");
            method.invoke(item);
        } catch (Exception ignored) {
            try {
                ensureGroup().removeItem(item);
            } catch (Exception ignoredAgain) {
            }
        }
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
