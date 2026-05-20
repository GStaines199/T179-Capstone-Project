package com.atakmap.android.plugintemplate.runtime;

import com.atakmap.android.plugintemplate.grid.SearchGridCell;
import com.atakmap.android.plugintemplate.grid.SearchGridStatus;
import com.atakmap.android.plugintemplate.grid.SearchLineColorOption;

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
    private final double lineNorthing;
    private final SearchLineColorOption colorOption;
    private final double toleranceMeters;
    private final long created;

    public SearchLineCotMessage(String uid, String action, String teamId,
            String senderUid, String senderCallsign, String zoneDescriptor,
            String aggregateId, String cellId, int row, int column,
            double west, double south, double east, double north,
            double lineNorthing, SearchLineColorOption colorOption,
            double toleranceMeters, long created) {
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
        this.lineNorthing = lineNorthing;
        this.colorOption = colorOption;
        this.toleranceMeters = toleranceMeters;
        this.created = created;
    }

    public String getUid() { return uid; }
    public String getAction() { return action; }
    public String getTeamId() { return teamId; }
    public String getSenderUid() { return senderUid; }
    public String getSenderCallsign() { return senderCallsign; }
    public String getZoneDescriptor() { return zoneDescriptor; }
    public double getLineNorthing() { return lineNorthing; }
    public SearchLineColorOption getColorOption() { return colorOption; }
    public double getToleranceMeters() { return toleranceMeters; }
    public long getCreated() { return created; }

    public SearchGridCell toCell() {
        if (zoneDescriptor == null || zoneDescriptor.length() == 0
                || cellId == null || cellId.length() == 0)
            return null;
        return new SearchGridCell(aggregateId, cellId, row, column,
                zoneDescriptor, west, south, east, north,
                SearchGridStatus.NOT_STARTED);
    }
}
