package com.atakmap.android.plugintemplate.runtime;

public class SearchTeamCotMessage {

    public static final String ACTION_ADVERTISE = "advertise";
    public static final String ACTION_INVITE = "invite";
    public static final String ACTION_INVITE_ACCEPT = "invite_accept";
    public static final String ACTION_INVITE_DECLINE = "invite_decline";
    public static final String ACTION_INVITE_CANCEL = "invite_cancel";
    public static final String ACTION_JOIN_REQUEST = "join_request";
    public static final String ACTION_JOIN_ACCEPT = "join_accept";
    public static final String ACTION_JOIN_DECLINE = "join_decline";
    public static final String ACTION_JOIN_CANCEL = "join_cancel";

    private final String uid;
    private final String action;
    private final String teamId;
    private final String teamName;
    private final String leaderUid;
    private final String leaderCallsign;
    private final String senderUid;
    private final String senderCallsign;
    private final String targetUid;
    private final String targetCallsign;

    public SearchTeamCotMessage(String uid, String action, String teamId,
            String teamName, String leaderUid, String leaderCallsign,
            String senderUid, String senderCallsign, String targetUid,
            String targetCallsign) {
        this.uid = uid;
        this.action = action;
        this.teamId = teamId;
        this.teamName = teamName;
        this.leaderUid = leaderUid;
        this.leaderCallsign = leaderCallsign;
        this.senderUid = senderUid;
        this.senderCallsign = senderCallsign;
        this.targetUid = targetUid;
        this.targetCallsign = targetCallsign;
    }

    public String getUid() { return uid; }
    public String getAction() { return action; }
    public String getTeamId() { return teamId; }
    public String getTeamName() { return teamName; }
    public String getLeaderUid() { return leaderUid; }
    public String getLeaderCallsign() { return leaderCallsign; }
    public String getSenderUid() { return senderUid; }
    public String getSenderCallsign() { return senderCallsign; }
    public String getTargetUid() { return targetUid; }
    public String getTargetCallsign() { return targetCallsign; }

    public String getDisplayLabel() {
        return teamName + "\nLeader: " + leaderCallsign + "\n" + teamId;
    }
}
