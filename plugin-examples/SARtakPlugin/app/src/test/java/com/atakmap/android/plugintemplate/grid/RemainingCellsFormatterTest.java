package com.atakmap.android.plugintemplate.grid;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** The remaining-cells lines on the Home and Grid tabs. */
public class RemainingCellsFormatterTest {

    @Test
    public void area_showsCellsNotComplete_withPartialCount() {
        assertEquals("Area left: 35000 of 40401 cells not complete"
                        + " (401 partial)",
                RemainingCellsFormatter.areaRemaining(
                        new SearchGridManager.AreaProgress(5401, 401, 0,
                                40401)));
    }

    @Test
    public void area_withoutPartials_leavesTheBracketOut() {
        assertEquals("Area left: 961 of 961 cells not complete",
                RemainingCellsFormatter.areaRemaining(
                        new SearchGridManager.AreaProgress(0, 0, 3, 961)));
    }

    @Test
    public void area_allComplete_saysSo() {
        assertEquals("Area left: all 961 cells complete",
                RemainingCellsFormatter.areaRemaining(
                        new SearchGridManager.AreaProgress(961, 0, 0, 961)));
    }

    @Test
    public void area_missing_saysThereIsNoPlannedArea() {
        assertEquals("Area left: no planned area",
                RemainingCellsFormatter.areaRemaining(null));
    }

    @Test
    public void route_showsCellsLeft_andTheNextCell() {
        assertEquals("Route left: 580 of 620 cells\n"
                        + "Next route cell (#41): 56J 4997 69598",
                RemainingCellsFormatter.routeRemaining(
                        new RouteProgressTracker.Progress(620, 40, "cell-41",
                                41), "56J 4997 69598"));
    }

    @Test
    public void route_withoutDisplayText_fallsBackToTheCellId() {
        assertEquals("Route left: 3 of 3 cells\nNext route cell (#1): a",
                RemainingCellsFormatter.routeRemaining(
                        new RouteProgressTracker.Progress(3, 0, "a", 1),
                        null));
    }

    @Test
    public void route_allComplete_saysSo() {
        assertEquals("Route left: all 620 cells complete",
                RemainingCellsFormatter.routeRemaining(
                        new RouteProgressTracker.Progress(620, 620, null, 0),
                        null));
    }

    @Test
    public void route_missing_saysThereIsNoRoute() {
        assertEquals("Route left: no route planned",
                RemainingCellsFormatter.routeRemaining(null, null));
    }
}
