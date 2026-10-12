package com.atakmap.android.plugintemplate.grid;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Which legs between consecutive fixes count towards track distance. */
public class SearchTrackDistanceTest {

    private static final double BRISBANE_LAT = -27.483137;
    private static final double BRISBANE_LNG = 153.009617;
    private static final long T0 = 1_791_000_000_000L;

    /** About 33 m north of the Brisbane fix. */
    private static final double STEP_LAT = BRISBANE_LAT + 0.0003;

    @Test
    public void walkingPace_counts() {
        double leg = SearchTrackManager.legDistance(BRISBANE_LAT,
                BRISBANE_LNG, T0, STEP_LAT, BRISBANE_LNG, T0 + 4000L);

        assertEquals(33.4, leg, 0.5);
    }

    @Test
    public void jumpAcrossThePacific_inAFewSeconds_doesNotCount() {
        assertEquals(0.0, SearchTrackManager.legDistance(BRISBANE_LAT,
                BRISBANE_LNG, T0, 37.4220, -122.0841, T0 + 5000L), 0.0);
    }

    @Test
    public void longMoveAfterALongGap_stillCounts() {
        // 5 km over an hour with no fixes is 1.4 m/s, so it is real travel.
        double leg = SearchTrackManager.legDistance(BRISBANE_LAT,
                BRISBANE_LNG, T0, BRISBANE_LAT + 0.045, BRISBANE_LNG,
                T0 + 3_600_000L);

        assertEquals(5004.0, leg, 10.0);
    }

    @Test
    public void missingOrOutOfOrderTimestamps_areTreatedAsOneSecondApart() {
        assertEquals(33.4, SearchTrackManager.legDistance(BRISBANE_LAT,
                BRISBANE_LNG, 0L, STEP_LAT, BRISBANE_LNG, 0L), 0.5);
        assertEquals(33.4, SearchTrackManager.legDistance(BRISBANE_LAT,
                BRISBANE_LNG, T0 + 10L, STEP_LAT, BRISBANE_LNG, T0), 0.5);
        assertEquals(0.0, SearchTrackManager.legDistance(BRISBANE_LAT,
                BRISBANE_LNG, 0L, BRISBANE_LAT + 0.01, BRISBANE_LNG, 0L),
                0.0);
    }

    @Test
    public void justUnderTheSpeedLimit_counts_justOver_doesNot() {
        // 0.0013 degrees of latitude is about 145 m.
        double near = SearchTrackManager.legDistance(BRISBANE_LAT,
                BRISBANE_LNG, T0, BRISBANE_LAT + 0.0013, BRISBANE_LNG,
                T0 + 1000L);
        double far = SearchTrackManager.legDistance(BRISBANE_LAT,
                BRISBANE_LNG, T0, BRISBANE_LAT + 0.0014, BRISBANE_LNG,
                T0 + 1000L);

        assertEquals(144.6, near, 0.5);
        assertEquals(0.0, far, 0.0);
    }
}
