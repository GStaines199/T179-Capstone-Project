package com.atakmap.android.plugintemplate.runtime;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Which cell ids can be folded into a run. The parser is hand-written for
 * speed, so it must accept and refuse exactly what the old pattern
 * {@code ^utm-([^-]+)-c100-e(\d+)-n(\d+)$} plus the rebuild check did.
 */
public class RouteCellRunsParseTest {

    /** A run forms only when both ids parse and are neighbours. */
    private static boolean foldsWithNeighbour(String id, String neighbour) {
        return RouteCellRuns.encode(Arrays.asList(id, neighbour)).size() == 1;
    }

    @Test
    public void canonicalIdsFold() {
        assertEquals(true, foldsWithNeighbour("utm-56J-c100-e500000-n6960000",
                "utm-56J-c100-e500000-n6960100"));
        assertEquals(true, foldsWithNeighbour("utm-56J-c100-e0-n0",
                "utm-56J-c100-e100-n0"));
    }

    @Test
    public void malformedIdsAreSentOnTheirOwn() {
        String neighbour = "utm-56J-c100-e500100-n6960000";
        List<String> bad = Arrays.asList(
                "utm-56J-c100-e0500000-n6960000",   // leading zero
                "utm-56J-c100-e500000-n06960000",
                "UTM-56J-c100-e500000-n6960000",    // prefix case
                "utm--c100-e500000-n6960000",       // empty zone
                "utm-56-J-c100-e500000-n6960000",   // dash in zone
                "utm-56J-c50-e500000-n6960000",     // other cell size
                "utm-56J-c100-e-n6960000",          // no easting digits
                "utm-56J-c100-e500000-n",           // no northing digits
                "utm-56J-c100-e500000-n6960000x",   // trailing text
                "utm-56J-c100-e50a000-n6960000",
                "");
        for (String id : bad)
            assertEquals(id, false, foldsWithNeighbour(id, neighbour));
    }

    @Test
    public void unparsedIdsSurviveARoundTripUnchanged() {
        List<String> route = Arrays.asList("custom-cell",
                "utm-56J-c100-e0500000-n6960000");
        assertEquals(route, RouteCellRuns.decode(RouteCellRuns.encode(route)));
        assertEquals(Collections.<String>emptyList(),
                RouteCellRuns.encode(Collections.<String>emptyList()));
    }
}
