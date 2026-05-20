package com.atakmap.android.plugintemplate.runtime;

import com.atakmap.android.plugintemplate.grid.SearchGridStatus;

public class SearchGridCotMessage {

    public static final String ACTION_GRID_STATUS = "grid_status";

    private final String uid;
    private final String teamId;
    private final String senderUid;
    private final String senderCallsign;
    private final String cellId;
    private final SearchGridStatus status;
    private final long created;

    public SearchGridCotMessage(String uid, String teamId, String senderUid,
            String senderCallsign, String cellId, SearchGridStatus status,
            long created) {
        this.uid = uid;
        this.teamId = teamId;
        this.senderUid = senderUid;
        this.senderCallsign = senderCallsign;
        this.cellId = cellId;
        this.status = status;
        this.created = created;
    }

    public String getUid() { return uid; }
    public String getTeamId() { return teamId; }
    public String getSenderUid() { return senderUid; }
    public String getSenderCallsign() { return senderCallsign; }
    public String getCellId() { return cellId; }
    public SearchGridStatus getStatus() { return status; }
    public long getCreated() { return created; }
}
