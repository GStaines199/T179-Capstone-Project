package com.atakmap.android.plugintemplate.grid;

/**
 * What leaving an operation does to this device's search line. Leaving used
 * to clear only the saved copy, so a leader's line kept running in memory
 * and turned up ACTIVE in the next operation without anyone starting it.
 */
public final class SearchLineOperationExit {

    /** Sends the END for a line this device was running. */
    public interface EndPublisher {
        void publishEnd();
    }

    private SearchLineOperationExit() {
    }

    /**
     * Ends the line and forgets the saved copy. A line this device owned is
     * ended for the old team too, so members are not left following it until
     * its sync messages expire. Returns true when an END was published.
     */
    public static boolean endForLeave(SearchLineManager manager,
            SearchLineStateStore store, boolean teamCreated,
            EndPublisher publisher) {
        boolean ownLine = teamCreated && manager.isStarted()
                && !manager.isRemoteControlled();
        manager.end();
        if (ownLine && publisher != null)
            publisher.publishEnd();
        store.clear();
        return ownLine;
    }
}
