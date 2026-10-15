package com.atakmap.android.plugintemplate.runtime;

import com.atakmap.android.plugintemplate.grid.SearchTeamMember.ConnectionStatus;

/**
 * Decides whether a team member's connectivity change deserves an active
 * warning (a Toast at the moment it happens) rather than just the passive
 * roster text that already shows the current status on every refresh.
 * <p>
 * Kept ATAK/Android-type-free so the transition rule is unit-testable - the
 * actual Toast call lives in PluginTemplateDropDownReceiver, which tracks
 * each member's last-seen status and calls shouldWarn when it changes.
 */
public final class ConnectionWarningPolicy {

    private ConnectionWarningPolicy() {
    }

    /**
     * Only fires when connectivity gets strictly worse than it was a moment
     * ago. Never fires on first sighting (previous == null) - a member who
     * was already stale before SARtak started watching should not trigger a
     * warning the instant the roster loads - and never fires on recovery.
     */
    public static boolean shouldWarn(ConnectionStatus previous,
            ConnectionStatus current) {
        if (previous == null || current == null)
            return false;
        return severity(current) > severity(previous);
    }

    public static String messageFor(String callsign,
            ConnectionStatus current) {
        String who = callsign == null || callsign.trim().length() == 0
                ? "A team member" : callsign;
        if (current == ConnectionStatus.DISCONNECTED)
            return who + " has disconnected";
        return who + "'s connection is stale";
    }

    /**
     * STALE and RECONNECTING are the same tier: different code paths reach
     * roughly the same "not current, not yet given up" state for an ATAK
     * contact versus a Ditto presence timeout respectively. DISCONNECTED is
     * strictly worse than either.
     */
    private static int severity(ConnectionStatus status) {
        if (status == null)
            return 0;
        switch (status) {
            case DISCONNECTED:
                return 2;
            case STALE:
            case RECONNECTING:
                return 1;
            case CONNECTED:
            default:
                return 0;
        }
    }
}
