package com.atakmap.android.plugintemplate.grid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.atakmap.android.plugintemplate.runtime.AtakLocationStatus;
import com.atakmap.android.plugintemplate.runtime.AtakTeamContactDataSource;
import com.atakmap.android.plugintemplate.runtime.DittoDeviceSnapshot;
import com.atakmap.android.plugintemplate.runtime.SearchTeamCotMessage;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.UTMPoint;

import com.atakmap.android.plugintemplate.database.SearcherRepository;

public class SearchPartyAssignmentManager {

    public static final long STALE_AFTER_MS = 30000L;
    public static final long DISCONNECTED_AFTER_MS = 60000L;

    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_BLUE = 0xFF4AA3FF;
    private static final int COLOR_GREEN = 0xFF42C36A;
    private static final int COLOR_YELLOW = 0xFFD8B64C;
    private static final int COLOR_RED = 0xFFD8544C;
    private static final int COLOR_PURPLE = 0xFFB47CFF;
    private static final int[] SEARCHER_COLORS = new int[] {
            COLOR_BLUE, COLOR_GREEN, COLOR_YELLOW, COLOR_RED, COLOR_PURPLE
    };
    private static final String[] SEARCHER_COLOR_NAMES = new String[] {
            "Blue", "Green", "Yellow", "Red", "Purple"
    };

    private String teamId = "";
    private String teamName = "";
    private String teamColorName = "White";
    private int teamColorArgb = COLOR_WHITE;
    private String selfMemberId = "TL-A-001";
    private final List<SearchTeamMember> members = new ArrayList<>();
    private int lastAtakMatchedMembers;
    private boolean teamCreated;

    private final SearcherRepository searcherRepository;


    public SearchPartyAssignmentManager(SearcherRepository searcherRepository) {
        this.searcherRepository = searcherRepository;
        members.add(new SearchTeamMember("TL-A-001", "Alpha Lead",
                SearchTeamMember.TeamRole.TEAM_LEADER, "White", COLOR_WHITE,
                SearchTeamMember.MembershipStatus.ACTIVE_MEMBER,
                SearchTeamMember.ConnectionStatus.CONNECTED, 1,
                -27.4705, 153.0260, 0.0,
                "No GPS Signal", "No GPS Signal", "No GPS Signal",
                "No GPS Signal", "No GPS Signal", "No GPS Signal"));
    }

    public String getTeamId() {
        return teamId;
    }

    public String getTeamName() {
        return teamName;
    }

    public String getTeamColorName() {
        return teamColorName;
    }

    public int getTeamColorArgb() {
        return teamColorArgb;
    }

    public String getSelfMemberId() {
        return selfMemberId;
    }

    public String getLeaderUid() {
        SearchTeamMember leader = getLeaderMember();
        return leader == null ? "" : leader.getUniqueId();
    }

    public String getLeaderCallsign() {
        SearchTeamMember leader = getLeaderMember();
        return leader == null ? "" : leader.getCallsign();
    }

    public int getLastAtakMatchedMembers() {
        return lastAtakMatchedMembers;
    }

    public boolean isTeamCreated() {
        return teamCreated;
    }

    public void setTeamCreated(boolean teamCreated) {
        this.teamCreated = teamCreated;
    }

    public List<SearchTeamMember> getVisibleMembers() {
        if (!teamCreated)
            return Collections.emptyList();
        List<SearchTeamMember> visibleMembers = new ArrayList<>();
        for (SearchTeamMember member : members) {
            if (member.getMembershipStatus()
                    != SearchTeamMember.MembershipStatus.REMOVED)
                visibleMembers.add(member);
        }
        return Collections.unmodifiableList(visibleMembers);
    }

    public List<SearchTeamMember> getLaneMembers() {
        if (!teamCreated)
            return Collections.emptyList();
        List<SearchTeamMember> laneMembers = new ArrayList<>();
        for (SearchTeamMember member : members) {
            if (member.contributesLane())
                laneMembers.add(member);
        }
        return laneMembers;
    }

    public SearchTeamMember findMemberById(String uniqueId) {
        for (SearchTeamMember member : members) {
            if (member.getUniqueId().equals(uniqueId)
                    && member.getMembershipStatus()
                    != SearchTeamMember.MembershipStatus.REMOVED)
                return member;
        }
        return null;
    }

    public int getTeamSize() {
        return getLaneMemberCount();
    }

    public int getVisibleMemberCount() {
        return getVisibleMembers().size();
    }

    public int getConnectedMemberCount() {
        int count = 0;
        for (SearchTeamMember member : getVisibleMembers()) {
            if (member.getConnectionStatus()
                    == SearchTeamMember.ConnectionStatus.CONNECTED)
                count++;
        }
        return count;
    }

    public int getStaleMemberCount() {
        int count = 0;
        for (SearchTeamMember member : getVisibleMembers()) {
            if (member.getConnectionStatus()
                    != SearchTeamMember.ConnectionStatus.CONNECTED)
                count++;
        }
        return count;
    }

    public int getLaneMemberCount() {
        return Math.max(1, getLaneMembers().size());
    }

    public void setTeamDetails(String teamName, String teamId) {
        if (teamName != null && teamName.trim().length() > 0)
            this.teamName = teamName.trim();
        if (teamId != null && teamId.trim().length() > 0)
            this.teamId = teamId.trim();
        applyTeamStyle();
    }

    public void createTeam(String teamName, String teamId) {
        setTeamDetails(teamName, teamId);
        teamCreated = true;
        SearchTeamMember self = findMemberById(selfMemberId);
        if (self != null)
            self.setRole(SearchTeamMember.TeamRole.TEAM_LEADER);
        applyTeamStyle();
    }

    public void joinTeam(String teamName, String teamId) {
        setTeamDetails(teamName, teamId);
        teamCreated = true;
        SearchTeamMember self = findMemberById(selfMemberId);
        if (self != null)
            self.setRole(SearchTeamMember.TeamRole.SEARCHER);
        applyTeamStyle();
    }

    public void clearTeam() {
        teamId = "";
        teamName = "";
        teamCreated = false;
        teamColorName = "White";
        teamColorArgb = COLOR_WHITE;
        for (SearchTeamMember member : members) {
            if (member.getUniqueId().equals(selfMemberId)) {
                member.setRole(SearchTeamMember.TeamRole.TEAM_LEADER);
                member.setLaneNumber(1);
                member.setMembershipStatus(
                        SearchTeamMember.MembershipStatus.ACTIVE_MEMBER);
            } else {
                member.setMembershipStatus(
                        SearchTeamMember.MembershipStatus.REMOVED);
            }
        }
    }

    public void setSelfIdentity(String uid, String callsign) {
        if (uid == null || uid.trim().length() == 0)
            return;
        String trimmedUid = uid.trim();
        String trimmedCallsign = callsign == null || callsign.trim().length() == 0
                ? "Team Leader" : callsign.trim();
        if (trimmedUid.equals(selfMemberId)) {
            return;
        }

        SearchTeamMember existingSelf = findMemberById(selfMemberId);
        if (existingSelf != null)
            existingSelf.setMembershipStatus(
                    SearchTeamMember.MembershipStatus.REMOVED);
        selfMemberId = trimmedUid;
        SearchTeamMember self = findAnyMemberById(trimmedUid);
        if (self == null) {
            members.add(new SearchTeamMember(trimmedUid, trimmedCallsign,
                    SearchTeamMember.TeamRole.TEAM_LEADER, "White",
                    COLOR_WHITE,
                    SearchTeamMember.MembershipStatus.ACTIVE_MEMBER,
                    SearchTeamMember.ConnectionStatus.STALE, 1,
                    -27.4705, 153.0260, 0.0,
                    "No GPS Signal", "No GPS Signal", "No GPS Signal",
                    "No GPS Signal", "No GPS Signal", "No GPS Signal"));
        } else {
            self.setMembershipStatus(
                    SearchTeamMember.MembershipStatus.ACTIVE_MEMBER);
        }
        applyTeamStyle();
    }

    public SearchTeamMember addTeamMember(
            AtakTeamContactDataSource.ContactSnapshot contact,
            GeoPoint selfPoint, GridCoordinateConverter converter) {
        if (contact == null || contact.getUid() == null
                || contact.getUid().trim().length() == 0)
            return null;

        String uid = contact.getUid().trim();
        SearchTeamMember existing = findAnyMemberById(uid);
        if (existing != null) {
            existing.setMembershipStatus(
                    SearchTeamMember.MembershipStatus.ACTIVE_MEMBER);
            existing.setAtakGroupName(contact.getAtakGroupName());
            updateMemberFromContact(existing, contact, selfPoint, converter);
            applyMemberStyle(existing);
            return existing;
        }

        GeoPoint contactPoint = contact.getPoint();
        boolean hasLocation = contactPoint != null && contactPoint.isValid();
        int colorIndex = Math.max(0, getLaneMemberCount() - 1)
                % SEARCHER_COLORS.length;
        String label = contact.getCallsign() == null
                || contact.getCallsign().trim().length() == 0
                ? uid : contact.getCallsign().trim();
        SearchTeamMember member = new SearchTeamMember(uid, label,
                SearchTeamMember.TeamRole.SEARCHER,
                SEARCHER_COLOR_NAMES[colorIndex], SEARCHER_COLORS[colorIndex],
                SearchTeamMember.MembershipStatus.ACTIVE_MEMBER,
                hasLocation ? SearchTeamMember.ConnectionStatus.CONNECTED
                        : SearchTeamMember.ConnectionStatus.STALE,
                getLaneMemberCount() + 1,
                hasLocation ? contactPoint.getLatitude() : 0.0,
                hasLocation ? contactPoint.getLongitude() : 0.0,
                contact.getHeadingDegrees(), formatGeo(contact.getPoint()),
                formatAltitude(contact.getPoint()),
                hasLocation ? converter.cellIdForPoint(contactPoint)
                        : "No GPS Signal",
                formatLastPing(contact.getTimestamp()),
                formatDistanceFromSelf(selfPoint, contact.getPoint()),
                hasLocation ? "Not measured" : "No GPS Signal");
        member.setLiveAtakContact(hasLocation);
        member.setAtakGroupName(contact.getAtakGroupName());
        applyMemberStyle(member);
        members.add(member);
        return member;
    }

    public SearchTeamMember addConfirmedRosterMember(String uid,
            String callsign, SearchTeamMember.TeamRole role) {
        if (uid == null || uid.trim().length() == 0)
            return null;

        String trimmedUid = uid.trim();
        SearchTeamMember existing = findAnyMemberById(trimmedUid);
        if (existing != null) {
            existing.setMembershipStatus(
                    SearchTeamMember.MembershipStatus.ACTIVE_MEMBER);
            existing.setRole(role);
            if (!existing.hasLiveAtakContact())
                existing.markLocationUnavailable("ATAK contact pending");
            applyMemberStyle(existing);
            return existing;
        }

        int colorIndex = Math.max(0, getLaneMemberCount() - 1)
                % SEARCHER_COLORS.length;
        String label = callsign == null || callsign.trim().length() == 0
                ? trimmedUid : callsign.trim();
        String colorName = role == SearchTeamMember.TeamRole.TEAM_LEADER
                ? "White" : SEARCHER_COLOR_NAMES[colorIndex];
        int displayColor = role == SearchTeamMember.TeamRole.TEAM_LEADER
                ? COLOR_WHITE : SEARCHER_COLORS[colorIndex];
        SearchTeamMember member = new SearchTeamMember(trimmedUid, label, role,
                colorName, displayColor,
                SearchTeamMember.MembershipStatus.ACTIVE_MEMBER,
                SearchTeamMember.ConnectionStatus.STALE,
                getLaneMemberCount() + 1, 0.0, 0.0, 0.0,
                "ATAK contact pending", "ATAK contact pending",
                "ATAK contact pending", "ATAK contact pending",
                "ATAK contact pending", "ATAK contact pending");
        member.setLiveAtakContact(false);
        applyMemberStyle(member);
        members.add(member);
        return member;
    }

    public boolean removeTeamMember(String uniqueId) {
        SearchTeamMember member = findMemberById(uniqueId);
        if (member == null || member.getUniqueId().equals(selfMemberId))
            return false;
        member.setMembershipStatus(SearchTeamMember.MembershipStatus.REMOVED);
        return true;
    }

    public void updateSelfFromAtak(AtakLocationStatus.Snapshot snapshot,
            SearchGridCell selectedCell) {
        SearchTeamMember self = findMemberById(selfMemberId);
        if (self == null)
            return;

        self.markPresenceSeen("SARtak ping now");

        if (snapshot == null || !snapshot.isAvailable()) {
            self.markLocationUnavailable("No GPS Signal");
            self.setConnectionStatus(SearchTeamMember.ConnectionStatus.STALE);
            return;
        }

        GeoPoint point = snapshot.getPoint();
        self.setConnectionStatus(SearchTeamMember.ConnectionStatus.CONNECTED);
        self.updatePosition(point.getLatitude(), point.getLongitude(), 0.0,
                formatGeo(point), formatAltitude(point),
                selectedCell == null ? "No cell selected" : selectedCell.getId(),
                formatLastPing(snapshot.getTimestamp()), "You",
                self.getDistanceFromSearchLine());
    }

    public void updateFromAtakContacts(
            List<AtakTeamContactDataSource.ContactSnapshot> contacts,
            GeoPoint selfPoint, GridCoordinateConverter converter) {
        lastAtakMatchedMembers = 0;
        for (SearchTeamMember member : getVisibleMembers()) {
            if (member.getUniqueId().equals(selfMemberId))
                continue;

            AtakTeamContactDataSource.ContactSnapshot contact = findContact(
                    member, contacts);
            if (contact == null) {
                member.setLiveAtakContact(false);
                if (member.getConnectionStatus()
                        == SearchTeamMember.ConnectionStatus.CONNECTED)
                    member.setConnectionStatus(
                            SearchTeamMember.ConnectionStatus.STALE);
                continue;
            }

            lastAtakMatchedMembers++;
            updateMemberFromContact(member, contact, selfPoint, converter);
            searcherRepository.updateLastSeen(
                    contact.getUid(),
                    System.currentTimeMillis()
            );
        }
    }

    public void updateConnectionAges() {
        long now = System.currentTimeMillis();
        for (SearchTeamMember member : getVisibleMembers()) {
            if (member.getUniqueId().equals(selfMemberId))
                continue;
            long lastPresence = member.getLastPresenceTimestamp();
            if (lastPresence <= 0L)
                continue;
            long age = now - lastPresence;
            if (age > DISCONNECTED_AFTER_MS)
                member.setConnectionStatus(
                        SearchTeamMember.ConnectionStatus.DISCONNECTED);
            else if (age > STALE_AFTER_MS)
                member.setConnectionStatus(
                        SearchTeamMember.ConnectionStatus.RECONNECTING);
        }
    }

    public boolean updateFromPresence(
            List<SearchTeamCotMessage> presenceMessages,
            List<AtakTeamContactDataSource.ContactSnapshot> contacts,
            GeoPoint selfPoint, GridCoordinateConverter converter) {
        boolean changed = false;
        if (!teamCreated || presenceMessages == null)
            return false;

        for (SearchTeamCotMessage presence : presenceMessages) {
            if (!teamId.equals(presence.getTeamId()))
                continue;
            if (selfMemberId.equals(presence.getSenderUid()))
                continue;

            SearchTeamMember.TeamRole role = sameMember(
                    presence.getSenderUid(), presence.getSenderCallsign(),
                    presence.getLeaderUid(), presence.getLeaderCallsign())
                            ? SearchTeamMember.TeamRole.TEAM_LEADER
                            : SearchTeamMember.TeamRole.SEARCHER;
            AtakTeamContactDataSource.ContactSnapshot contact = findContact(
                    presence.getSenderUid(), presence.getSenderCallsign(),
                    contacts);
            SearchTeamMember member = contact == null
                    ? addConfirmedRosterMember(presence.getSenderUid(),
                            presence.getSenderCallsign(), role)
                    : addTeamMember(contact, selfPoint, converter);
            if (member == null)
                continue;
            member.setRole(role);
            if (presence.getTeamColorArgb() != 0)
                setTeamStyle(presence.getTeamColorName(),
                        presence.getTeamColorArgb());
            if (presence.getMemberColorArgb() != 0)
                member.setMarkerStyle(teamColorName, teamColorArgb,
                        presence.getMemberColorName(),
                        presence.getMemberColorArgb());
            else
                applyMemberStyle(member);
            member.markPresenceSeen(formatLastPing(presence.getCreated()));
            long now = System.currentTimeMillis();
            searcherRepository.insertOrUpdate(
                    presence.getSenderUid(),
                    presence.getSenderCallsign(),
                    "",          // device model unknown from CoT
                    now,         // first_seen — CONFLICT_REPLACE keeps original if already exists
                    now,         // last_seen always updated
                    false        // not self
            );
            changed = true;
        }
        return changed;
    }

    public boolean updateFromDittoDevices(
            List<DittoDeviceSnapshot> snapshots,
            GeoPoint selfPoint, GridCoordinateConverter converter) {
        boolean changed = false;
        if (!teamCreated || snapshots == null)
            return false;

        for (DittoDeviceSnapshot snapshot : snapshots) {
            if (!teamId.equals(snapshot.getTeamId()))
                continue;
            if (selfMemberId.equals(snapshot.getUid()))
                continue;

            SearchTeamMember.TeamRole role = sameMember(snapshot.getUid(),
                    snapshot.getCallsign(), snapshot.getLeaderUid(),
                    snapshot.getLeaderCallsign())
                            ? SearchTeamMember.TeamRole.TEAM_LEADER
                            : SearchTeamMember.TeamRole.SEARCHER;
            SearchTeamMember member = addConfirmedRosterMember(
                    snapshot.getUid(), snapshot.getCallsign(), role);
            if (member == null)
                continue;
            member.setRole(role);
            if (snapshot.getTeamColorArgb() != 0)
                setTeamStyle(snapshot.getTeamColorName(),
                        snapshot.getTeamColorArgb());
            if (snapshot.getMemberColorArgb() != 0)
                member.setMarkerStyle(teamColorName, teamColorArgb,
                        snapshot.getMemberColorName(),
                        snapshot.getMemberColorArgb());
            else
                applyMemberStyle(member);
            member.markPresenceSeen(formatLastPing(snapshot.getUpdatedAt()));
            if (snapshot.hasLocation()) {
                GeoPoint point = new GeoPoint(snapshot.getLatitude(),
                        snapshot.getLongitude(), snapshot.getAltitude());
                member.updatePosition(snapshot.getLatitude(),
                        snapshot.getLongitude(), snapshot.getHeading(),
                        formatGeo(point), formatAltitude(point),
                        snapshot.getGridCellId(), formatLastPing(snapshot
                                .getUpdatedAt()),
                        formatDistanceFromSelf(selfPoint, point),
                        member.getDistanceFromSearchLine());
                member.updateMovement(snapshot.getHeading(),
                        snapshot.isHeadingReliable(), snapshot.getSpeed());
            } else {
                member.markLocationUnavailable("Ditto location unavailable");
            }
            long now = System.currentTimeMillis();
            searcherRepository.insertOrUpdate(snapshot.getUid(),
                    snapshot.getCallsign(), "", now, now, false);
            changed = true;
        }
        return changed;
    }

    public void removeLastLaneMember() {
        for (int i = members.size() - 1; i >= 0; i--) {
            SearchTeamMember member = members.get(i);
            if (member.contributesLane()
                    && !member.getUniqueId().equals(selfMemberId)) {
                member.setMembershipStatus(
                        SearchTeamMember.MembershipStatus.REMOVED);
                return;
            }
        }
    }

    public void arrangeMembersForCell(SearchGridCell cell,
            GridCoordinateConverter converter, GeoPoint leaderPoint,
            double lineNorthing) {
        if (cell == null || leaderPoint == null || !leaderPoint.isValid())
            return;

        List<SearchTeamMember> laneMembers = getLaneMembers();
        int laneCount = Math.max(1, laneMembers.size());
        double laneWidth = (cell.getEast() - cell.getWest()) / laneCount;
        UTMPoint leaderUtm = UTMPoint.fromGeoPoint(leaderPoint);
        int leaderLaneIndex = clamp((int) Math.floor((leaderUtm.getEasting()
                - cell.getWest()) / laneWidth), 0, laneCount - 1);

        SearchTeamMember leader = findMemberById(selfMemberId);
        if (leader != null && leader.contributesLane()) {
            leader.setLaneNumber(leaderLaneIndex + 1);
            leader.updateMapPosition(leaderPoint.getLatitude(),
                    leaderPoint.getLongitude(), 0.0, cell.getId(), "You",
                    "Leader line");
        }

        List<Integer> availableLaneIndexes = new ArrayList<>();
        for (int i = 0; i < laneCount; i++) {
            if (i != leaderLaneIndex)
                availableLaneIndexes.add(i);
        }

        int laneCursor = 0;
        int variationCursor = 0;
        for (SearchTeamMember member : laneMembers) {
            if (member.getUniqueId().equals(selfMemberId))
                continue;

            int laneIndex = laneCursor < availableLaneIndexes.size()
                    ? availableLaneIndexes.get(laneCursor)
                    : laneCursor;
            laneCursor++;
            member.setLaneNumber(laneIndex + 1);
            if (!member.hasLiveAtakContact())
                continue;

            UTMPoint memberPoint = UTMPoint.fromGeoPoint(new GeoPoint(
                    member.getLatitude(), member.getLongitude()));
            member.updateMapPosition(member.getLatitude(),
                    member.getLongitude(), member.getHeadingDegrees(),
                    cell.getId(), formatDistance(distance(leaderUtm,
                            memberPoint)),
                    formatLineOffset(memberPoint.getNorthing()
                            - lineNorthing));
        }
    }

    public String describeAssignments(SearchGridCell cell) {
        if (cell == null)
            return "No cell selected";
        return SearchGridDisplayFormatter.formatCellCompact(cell)
                + " split into " + getLaneMemberCount()
                + " assigned lanes";
    }

    public String describeTeamRoster() {
        StringBuilder builder = new StringBuilder();
        builder.append(teamName).append(" | ").append(teamId).append("\n");
        for (SearchTeamMember member : members) {
            if (member.getMembershipStatus()
                    == SearchTeamMember.MembershipStatus.REMOVED)
                continue;
            builder.append(member.isTeamLeader() ? "Leader: " : "- ");
            builder.append(member.getCallsign())
                    .append(" (")
                    .append(member.getColorName())
                    .append(") ")
                    .append(member.getConnectionStatus());
            builder.append(" | ")
                    .append(member.getLaneLabel());
            builder.append(" | Team colour: ")
                    .append(member.getTeamColorName());
            builder.append("\nGPS: ")
                    .append(member.getGpsCoordinates())
                    .append(" | Alt: ")
                    .append(member.getAltitude())
                    .append("\nGrid: ")
                    .append(member.getCurrentGridCellDisplay())
                    .append(" | Last ping: ")
                    .append(member.getLastPing())
                    .append("\nDistance: ")
                    .append(member.getDistanceFromYou())
                    .append(" | Line: ")
                    .append(member.getDistanceFromSearchLine())
                    .append("\n");
        }
        return builder.toString().trim();
    }

    private SearchTeamMember findAnyMemberById(String uniqueId) {
        if (uniqueId == null)
            return null;
        for (SearchTeamMember member : members) {
            if (member.getUniqueId().equals(uniqueId))
                return member;
        }
        return null;
    }

    public SearchTeamMember getSelfMember() {
        return findMemberById(selfMemberId);
    }

    private void applyTeamStyle() {
        SearchTeamStyle.ColorChoice teamColor = SearchTeamStyle.teamColorFor(
                teamId, teamName, "");
        setTeamStyle(teamColor.getName(), teamColor.getArgb());
    }

    private void setTeamStyle(String colorName, int colorArgb) {
        if (colorArgb != 0)
            teamColorArgb = colorArgb;
        if (colorName != null && colorName.trim().length() > 0)
            teamColorName = colorName.trim();
        for (SearchTeamMember member : members)
            applyMemberStyle(member);
    }

    private void applyMemberStyle(SearchTeamMember member) {
        if (member == null)
            return;
        SearchTeamStyle.ColorChoice personal =
                SearchTeamStyle.memberColorFor(member.getUniqueId(),
                        member.getLaneNumber(), member.isTeamLeader());
        member.setMarkerStyle(teamColorName, teamColorArgb,
                personal.getName(), personal.getArgb());
    }

    private SearchTeamMember getLeaderMember() {
        for (SearchTeamMember member : members) {
            if (member.getMembershipStatus()
                    != SearchTeamMember.MembershipStatus.REMOVED
                    && member.isTeamLeader())
                return member;
        }
        return findMemberById(selfMemberId);
    }

    private AtakTeamContactDataSource.ContactSnapshot findContact(
            SearchTeamMember member,
            List<AtakTeamContactDataSource.ContactSnapshot> contacts) {
        return findContact(member.getUniqueId(), member.getCallsign(),
                contacts);
    }

    private AtakTeamContactDataSource.ContactSnapshot findContact(String uid,
            String callsign,
            List<AtakTeamContactDataSource.ContactSnapshot> contacts) {
        if (contacts == null)
            return null;
        for (AtakTeamContactDataSource.ContactSnapshot contact : contacts) {
            if ((uid != null && uid.equals(contact.getUid()))
                    || (callsign != null && callsign.equalsIgnoreCase(
                            contact.getCallsign())))
                return contact;
        }
        return null;
    }

    private boolean sameMember(String firstUid, String firstCallsign,
            String secondUid, String secondCallsign) {
        return (firstUid != null && firstUid.length() > 0
                && firstUid.equals(secondUid))
                || (firstCallsign != null && firstCallsign.length() > 0
                && firstCallsign.equalsIgnoreCase(secondCallsign));
    }

    private void updateMemberFromContact(SearchTeamMember member,
            AtakTeamContactDataSource.ContactSnapshot contact,
            GeoPoint selfPoint, GridCoordinateConverter converter) {
        GeoPoint point = contact.getPoint();
        if (point == null || !point.isValid()) {
            member.setAtakGroupName(contact.getAtakGroupName());
            member.markLocationUnavailable("No GPS Signal");
            member.setConnectionStatus(SearchTeamMember.ConnectionStatus.STALE);
            return;
        }
        member.setAtakGroupName(contact.getAtakGroupName());
        member.setConnectionStatus(SearchTeamMember.ConnectionStatus.CONNECTED);
        member.updatePosition(point.getLatitude(), point.getLongitude(),
                contact.getHeadingDegrees(), formatGeo(point),
                formatAltitude(point), converter.cellIdForPoint(point),
                formatLastPing(contact.getTimestamp()),
                formatDistanceFromSelf(selfPoint, point),
                member.getDistanceFromSearchLine());
        member.updateMovement(contact.getHeadingDegrees(),
                contact.isHeadingReliable(), contact.getSpeedMetersPerSecond());
        applyMemberStyle(member);
    }

    private String formatDistanceFromSelf(GeoPoint selfPoint,
            GeoPoint contactPoint) {
        if (selfPoint == null || contactPoint == null
                || !selfPoint.isValid() || !contactPoint.isValid())
            return "Self GPS unavailable";
        return formatDistance(distance(UTMPoint.fromGeoPoint(selfPoint),
                UTMPoint.fromGeoPoint(contactPoint)));
    }

    public String describeConnectionAlerts() {
        StringBuilder builder = new StringBuilder();
        for (SearchTeamMember member : members) {
            if (member.needsConnectionAlert()) {
                builder.append("ALERT: ")
                        .append(member.getCallsign())
                        .append(" ")
                        .append(member.getConnectionStatus())
                        .append(". Lane remains assigned. Reconnect pending.")
                        .append("\n");
            }
        }
        if (builder.length() == 0)
            return "No team connection alerts";
        return builder.toString().trim();
    }

    private double searchLineOffset(int index) {
        double[] offsets = new double[] { -6.0, 4.0, 11.0, -3.0, 7.0,
                -9.0, 13.0, 2.0 };
        return offsets[index % offsets.length];
    }

    private String formatGeo(GeoPoint point) {
        if (point == null || !point.isValid())
            return "No GPS Signal";
        return String.format(Locale.US, "%.6f, %.6f", point.getLatitude(),
                point.getLongitude());
    }

    private String formatAltitude(GeoPoint point) {
        if (point == null || !point.isAltitudeValid())
            return "No ATAK altitude";
        return Math.round(point.getAltitude()) + " m";
    }

    private String formatLastPing(long timestamp) {
        if (timestamp <= 0L)
            return "ATAK GPS now";
        long seconds = Math.max(0L, (System.currentTimeMillis() - timestamp)
                / 1000L);
        return seconds <= 1L ? "ATAK GPS now" : seconds + " sec ago";
    }

    private String formatDistance(double meters) {
        return Math.round(meters) + " m";
    }

    private String formatLineOffset(double meters) {
        long rounded = Math.round(Math.abs(meters));
        if (rounded <= 2)
            return "On line";
        return rounded + " m " + (meters > 0 ? "ahead" : "behind");
    }

    private double distance(UTMPoint first, UTMPoint second) {
        double dx = first.getEasting() - second.getEasting();
        double dy = first.getNorthing() - second.getNorthing();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
