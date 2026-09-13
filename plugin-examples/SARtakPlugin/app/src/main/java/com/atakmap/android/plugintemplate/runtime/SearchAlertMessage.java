package com.atakmap.android.plugintemplate.runtime;

public class SearchAlertMessage {

    public static final String ACTION_ACTIVE = "active";
    public static final String ACTION_ACK = "ack";
    public static final String ACTION_CANCEL = "cancel";

    public static final String TYPE_HOLD_POSITION = "hold_position";
    public static final String TYPE_EMERGENCY_STOP = "emergency_stop";
    public static final String TYPE_REQUEST_LEADER = "request_leader";
    public static final String TYPE_RESUME_SEARCH = "resume_search";

    private final String uid;
    private final String action;
    private final String alertId;
    private final String alertType;
    private final String title;
    private final String message;
    private final String teamId;
    private final String teamName;
    private final String senderUid;
    private final String senderCallsign;
    private final String ackUid;
    private final String ackCallsign;
    private final double latitude;
    private final double longitude;
    private final boolean requiresHalt;
    private final long created;
    private final String operationId;

    public SearchAlertMessage(String uid, String action, String alertId,
            String alertType, String title, String message, String teamId,
            String teamName, String senderUid, String senderCallsign,
            String ackUid, String ackCallsign, double latitude,
            double longitude, boolean requiresHalt, long created,
            String operationId) {
        this.uid = safe(uid);
        this.action = safe(action);
        this.alertId = safe(alertId);
        this.alertType = safe(alertType);
        this.title = safe(title);
        this.message = safe(message);
        this.teamId = safe(teamId);
        this.teamName = safe(teamName);
        this.senderUid = safe(senderUid);
        this.senderCallsign = safe(senderCallsign);
        this.ackUid = safe(ackUid);
        this.ackCallsign = safe(ackCallsign);
        this.latitude = latitude;
        this.longitude = longitude;
        this.requiresHalt = requiresHalt;
        this.created = created;
        this.operationId = safe(operationId);
    }

    public String getUid() { return uid; }
    public String getAction() { return action; }
    public String getAlertId() { return alertId; }
    public String getAlertType() { return alertType; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getTeamId() { return teamId; }
    public String getTeamName() { return teamName; }
    public String getSenderUid() { return senderUid; }
    public String getSenderCallsign() { return senderCallsign; }
    public String getAckUid() { return ackUid; }
    public String getAckCallsign() { return ackCallsign; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public boolean requiresHalt() { return requiresHalt; }
    public long getCreated() { return created; }
    public String getOperationId() { return operationId; }

    public static String titleForType(String alertType) {
        if (TYPE_EMERGENCY_STOP.equals(alertType))
            return "Emergency Stop";
        if (TYPE_REQUEST_LEADER.equals(alertType))
            return "Team Leader Requested";
        if (TYPE_RESUME_SEARCH.equals(alertType))
            return "Resume Search";
        return "Hold Position";
    }

    public static String messageForType(String alertType, String callsign) {
        String sender = safe(callsign).length() == 0 ? "A team member"
                : callsign;
        if (TYPE_EMERGENCY_STOP.equals(alertType))
            return sender + " has triggered an emergency. Stop movement and check in.";
        if (TYPE_REQUEST_LEADER.equals(alertType))
            return sender + " needs the team leader. Hold position until directed.";
        if (TYPE_RESUME_SEARCH.equals(alertType))
            return sender + " has cleared the alert. Resume search when ready.";
        return sender + " says hold position. Stop movement and await instructions.";
    }

    public static boolean requiresHalt(String alertType) {
        return TYPE_HOLD_POSITION.equals(alertType)
                || TYPE_EMERGENCY_STOP.equals(alertType)
                || TYPE_REQUEST_LEADER.equals(alertType);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
