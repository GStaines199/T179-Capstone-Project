package com.atakmap.android.plugintemplate.runtime;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/** The acknowledgement lines on alert cards and the Home tab. */
public class AlertAckStatusTest {

    private static final long SENT = 1_000_000L;

    private static SearchAlertMessage alert() {
        return new SearchAlertMessage("a1-active",
                SearchAlertMessage.ACTION_ACTIVE, "a1",
                SearchAlertMessage.TYPE_HOLD_POSITION, "Hold Position", "",
                "team", "Team", "lead", "Lead", "", "", Double.NaN,
                Double.NaN, true, SENT, "op");
    }

    private static SearchAlertMessage ack(String alertId, String uid,
            String callsign, long created) {
        return new SearchAlertMessage(alertId + "-ack-" + uid,
                SearchAlertMessage.ACTION_ACK, alertId,
                SearchAlertMessage.TYPE_HOLD_POSITION, "Hold Position",
                "Acknowledged", "team", "Team", "lead", "Lead", uid,
                callsign, Double.NaN, Double.NaN, true, created, "op");
    }

    private static Map<String, String> recipients(String... uidCallsign) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < uidCallsign.length; i += 2)
            map.put(uidCallsign[i], uidCallsign[i + 1]);
        return map;
    }

    @Test
    public void listsWhoAcknowledgedAndWhoIsWaiting() {
        AlertAckStatus status = AlertAckStatus.of(alert(),
                Arrays.asList(alert(), ack("a1", "u2", "Bravo", SENT + 5)),
                recipients("u1", "Alpha", "u2", "Bravo", "u3", "Charlie"),
                "lead");

        assertEquals(Collections.singletonList("Bravo"),
                status.getAcknowledged());
        assertEquals(Arrays.asList("Alpha", "Charlie"), status.getWaiting());
        assertEquals("1/3 acknowledged | Waiting: Alpha, Charlie",
                status.summary());
        assertEquals("Acknowledged (1/3): Bravo\nWaiting: Alpha, Charlie",
                status.detail());
    }

    @Test
    public void recipientSeesThemselfAsYou() {
        AlertAckStatus status = AlertAckStatus.of(alert(),
                Collections.singletonList(ack("a1", "u1", "Alpha", SENT)),
                recipients("u1", "Alpha", "u2", "Bravo"), "u1");

        assertEquals("Acknowledged (1/2): you\nWaiting: Bravo",
                status.detail());
    }

    @Test
    public void ignoresAcksForOtherAlertsAndFromBeforeTheAlert() {
        AlertAckStatus status = AlertAckStatus.of(alert(),
                Arrays.asList(ack("a0", "u1", "Alpha", SENT + 5),
                        ack("a1", "u2", "Bravo", SENT - 1)),
                recipients("u1", "Alpha", "u2", "Bravo"), "lead");

        assertEquals("Acknowledged (0/2): nobody yet\nWaiting: Alpha, Bravo",
                status.detail());
    }

    @Test
    public void ignoresAcksFromNonRecipients_andCountsRepeatAcksOnce() {
        AlertAckStatus status = AlertAckStatus.of(alert(),
                Arrays.asList(ack("a1", "u1", "Alpha", SENT + 1),
                        ack("a1", "u1", "Alpha", SENT + 9),
                        ack("a1", "gone", "Departed", SENT + 2)),
                recipients("u1", "Alpha"), "lead");

        assertEquals("1/1 acknowledged", status.summary());
        assertEquals("Acknowledged (1/1): Alpha\nWaiting: nobody",
                status.detail());
    }

    @Test
    public void noRecipients_saysSo() {
        AlertAckStatus status = AlertAckStatus.of(alert(),
                Collections.<SearchAlertMessage>emptyList(), recipients(),
                "lead");

        assertEquals("0/0 acknowledged", status.summary());
        assertEquals("No other team members to acknowledge", status.detail());
    }
}
