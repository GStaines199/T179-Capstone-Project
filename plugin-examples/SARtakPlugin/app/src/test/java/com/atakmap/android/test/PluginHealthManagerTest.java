package com.atakmap.android.test;

import com.atakmap.android.plugintemplate.runtime.PluginHealthManager;
import com.atakmap.android.plugintemplate.runtime.PluginHealthState;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PluginHealthManagerTest {

    @Test
    public void getState_whenStopped_isInactive() {
        PluginHealthManager manager = new PluginHealthManager();

        assertEquals(PluginHealthState.INACTIVE, manager.getState());
    }

    @Test
    public void getState_whenStartedButNoFreshLocation_isDegraded() {
        PluginHealthManager manager = readyManager();

        assertEquals(PluginHealthState.DEGRADED, manager.getState());
    }

    @Test
    public void getState_whenStartedAndFreshLocation_isActive() {
        PluginHealthManager manager = readyManager();
        manager.recordLocationSuccess(System.currentTimeMillis(), 6.0);

        assertEquals(PluginHealthState.ACTIVE, manager.getState());
    }

    private PluginHealthManager readyManager() {
        PluginHealthManager manager = new PluginHealthManager();
        manager.start();
        manager.setStorageReady(true, "ready");
        manager.setIdentityResolved(true, "identity");
        manager.setTrackingActive(true);
        return manager;
    }
}
