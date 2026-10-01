package com.atakmap.android.plugintemplate.runtime;

import com.atakmap.android.plugintemplate.grid.SearchTeamMember.ConnectionStatus;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for which connectivity transitions trigger an active warning.
 * <p>
 * Dependencies (build.gradle):
 *   testImplementation 'junit:junit:4.13.2'
 */
public class ConnectionWarningPolicyTest {

    @Test
    public void connectedToStale_warns() {
        assertTrue(ConnectionWarningPolicy.shouldWarn(
                ConnectionStatus.CONNECTED, ConnectionStatus.STALE));
    }

    @Test
    public void connectedToReconnecting_warns() {
        assertTrue(ConnectionWarningPolicy.shouldWarn(
                ConnectionStatus.CONNECTED, ConnectionStatus.RECONNECTING));
    }

    @Test
    public void connectedToDisconnected_warns() {
        assertTrue(ConnectionWarningPolicy.shouldWarn(
                ConnectionStatus.CONNECTED, ConnectionStatus.DISCONNECTED));
    }

    @Test
    public void staleToDisconnected_stillWorse_warnsAgain() {
        assertTrue(ConnectionWarningPolicy.shouldWarn(
                ConnectionStatus.STALE, ConnectionStatus.DISCONNECTED));
    }

    @Test
    public void staleToReconnecting_sameTier_doesNotWarnAgain() {
        assertFalse(ConnectionWarningPolicy.shouldWarn(
                ConnectionStatus.STALE, ConnectionStatus.RECONNECTING));
    }

    @Test
    public void recoveryToConnected_doesNotWarn() {
        assertFalse(ConnectionWarningPolicy.shouldWarn(
                ConnectionStatus.DISCONNECTED, ConnectionStatus.CONNECTED));
        assertFalse(ConnectionWarningPolicy.shouldWarn(
                ConnectionStatus.STALE, ConnectionStatus.CONNECTED));
    }

    @Test
    public void unchangedStatus_doesNotWarn() {
        assertFalse(ConnectionWarningPolicy.shouldWarn(
                ConnectionStatus.STALE, ConnectionStatus.STALE));
        assertFalse(ConnectionWarningPolicy.shouldWarn(
                ConnectionStatus.CONNECTED, ConnectionStatus.CONNECTED));
    }

    @Test
    public void firstSighting_neverWarnsEvenIfAlreadyBad() {
        assertFalse(ConnectionWarningPolicy.shouldWarn(null,
                ConnectionStatus.DISCONNECTED));
    }

    @Test
    public void messageFor_disconnected_saysDisconnected() {
        assertEquals("Alpha One has disconnected", ConnectionWarningPolicy
                .messageFor("Alpha One", ConnectionStatus.DISCONNECTED));
    }

    @Test
    public void messageFor_staleOrReconnecting_saysStale() {
        assertEquals("Alpha One's connection is stale", ConnectionWarningPolicy
                .messageFor("Alpha One", ConnectionStatus.STALE));
        assertEquals("Alpha One's connection is stale", ConnectionWarningPolicy
                .messageFor("Alpha One", ConnectionStatus.RECONNECTING));
    }

    @Test
    public void messageFor_withNoCallsign_fallsBackToGenericLabel() {
        assertEquals("A team member has disconnected", ConnectionWarningPolicy
                .messageFor("", ConnectionStatus.DISCONNECTED));
        assertEquals("A team member has disconnected", ConnectionWarningPolicy
                .messageFor(null, ConnectionStatus.DISCONNECTED));
    }
}
