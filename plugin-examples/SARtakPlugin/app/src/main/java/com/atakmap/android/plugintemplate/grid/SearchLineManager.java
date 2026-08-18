package com.atakmap.android.plugintemplate.grid;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.UTMPoint;
import com.atakmap.android.plugintemplate.runtime.SearchLineCotMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SearchLineManager {

    public static final double SLOW_DOWN_THRESHOLD_METERS = 8.0;
    private final GridCoordinateConverter converter;
    private final SearchPartyAssignmentManager assignmentManager;
    private SearchLineState state = SearchLineState.NOT_STARTED;
    private SearchGridCell activeCell;
    private String zoneDescriptor;
    private double lineNorthing;
    private long lineStartedAt;
    private long linePausedAt;
    private String restartWarning = "Search line not started";
    private SearchLineColorOption colorOption = SearchLineColorOption.CYAN;
    private double returnMarkToleranceMeters = 10.0;
    private boolean remoteControlled;
    private String remoteLeaderCallsign = "";
    private long lastRemoteUpdate;

    public SearchLineManager(GridCoordinateConverter converter,
            SearchPartyAssignmentManager assignmentManager) {
        this.converter = converter;
        this.assignmentManager = assignmentManager;
    }

    public void start(SearchGridCell cell, GeoPoint leaderPoint) {
        if (cell == null)
            return;
        activeCell = cell;
        zoneDescriptor = cell.getZoneDescriptor();
        lineNorthing = northingForLeader(cell, leaderPoint);
        state = SearchLineState.ACTIVE;
        lineStartedAt = System.currentTimeMillis();
        restartWarning = "Search line active";
        remoteControlled = false;
        remoteLeaderCallsign = "";
    }

    public void end() {
        activeCell = null;
        zoneDescriptor = null;
        lineNorthing = 0.0;
        state = SearchLineState.NOT_STARTED;
        lineStartedAt = 0L;
        linePausedAt = 0L;
        restartWarning = "Search line ended";
        remoteControlled = false;
        remoteLeaderCallsign = "";
        lastRemoteUpdate = 0L;
    }

    public void applyRemote(SearchLineCotMessage message) {
        if (message == null)
            return;
        if (SearchLineCotMessage.ACTION_END.equals(message.getAction())) {
            activeCell = null;
            zoneDescriptor = null;
            lineNorthing = 0.0;
            state = SearchLineState.NOT_STARTED;
            lineStartedAt = 0L;
            linePausedAt = 0L;
            remoteControlled = true;
            remoteLeaderCallsign = message.getSenderCallsign();
            lastRemoteUpdate = message.getCreated();
            restartWarning = "Team leader ended the search line";
            return;
        }

        SearchGridCell cell = message.toCell();
        if (cell == null)
            return;
        activeCell = cell;
        zoneDescriptor = cell.getZoneDescriptor();
        lineNorthing = clamp(message.getLineNorthing(), cell.getSouth(),
                cell.getNorth());
        colorOption = message.getColorOption();
        setReturnMarkToleranceMeters(message.getToleranceMeters());
        state = SearchLineCotMessage.ACTION_PAUSE.equals(message.getAction())
                ? SearchLineState.PAUSED : SearchLineState.ACTIVE;
        if (lineStartedAt <= 0L)
            lineStartedAt = message.getCreated();
        if (state == SearchLineState.PAUSED)
            linePausedAt = message.getCreated();
        remoteControlled = true;
        remoteLeaderCallsign = message.getSenderCallsign();
        lastRemoteUpdate = message.getCreated();
        restartWarning = state == SearchLineState.PAUSED
                ? "Leader paused the search line"
                : "Synced from team leader";
    }

    public void updateLeaderPosition(SearchGridCell cell,
            GeoPoint leaderPoint) {
        if (state != SearchLineState.ACTIVE || cell == null)
            return;
        activeCell = cell;
        zoneDescriptor = cell.getZoneDescriptor();
        lineNorthing = northingForLeader(cell, leaderPoint);
    }

    public void pause() {
        if (state != SearchLineState.ACTIVE)
            return;
        state = SearchLineState.PAUSED;
        linePausedAt = System.currentTimeMillis();
        restartWarning = "Line paused. Return marks are fixed.";
    }

    public boolean resume() {
        if (state != SearchLineState.PAUSED)
            return state == SearchLineState.ACTIVE;
        List<SearchLineMemberStatus> offMarks = getMembersOffReturnMark();
        if (!offMarks.isEmpty()) {
            restartWarning = buildRestartPrompt(offMarks);
            return false;
        }
        state = SearchLineState.ACTIVE;
        restartWarning = "Search line resumed";
        return true;
    }

    public void forceResume() {
        if (state == SearchLineState.PAUSED) {
            state = SearchLineState.ACTIVE;
            restartWarning = "Leader forced search line restart";
        }
    }

    public SearchLineState getState() {
        return state;
    }

    public boolean isStarted() {
        return state != SearchLineState.NOT_STARTED;
    }

    public boolean isPaused() {
        return state == SearchLineState.PAUSED;
    }

    public boolean isRemoteControlled() {
        return remoteControlled;
    }

    public SearchLineColorOption cycleColor() {
        colorOption = colorOption.next();
        return colorOption;
    }

    public void setColorOption(SearchLineColorOption colorOption) {
        this.colorOption = colorOption;
    }

    public SearchLineColorOption getColorOption() {
        return colorOption;
    }

    public void setReturnMarkToleranceMeters(double toleranceMeters) {
        returnMarkToleranceMeters = Math.max(1.0, toleranceMeters);
    }

    public double getReturnMarkToleranceMeters() {
        return returnMarkToleranceMeters;
    }

    public SearchGridCell getActiveCell() {
        return activeCell;
    }

    public double getLineNorthing() {
        return lineNorthing;
    }

    public double getArrangementNorthing(SearchGridCell cell,
            GeoPoint leaderPoint) {
        if (state != SearchLineState.NOT_STARTED && activeCell != null)
            return lineNorthing;
        if (cell == null || leaderPoint == null)
            return 0.0;
        return northingForLeader(cell, leaderPoint);
    }

    public String getZoneDescriptor() {
        return zoneDescriptor;
    }

    public GeoPoint getLineStart() {
        if (activeCell == null)
            return null;
        return converter.toGeoPoint(zoneDescriptor, activeCell.getWest(),
                lineNorthing);
    }

    public GeoPoint getLineEnd() {
        if (activeCell == null)
            return null;
        return converter.toGeoPoint(zoneDescriptor, activeCell.getEast(),
                lineNorthing);
    }

    public GeoPoint getDirectionStart() {
        if (activeCell == null)
            return null;
        double centerEasting = (activeCell.getWest() + activeCell.getEast())
                / 2.0;
        return converter.toGeoPoint(zoneDescriptor, centerEasting,
                lineNorthing);
    }

    public GeoPoint getDirectionEnd() {
        if (activeCell == null)
            return null;
        double centerEasting = (activeCell.getWest() + activeCell.getEast())
                / 2.0;
        double northing = clamp(lineNorthing + 14.0, activeCell.getSouth(),
                activeCell.getNorth());
        return converter.toGeoPoint(zoneDescriptor, centerEasting, northing);
    }

    public List<SearchLineMemberStatus> getMemberStatuses() {
        List<SearchLineMemberStatus> statuses = new ArrayList<>();
        if (activeCell == null || zoneDescriptor == null)
            return statuses;

        int laneCount = Math.max(1, assignmentManager.getLaneMemberCount());
        for (SearchTeamMember member : assignmentManager.getVisibleMembers()) {
            if (!member.contributesLane())
                continue;
            if (!member.hasLiveAtakContact())
                continue;
            UTMPoint memberPoint = UTMPoint.fromGeoPoint(new GeoPoint(
                    member.getLatitude(), member.getLongitude()));
            GeoPoint returnMark = returnMarkForMember(member, laneCount);
            UTMPoint returnPoint = UTMPoint.fromGeoPoint(returnMark);
            double distanceFromLine = memberPoint.getNorthing() - lineNorthing;
            double distanceFromReturnMark = distance(memberPoint, returnPoint);
            statuses.add(new SearchLineMemberStatus(member, distanceFromLine,
                    distanceFromReturnMark, paceFor(member), returnMark));
        }
        return statuses;
    }

    public String getSummary() {
        if (state == SearchLineState.NOT_STARTED)
            return remoteControlled && remoteLeaderCallsign.length() > 0
                    ? "Leader line not started"
                    : "Line not started";
        String prefix = remoteControlled
                ? "Leader line " + state.name() + " | "
                        + remoteLeaderCallsign
                : "Line " + state.name();
        return prefix
                + " | Leader pace: " + formatPace(getLeaderPace())
                + " | Line pace: " + formatPace(getLinePace());
    }

    public String getMemberLineSummary() {
        if (state == SearchLineState.NOT_STARTED)
            return "Start the search line to measure team spacing.";

        StringBuilder builder = new StringBuilder();
        for (SearchLineMemberStatus status : getMemberStatuses()) {
            builder.append(status.getMember().getCallsign())
                    .append(": ")
                    .append(formatLineDistance(status
                            .getDistanceFromLineMeters()))
                    .append(" | Pace ")
                    .append(formatPace(status.getPaceMetersPerMinute()));
            if (status.isTooFarAhead(SLOW_DOWN_THRESHOLD_METERS))
                builder.append(" | Slow down");
            if (state == SearchLineState.PAUSED)
                builder.append(" | Return mark ")
                        .append(Math.round(status
                                .getDistanceFromReturnMarkMeters()))
                        .append(" m");
            builder.append("\n");
        }
        return builder.toString().trim();
    }

    public String getMemberCardLineSummary(String uniqueId) {
        if (state == SearchLineState.NOT_STARTED)
            return "Line not started";
        for (SearchLineMemberStatus status : getMemberStatuses()) {
            if (status.getMember().getUniqueId().equals(uniqueId)) {
                String summary = formatLineDistance(status
                        .getDistanceFromLineMeters()) + " | Pace "
                        + formatPace(status.getPaceMetersPerMinute());
                if (status.isTooFarAhead(SLOW_DOWN_THRESHOLD_METERS))
                    summary += " | Slow down";
                return summary;
            }
        }
        return "Leader controls line";
    }

    public String getWarningSummary() {
        if (state == SearchLineState.NOT_STARTED)
            return "No search line warnings";

        StringBuilder builder = new StringBuilder();
        for (SearchLineMemberStatus status : getMemberStatuses()) {
            if (status.isTooFarAhead(SLOW_DOWN_THRESHOLD_METERS)) {
                builder.append("HOLD/SLOW: ")
                        .append(status.getMember().getCallsign())
                        .append(" is ")
                        .append(Math.round(status
                                .getDistanceFromLineMeters()))
                        .append(" m ahead of the line.\n");
            }
        }
        if (state == SearchLineState.PAUSED)
            builder.append(restartWarning).append("\n");
        if (builder.length() == 0)
            return "No search line warnings";
        return builder.toString().trim();
    }

    public String getRestartWarning() {
        return restartWarning;
    }

    public String getRestartPrompt() {
        List<SearchLineMemberStatus> offMarks = getMembersOffReturnMark();
        if (offMarks.isEmpty())
            return "Everyone is on their restart marks.";
        return buildRestartPrompt(offMarks);
    }

    public List<SearchLineMemberStatus> getMembersOffReturnMark() {
        List<SearchLineMemberStatus> offMarks = new ArrayList<>();
        for (SearchLineMemberStatus status : getMemberStatuses()) {
            if (status.getMember().isTeamLeader())
                continue;
            if (status.isOffReturnMark(getReturnMarkToleranceMeters()))
                offMarks.add(status);
        }
        return offMarks;
    }

    private String buildRestartPrompt(List<SearchLineMemberStatus> offMarks) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < offMarks.size(); i++) {
            if (i > 0) {
                if (i == offMarks.size() - 1)
                    builder.append(" and ");
                else
                    builder.append(", ");
            }
            builder.append(offMarks.get(i).getMember().getCallsign());
        }
        builder.append(offMarks.size() == 1
                ? " is not on their restart mark. "
                : " are not on their restart marks. ");
        builder.append("Would you like to force restart?");
        return builder.toString();
    }

    private GeoPoint returnMarkForMember(SearchTeamMember member,
            int laneCount) {
        int laneIndex = Math.max(0, Math.min(laneCount - 1,
                member.getLaneNumber() - 1));
        double laneWidth = (activeCell.getEast() - activeCell.getWest())
                / laneCount;
        double easting = activeCell.getWest() + laneWidth
                * (laneIndex + 0.5);
        return converter.toGeoPoint(zoneDescriptor, easting, lineNorthing);
    }

    private double northingForLeader(SearchGridCell cell,
            GeoPoint leaderPoint) {
        UTMPoint leader = UTMPoint.fromGeoPoint(leaderPoint);
        return clamp(leader.getNorthing(), cell.getSouth(), cell.getNorth());
    }

    private double distance(UTMPoint first, UTMPoint second) {
        double dx = first.getEasting() - second.getEasting();
        double dy = first.getNorthing() - second.getNorthing();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private double paceFor(SearchTeamMember member) {
        if (member.getConnectionStatus()
                != SearchTeamMember.ConnectionStatus.CONNECTED)
            return 0.0;
        return member.getSpeedMetersPerSecond() * 60.0;
    }

    private double getLeaderPace() {
        if (state != SearchLineState.ACTIVE)
            return 0.0;
        SearchTeamMember leader = getLeaderMember();
        return leader == null ? 0.0 : paceFor(leader);
    }

    private double getLinePace() {
        if (state != SearchLineState.ACTIVE)
            return 0.0;
        return getLeaderPace();
    }

    private SearchTeamMember getLeaderMember() {
        for (SearchTeamMember member : assignmentManager.getVisibleMembers()) {
            if (member.isTeamLeader())
                return member;
        }
        return null;
    }

    private String formatLineDistance(double distanceMeters) {
        long rounded = Math.round(Math.abs(distanceMeters));
        if (rounded <= 2)
            return "On line";
        return rounded + " m " + (distanceMeters > 0 ? "ahead" : "behind");
    }

    private String formatPace(double pace) {
        if (pace <= 0.0)
            return "unavailable";
        return String.format(Locale.US, "%.1f m/min", pace);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
