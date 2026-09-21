package com.atakmap.android.plugintemplate.runtime;

import com.atakmap.android.plugintemplate.grid.SearchGridCell;
import com.atakmap.android.plugintemplate.grid.SearchGridStatus;
import com.atakmap.android.plugintemplate.grid.SearchLineColorOption;
import com.atakmap.android.plugintemplate.grid.SearchLineDirection;

public class SearchLineCotMessage {

    public static final String ACTION_START = "line_start";
    public static final String ACTION_UPDATE = "line_update";
    public static final String ACTION_PAUSE = "line_pause";
    public static final String ACTION_RESUME = "line_resume";
    public static final String ACTION_END = "line_end";

    private final String uid;
    private final String action;
    private final String teamId;
    private final String senderUid;
    private final String senderCallsign;
    private final String zoneDescriptor;
    private final String aggregateId;
    private final String cellId;
    private final int row;
    private final int column;
    private final double west;
    private final double south;
    private final double east;
    private final double north;
    private final double lineOffset;
    private final SearchLineDirection direction;
    private final SearchLineColorOption colorOption;
    private final double toleranceMeters;
    private final long created;
    private final String operationId;

    /**
     * Retained for callers that predate directional lines. Defaults to
     * {@link SearchLineDirection#NORTH}, which is what every line was before
     * the direction existed.
     */
    public SearchLineCotMessage(String uid, String action, String teamId,
            String senderUid, String senderCallsign, String zoneDescriptor,
            String aggregateId, String cellId, int row, int column,
            double west, double south, double east, double north,
            double lineOffset, SearchLineColorOption colorOption,
            double toleranceMeters, long created) {
        this(uid, action, teamId, senderUid, senderCallsign, zoneDescriptor,
                aggregateId, cellId, row, column, west, south, east, north,
                lineOffset, colorOption, toleranceMeters, created, "",
                SearchLineDirection.NORTH);
    }

    public SearchLineCotMessage(String uid, String action, String teamId,
            String senderUid, String senderCallsign, String zoneDescriptor,
            String aggregateId, String cellId, int row, int column,
            double west, double south, double east, double north,
            double lineOffset, SearchLineColorOption colorOption,
            double toleranceMeters, long created, String operationId) {
        this(uid, action, teamId, senderUid, senderCallsign, zoneDescriptor,
                aggregateId, cellId, row, column, west, south, east, north,
                lineOffset, colorOption, toleranceMeters, created, operationId,
                SearchLineDirection.NORTH);
    }

    public SearchLineCotMessage(String uid, String action, String teamId,
            String senderUid, String senderCallsign, String zoneDescriptor,
            String aggregateId, String cellId, int row, int column,
            double west, double south, double east, double north,
            double lineOffset, SearchLineColorOption colorOption,
            double toleranceMeters, long created, String operationId,
            SearchLineDirection direction) {
        this.uid = uid;
        this.action = action;
        this.teamId = teamId;
        this.senderUid = senderUid;
        this.senderCallsign = senderCallsign;
        this.zoneDescriptor = zoneDescriptor;
        this.aggregateId = aggregateId;
        this.cellId = cellId;
        this.row = row;
        this.column = column;
        this.west = west;
        this.south = south;
        this.east = east;
        this.north = north;
        this.lineOffset = lineOffset;
        this.direction = direction == null
                ? SearchLineDirection.NORTH : direction;
        this.colorOption = colorOption;
        this.toleranceMeters = toleranceMeters;
        this.created = created;
        this.operationId = operationId == null ? "" : operationId;
    }

    public String getUid() { return uid; }
    public String getAction() { return action; }
    public String getTeamId() { return teamId; }
    public String getSenderUid() { return senderUid; }
    public String getSenderCallsign() { return senderCallsign; }
    public String getZoneDescriptor() { return zoneDescriptor; }
    /**
     * Where the line sits along its direction of travel: a northing for a
     * north or south line, an easting for an east or west one. Read
     * {@link #getDirection()} before interpreting it.
     */
    public double getLineOffset() { return lineOffset; }
    public SearchLineDirection getDirection() { return direction; }
    public SearchLineColorOption getColorOption() { return colorOption; }
    public double getToleranceMeters() { return toleranceMeters; }
    public long getCreated() { return created; }
    public String getOperationId() { return operationId; }

    public SearchGridCell toCell() {
        if (zoneDescriptor == null || zoneDescriptor.length() == 0
                || cellId == null || cellId.length() == 0)
            return null;
        return new SearchGridCell(aggregateId, cellId, row, column,
                zoneDescriptor, west, south, east, north,
                SearchGridStatus.NOT_STARTED);
    }
}
