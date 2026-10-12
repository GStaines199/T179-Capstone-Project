package com.atakmap.android.plugintemplate.runtime;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StepTimerTest {

    private static final long MS = 1_000_000L;

    @Test
    public void reportsTotalAndEachStepSinceThePreviousMark() {
        StepTimer timer = new StepTimer(0L);
        timer.mark("connect", 2 * MS);
        timer.mark("contacts", 20 * MS);
        timer.mark("arrange", 23 * MS + MS / 2);

        assertEquals("ms=23.5 connect=2.0 contacts=18.0 arrange=3.5",
                timer.summary());
    }

    @Test
    public void tinyStepsAreLeftOutButStillCount() {
        StepTimer timer = new StepTimer(0L);
        timer.mark("connect", MS / 20);
        timer.mark("contacts", 10 * MS);

        assertEquals("ms=10.0 contacts=10.0", timer.summary());
    }

    @Test
    public void noMarksIsZero() {
        assertEquals("ms=0.0", new StepTimer(5L).summary());
    }
}
