package com.atakmap.android.plugintemplate.runtime;

import com.atakmap.android.plugintemplate.grid.SearchGridStateStore;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class SearchAreaAssignmentTest {

    @Test
    public void newestUsesTimestampInsteadOfObserverOrder() {
        SearchAreaAssignment newest = assignment(200L, 504000.0);
        SearchAreaAssignment stale = assignment(100L, 502000.0);

        SearchAreaAssignment selected = SearchAreaAssignment.newest(
                newest, stale);

        assertSame(newest, selected);
        assertEquals(504000.0, selected.getArea().centerEasting, 0.0);
    }

    @Test
    public void newestAcceptsUpdatedGeometryForSameAssignment() {
        SearchAreaAssignment stale = assignment(100L, 502000.0);
        SearchAreaAssignment newest = assignment(200L, 504000.0);

        assertSame(newest, SearchAreaAssignment.newest(stale, newest));
    }

    private SearchAreaAssignment assignment(long updatedAt,
            double centerEasting) {
        SearchGridStateStore.PlannedAreaRecord area =
                new SearchGridStateStore.PlannedAreaRecord(
                        "CIRCLE", "56J", centerEasting, 6966000.0,
                        centerEasting - 5000.0, 6961000.0,
                        centerEasting + 5000.0, 6971000.0,
                        5000.0, 10000.0, 10000.0);
        return new SearchAreaAssignment("area-operation-OP-1", "OP-1",
                "", "Operation search area", "HQ-UID", "HQ",
                "HQ-UID", "HQ", SearchAreaAssignment.STATUS_IN_PROGRESS,
                updatedAt, area);
    }
}
