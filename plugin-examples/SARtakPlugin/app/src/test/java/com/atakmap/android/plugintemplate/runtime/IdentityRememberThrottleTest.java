package com.atakmap.android.plugintemplate.runtime;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The self row is written when it changes or once a minute, not on every call. */
public class IdentityRememberThrottleTest {

    private static IdentityManager.Identity identity(String callsign) {
        return new IdentityManager.Identity("ANDROID-1", callsign,
                "Identity: " + callsign, true);
    }

    @Test
    public void firstCallIsDue() {
        assertTrue(new IdentityManager.RememberThrottle()
                .isDue(identity("QA-LEAD"), "sdk", 1_000L));
    }

    @Test
    public void sameIdentityWithinAMinuteIsNotDue() {
        IdentityManager.RememberThrottle throttle =
                new IdentityManager.RememberThrottle();
        throttle.saved(identity("QA-LEAD"), "sdk", 1_000L);

        assertFalse(throttle.isDue(identity("QA-LEAD"), "sdk", 50_000L));
        assertTrue(throttle.isDue(identity("QA-LEAD"), "sdk",
                1_000L + IdentityManager.RememberThrottle.INTERVAL_MS));
    }

    @Test
    public void changedIdentityIsDueStraightAway() {
        IdentityManager.RememberThrottle throttle =
                new IdentityManager.RememberThrottle();
        throttle.saved(identity("QA-LEAD"), "sdk", 1_000L);

        assertTrue(throttle.isDue(identity("Rescue 1"), "sdk", 2_000L));
        assertTrue(throttle.isDue(identity("QA-LEAD"), "other", 2_000L));
    }
}
