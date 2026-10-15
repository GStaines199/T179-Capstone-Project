package com.atakmap.android.plugintemplate.runtime;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Unit tests for which alert types vibrate and with what pattern.
 * <p>
 * Dependencies (build.gradle):
 *   testImplementation 'junit:junit:4.13.2'
 */
public class AlertVibrationPolicyTest {

    @Test
    public void emergencyStop_usesTheUrgentTriplePattern() {
        assertArrayEquals(new long[] { 0, 400, 150, 400, 150, 400 },
                AlertVibrationPolicy.patternFor(
                        SearchAlertMessage.TYPE_EMERGENCY_STOP));
    }

    @Test
    public void holdPosition_usesTheSingleBuzzPattern() {
        assertArrayEquals(new long[] { 0, 400 },
                AlertVibrationPolicy.patternFor(
                        SearchAlertMessage.TYPE_HOLD_POSITION));
    }

    @Test
    public void requestLeader_alsoRequiresHalt_soItVibrates() {
        assertArrayEquals(new long[] { 0, 400 },
                AlertVibrationPolicy.patternFor(
                        SearchAlertMessage.TYPE_REQUEST_LEADER));
    }

    @Test
    public void resumeSearch_doesNotRequireHalt_soItDoesNotVibrate() {
        assertEquals(0, AlertVibrationPolicy.patternFor(
                SearchAlertMessage.TYPE_RESUME_SEARCH).length);
    }

    @Test
    public void unknownAlertType_doesNotVibrate() {
        assertEquals(0, AlertVibrationPolicy.patternFor("something_new")
                .length);
    }

    @Test
    public void nullAlertType_doesNotVibrate() {
        assertEquals(0, AlertVibrationPolicy.patternFor(null).length);
    }
}
