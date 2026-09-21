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
    public static final String ACTION_TEAM_REMOVED = "team_removed";
    public static final String ACTION_MEMBER_REMOVED = "member_removed";
    public static final String ACTION_MEMBER_LEFT = "member_left";
    public static final String ACTION_PRESENCE = "presence";

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
    private final long created;
    private final String teamColorName;
    private final int teamColorArgb;
    private final String memberColorName;
    private final int memberColorArgb;
    private final String memberRole;
    private final String operationId;

    public SearchTeamCotMessage(String uid, String action, String teamId,
            String teamName, String leaderUid, String leaderCallsign,
            String senderUid, String senderCallsign, String targetUid,
            String targetCallsign) {
        this(uid, action, teamId, teamName, leaderUid, leaderCallsign,
                senderUid, senderCallsign, targetUid, targetCallsign,
                System.currentTimeMillis());
    }

    public SearchTeamCotMessage(String uid, String action, String teamId,
            String teamName, String leaderUid, String leaderCallsign,
            String senderUid, String senderCallsign, String targetUid,
            String targetCallsign, long created) {
        this(uid, action, teamId, teamName, leaderUid, leaderCallsign,
                senderUid, senderCallsign, targetUid, targetCallsign,
                created, "", 0, "", 0, "", "");
    }

    public SearchTeamCotMessage(String uid, String action, String teamId,
            String teamName, String leaderUid, String leaderCallsign,
            String senderUid, String senderCallsign, String targetUid,
            String targetCallsign, long created, String teamColorName,
            int teamColorArgb, String memberColorName, int memberColorArgb,
            String memberRole) {
        this(uid, action, teamId, teamName, leaderUid, leaderCallsign,
                senderUid, senderCallsign, targetUid, targetCallsign, created,
                teamColorName, teamColorArgb, memberColorName, memberColorArgb,
                memberRole, "");
    }

    public SearchTeamCotMessage(String uid, String action, String teamId,
            String teamName, String leaderUid, String leaderCallsign,
            String senderUid, String senderCallsign, String targetUid,
            String targetCallsign, long created, String teamColorName,
            int teamColorArgb, String memberColorName, int memberColorArgb,
            String memberRole, String operationId) {
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
        this.created = created;
        this.teamColorName = teamColorName == null ? "" : teamColorName;
        this.teamColorArgb = teamColorArgb;
        this.memberColorName = memberColorName == null ? "" : memberColorName;
        this.memberColorArgb = memberColorArgb;
        this.memberRole = memberRole == null ? "" : memberRole;
        this.operationId = operationId == null ? "" : operationId;
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
    public long getCreated() { return created; }
    public String getTeamColorName() { return teamColorName; }
    public int getTeamColorArgb() { return teamColorArgb; }
    public String getMemberColorName() { return memberColorName; }
    public int getMemberColorArgb() { return memberColorArgb; }
    public String getMemberRole() { return memberRole; }
    public String getOperationId() { return operationId; }

    public String getDisplayLabel() {
        return teamName + "\nLeader: " + leaderCallsign + "\n" + teamId;
    }
}
