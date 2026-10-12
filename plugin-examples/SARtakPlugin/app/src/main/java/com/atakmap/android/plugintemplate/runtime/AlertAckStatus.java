package com.atakmap.android.plugintemplate.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Who has and hasn't acknowledged one alert, out of the team members it was
 * meant for. Shown on every alert card, not only the sender's, so a member
 * can see who else has stopped.
 */
public final class AlertAckStatus {

    private final List<String> acknowledged;
    private final List<String> waiting;

    private AlertAckStatus(List<String> acknowledged, List<String> waiting) {
        this.acknowledged = Collections.unmodifiableList(acknowledged);
        this.waiting = Collections.unmodifiableList(waiting);
    }

    /**
     * @param recipients intended recipients as UID to callsign, in display
     *        order; the sender must already be left out
     * @param selfUid this device's UID, shown as "you"
     */
    public static AlertAckStatus of(SearchAlertMessage alert,
            Collection<SearchAlertMessage> messages,
            Map<String, String> recipients, String selfUid) {
        Set<String> ackUids = new LinkedHashSet<>();
        if (alert != null && messages != null) {
            for (SearchAlertMessage message : messages) {
                // An ack older than the alert belongs to an earlier send.
                if (message.getAlertId().equals(alert.getAlertId())
                        && SearchAlertMessage.ACTION_ACK.equals(
                                message.getAction())
                        && message.getCreated() >= alert.getCreated())
                    ackUids.add(message.getAckUid());
            }
        }
        List<String> acknowledged = new ArrayList<>();
        List<String> waiting = new ArrayList<>();
        for (Map.Entry<String, String> recipient : recipients.entrySet()) {
            String uid = recipient.getKey();
            String name = uid.equals(selfUid) ? "you"
                    : recipient.getValue();
            (ackUids.contains(uid) ? acknowledged : waiting).add(name);
        }
        return new AlertAckStatus(acknowledged, waiting);
    }

    public List<String> getAcknowledged() { return acknowledged; }
    public List<String> getWaiting() { return waiting; }

    /** One line for the Home tab, e.g. "1/3 acknowledged | Waiting: B, C". */
    public String summary() {
        String counts = acknowledged.size() + "/" + total()
                + " acknowledged";
        return waiting.isEmpty() ? counts
                : counts + " | Waiting: " + join(waiting);
    }

    /** The alert card's lines: who has acknowledged and who hasn't yet. */
    public String detail() {
        if (total() == 0)
            return "No other team members to acknowledge";
        return "Acknowledged (" + acknowledged.size() + "/" + total()
                + "): " + (acknowledged.isEmpty() ? "nobody yet"
                        : join(acknowledged))
                + "\nWaiting: " + (waiting.isEmpty() ? "nobody"
                        : join(waiting));
    }

    private int total() {
        return acknowledged.size() + waiting.size();
    }

    private static String join(List<String> names) {
        StringBuilder builder = new StringBuilder();
        for (String name : names) {
            if (builder.length() > 0)
                builder.append(", ");
            builder.append(name);
        }
        return builder.toString();
    }
}
