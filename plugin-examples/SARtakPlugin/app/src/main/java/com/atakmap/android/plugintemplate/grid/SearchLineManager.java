package com.atakmap.android.plugintemplate.grid;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.UTMPoint;
import com.atakmap.android.plugintemplate.runtime.SearchLineCotMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SearchLineManager {

    public static final double SLOW_DOWN_THRESHOLD_METERS = 8.0;
    /** Length of the arrow drawn from the line toward the direction of travel. */
    private static final double DIRECTION_ARROW_METERS = 14.0;
    private final GridCoordinateConverter converter;
    private final SearchPartyAssignmentManager assignmentManager;
    private SearchLineState state = SearchLineState.NOT_STARTED;
    private SearchGridCell activeCell;
    private String zoneDescriptor;
    /**
     * Where the line sits along the direction of travel: a northing when
     * heading north or south, an easting when heading east or west. Named for
     * what it means rather than which coordinate it happens to be, because it
     * is only one of the two depending on {@link #direction}.
     */
    private double lineOffset;
    private SearchLineDirection direction = SearchLineDirection.NORTH;
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
        lineOffset = offsetForLeader(cell, leaderPoint);
        state = SearchLineState.ACTIVE;
        lineStartedAt = System.currentTimeMillis();
        restartWarning = "Search line active";
        remoteControlled = false;
        remoteLeaderCallsign = "";
    }

    public void end() {
        activeCell = null;
        zoneDescriptor = null;
        lineOffset = 0.0;
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
            lineOffset = 0.0;
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
        // Direction is read before the offset is clamped: the offset is a
        // northing or an easting depending on it, so clamping against the
        // wrong pair of cell edges would silently snap the line to a corner.
        direction = message.getDirection();
        lineOffset = clamp(message.getLineOffset(),
                direction.travelMin(cell), direction.travelMax(cell));
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
        lineOffset = offsetForLeader(cell, leaderPoint);
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

    public SearchLineDirection getDirection() {
        return direction;
    }

    /**
     * Re-aims the line, keeping it under the leader's feet.
     *
     * <p>The offset has to be recomputed from the leader rather than carried
     * over, because it changes meaning: a northing of 6 200 750 reinterpreted
     * as an easting lands the line outside the cell entirely. Turning east
     * while a line is running therefore re-anchors it to where the leader is
     * standing, which is also what a leader means by the instruction.
     */
    public void setDirection(SearchLineDirection direction,
            GeoPoint leaderPoint) {
        if (direction == null || direction == this.direction)
            return;
        this.direction = direction;
        if (activeCell != null && leaderPoint != null)
            lineOffset = offsetForLeader(activeCell, leaderPoint);
    }

    public SearchLineDirection cycleDirection(GeoPoint leaderPoint) {
        setDirection(direction.next(), leaderPoint);
        return direction;
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

    /**
     * The line's position along its travel axis. A northing when heading north
     * or south, an easting when heading east or west -- read
     * {@link #getDirection()} before interpreting it.
     */
    public double getLineOffset() {
        return lineOffset;
    }

    public double getArrangementOffset(SearchGridCell cell,
            GeoPoint leaderPoint) {
        if (state != SearchLineState.NOT_STARTED && activeCell != null)
            return lineOffset;
        if (cell == null || leaderPoint == null)
            return 0.0;
        return offsetForLeader(cell, leaderPoint);
    }

    public String getZoneDescriptor() {
        return zoneDescriptor;
    }

    /** The end of the line at the low edge of its axis: west, or south. */
    public GeoPoint getLineStart() {
        if (activeCell == null)
            return null;
        return pointOnLine(direction.lineMin(activeCell), lineOffset);
    }

    /** The end of the line at the high edge of its axis: east, or north. */
    public GeoPoint getLineEnd() {
        if (activeCell == null)
            return null;
        return pointOnLine(direction.lineMax(activeCell), lineOffset);
    }

    public GeoPoint getDirectionStart() {
        if (activeCell == null)
            return null;
        return pointOnLine(lineCenter(), lineOffset);
    }

    /**
     * The tip of the direction arrow, one arrow-length along the travel axis.
     *
     * <p>Clamped to the cell, so the arrow shortens rather than leaving the
     * cell as the line reaches the far edge. An arrow drawn past the boundary
     * would point at ground this team is not searching.
     */
    public GeoPoint getDirectionEnd() {
        if (activeCell == null)
            return null;
        double tip = clamp(direction.advanceOffset(lineOffset,
                DIRECTION_ARROW_METERS), direction.travelMin(activeCell),
                direction.travelMax(activeCell));
        return pointOnLine(lineCenter(), tip);
    }

    /** Midpoint of the line along its own axis, where the arrow is rooted. */
    private double lineCenter() {
        if (activeCell == null)
            return 0.0;
        return (direction.lineMin(activeCell) + direction.lineMax(activeCell))
                / 2.0;
    }

    /**
     * Converts a point expressed in line-relative terms -- how far along the
     * line, how far along the travel axis -- into a map point.
     *
     * <p>All line geometry goes through here so the mapping from those two
     * axes onto easting and northing is written once. The bug this prevents is
     * silent: swap them and the line is drawn perpendicular to the direction
     * the team is actually walking.
     */
    private GeoPoint pointOnLine(double alongLine, double travelOffset) {
        if (activeCell == null || zoneDescriptor == null)
            return null;
        return converter.toGeoPoint(zoneDescriptor,
                direction.eastingAt(alongLine, travelOffset),
                direction.northingAt(alongLine, travelOffset));
    }

    public List<SearchLineMemberStatus> getMemberStatuses() {
        List<SearchLineMemberStatus> statuses = new ArrayList<>();
        if (activeCell == null || zoneDescriptor == null)
            return statuses;

        int laneCount = Math.max(1, assignmentManager.getLaneMemberCount());
        for (SearchTeamMember member : assignmentManager.getVisibleMembers()) {
            if (!member.contributesLane())
                continue;
            GeoPoint returnMark = returnMarkForMember(member, laneCount);

            // A member with no live ATAK fix keeps their place in the line and
            // keeps their return mark -- both come from the lane assignment,
            // not from where they are -- but no distance is measured from
            // coordinates we do not have. Dropping them from the list instead
            // would hide a searcher from their leader.
            if (!MemberPositionPolicy.hasUsablePosition(member)) {
                statuses.add(new SearchLineMemberStatus(member, Double.NaN,
                        Double.NaN, paceFor(member), returnMark, false,
                        member.getHeadingDegrees(),
                        member.hasReliableHeading()));
                continue;
            }

            UTMPoint memberPoint = UTMPoint.fromGeoPoint(new GeoPoint(
                    member.getLatitude(), member.getLongitude()));
            UTMPoint returnPoint = UTMPoint.fromGeoPoint(returnMark);
            // Signed along the direction of travel, not along northing.
            // Subtracting coordinates directly would report a south-bound
            // searcher who had overrun the line as lagging behind it.
            double distanceFromLine = direction.progressMeters(
                    memberPoint.getEasting(), memberPoint.getNorthing(),
                    lineOffset);
            double distanceFromReturnMark = distance(memberPoint, returnPoint);
            statuses.add(new SearchLineMemberStatus(member, distanceFromLine,
                    distanceFromReturnMark, paceFor(member), returnMark, true,
                    member.getHeadingDegrees(), member.hasReliableHeading()));
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
                + " | Heading: " + getDirectionLabel()
                + " | Leader pace: " + formatPace(getLeaderPace())
                + " | Line pace: " + formatPace(getLinePace());
    }

    public String getMemberLineSummary() {
        if (state == SearchLineState.NOT_STARTED)
            return "Start the search line to measure team spacing.";

        StringBuilder builder = new StringBuilder();
        for (SearchLineMemberStatus status : getMemberStatuses()) {
            // getGuidanceLabel already carries "slow down" / "catch up" when
            // the drift warrants it, so no separate suffix is appended here.
            // Appending one as well is how the old summary could say
            // "Position unknown | Slow down" about the same searcher.
            builder.append(status.getMember().getCallsign())
                    .append(": ")
                    .append(status.getGuidanceLabel())
                    .append(" | Pace ")
                    .append(status.getPaceLabel())
                    .append(" | Bearing ")
                    .append(status.getBearingLabel());
            // Math.round turns NaN into 0, so an unmeasured member would
            // otherwise be reported as standing exactly on their return mark.
            if (state == SearchLineState.PAUSED && status.hasKnownPosition())
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
                return status.getGuidanceLabel()
                        + " | Pace " + status.getPaceLabel()
                        + " | Bearing " + status.getBearingLabel();
            }
        }
        return "Leader controls line";
    }

    public String getWarningSummary() {
        if (state == SearchLineState.NOT_STARTED)
            return "No search line warnings";

        StringBuilder builder = new StringBuilder();
        for (SearchLineMemberStatus status : getMemberStatuses()) {
            // Both directions. A searcher who drops behind leaves unswept
            // ground just as surely as one who runs ahead, and used to be
            // told nothing at all.
            if (status.isTooFarAhead(SLOW_DOWN_THRESHOLD_METERS)) {
                builder.append("HOLD/SLOW: ")
                        .append(status.getMember().getCallsign())
                        .append(" is ")
                        .append(Math.round(Math.abs(status
                                .getDistanceFromLineMeters())))
                        .append(" m ahead of the line.\n");
            } else if (status.isTooFarBehind(SLOW_DOWN_THRESHOLD_METERS)) {
                builder.append("CATCH UP: ")
                        .append(status.getMember().getCallsign())
                        .append(" is ")
                        .append(Math.round(Math.abs(status
                                .getDistanceFromLineMeters())))
                        .append(" m behind the line.\n");
            }
        }
        if (state == SearchLineState.PAUSED)
            builder.append(restartWarning).append("\n");
        if (builder.length() == 0)
            return "No search line warnings";
        return builder.toString().trim();
    }

    /**
     * The direction of travel as shown on the Home tab and the map, with its
     * bearing: "North (000 deg T)".
     *
     * <p>The bearing is the line's own heading, which is a fixed property of
     * the direction and is never withheld. It is not a searcher's heading --
     * that one comes from GPS and is hidden when unreliable, via
     * {@link SearchLineGuidance#bearingLabel}.
     */
    public String getDirectionLabel() {
        return direction.getLabel() + " ("
                + String.format(Locale.US, "%03d", Math.round(
                        direction.getCompassDegrees())) + " deg T)";
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

    /**
     * The point in this member's lane where they should stand.
     *
     * <p>Lanes divide the cell across the line, which is the easting axis for
     * a north-south line and the northing axis for an east-west one. Dividing
     * along easting regardless -- as this did when the line was north-only --
     * would stack an east-bound team's lanes on top of each other along their
     * own direction of travel.
     */
    private GeoPoint returnMarkForMember(SearchTeamMember member,
            int laneCount) {
        int laneIndex = Math.max(0, Math.min(laneCount - 1,
                member.getLaneNumber() - 1));
        double lineMin = direction.lineMin(activeCell);
        double laneWidth = (direction.lineMax(activeCell) - lineMin)
                / laneCount;
        return pointOnLine(lineMin + laneWidth * (laneIndex + 0.5),
                lineOffset);
    }

    private double offsetForLeader(SearchGridCell cell, GeoPoint leaderPoint) {
        UTMPoint leader = UTMPoint.fromGeoPoint(leaderPoint);
        return clamp(direction.travelCoordinate(leader.getEasting(),
                leader.getNorthing()), direction.travelMin(cell),
                direction.travelMax(cell));
    }

    private double distance(UTMPoint first, UTMPoint second) {
        double dx = first.getEasting() - second.getEasting();
        double dy = first.getNorthing() - second.getNorthing();
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * This member's pace in metres per minute, or NaN when ATAK has not
     * reported one.
     *
     * <p>NaN rather than zero, deliberately. Zero is a real reading -- a
     * searcher who has stopped -- and returning it for a member whose radio
     * has gone quiet made the two indistinguishable, so a stopped searcher was
     * labelled "unavailable" and an unreachable one looked stationary. A
     * member with no live ATAK fix has no pace either: the coordinates behind
     * any speed we last held are the ones
     * {@link MemberPositionPolicy#hasUsablePosition} already refuses to trust.
     */
    private double paceFor(SearchTeamMember member) {
        if (member.getConnectionStatus()
                != SearchTeamMember.ConnectionStatus.CONNECTED
                || !MemberPositionPolicy.hasUsablePosition(member))
            return Double.NaN;
        return member.getSpeedMetersPerSecond() * 60.0;
    }

    /**
     * NaN while the line is not running: no pace is being measured against it,
     * which is a different statement from a leader standing still.
     */
    private double getLeaderPace() {
        if (state != SearchLineState.ACTIVE)
            return Double.NaN;
        SearchTeamMember leader = getLeaderMember();
        return leader == null ? Double.NaN : paceFor(leader);
    }

    private double getLinePace() {
        if (state != SearchLineState.ACTIVE)
            return Double.NaN;
        return getLeaderPace();
    }

    private SearchTeamMember getLeaderMember() {
        for (SearchTeamMember member : assignmentManager.getVisibleMembers()) {
            if (member.isTeamLeader())
                return member;
        }
        return null;
    }

    private String formatPace(double pace) {
        return SearchLineGuidance.paceLabel(pace);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
