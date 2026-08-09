package com.atakmap.android.plugintemplate.runtime;

import android.content.Context;
import android.location.Location;

import com.atakmap.android.plugintemplate.database.LocationRepository;
import com.atakmap.android.plugintemplate.grid.SearchTrackManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Unit tests for RawGnssCaptureManager#handleLocation: the orchestration
 * around a raw GNSS fix - resolving identity, correlating with the active
 * track session (starting one if needed), and persisting the raw capture.
 * <p>
 * The LocationManager registration in start()/stop() is not covered here -
 * it depends on runtime permission grants that are awkward to fake under
 * Robolectric without a manifest, so this focuses on the callback logic that
 * fires once a Location has already been produced.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, manifest = Config.NONE)
public class RawGnssCaptureManagerTest {

    private static final IdentityManager.Identity RESOLVED_IDENTITY =
            new IdentityManager.Identity("uid1", "Alpha", "Identity: Alpha", true);
    private static final IdentityManager.Identity UNRESOLVED_IDENTITY =
            new IdentityManager.Identity(null, null, "Identity unavailable", false);

    private IdentityManager identityManager;
    private SearchTrackManager trackManager;
    private LocationRepository locationRepository;
    private RawGnssCaptureManager manager;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        identityManager = mock(IdentityManager.class);
        trackManager = mock(SearchTrackManager.class);
        locationRepository = mock(LocationRepository.class);
        manager = new RawGnssCaptureManager(context, identityManager, trackManager,
                locationRepository, null);
    }

    @Test
    public void handleLocation_withActiveSession_insertsUnderThatSession() {
        when(identityManager.resolveIdentity()).thenReturn(RESOLVED_IDENTITY);
        when(trackManager.getActiveSessionId()).thenReturn("session1");

        manager.handleLocation(gpsFix());

        verify(locationRepository).insertRaw(eq("uid1"), eq("Alpha"),
                eq("session1"), any(RawGnssCapture.class));
        verify(trackManager, never()).startOrResume(any(), any());
    }

    @Test
    public void handleLocation_withNoActiveSession_startsSessionBeforeInserting() {
        when(identityManager.resolveIdentity()).thenReturn(RESOLVED_IDENTITY);
        when(trackManager.getActiveSessionId()).thenReturn(null, "session2");

        manager.handleLocation(gpsFix());

        verify(trackManager).startOrResume("uid1", "Alpha");
        verify(locationRepository).insertRaw(eq("uid1"), eq("Alpha"),
                eq("session2"), any(RawGnssCapture.class));
    }

    @Test
    public void handleLocation_withUnresolvedIdentity_doesNotPersistAnything() {
        when(identityManager.resolveIdentity()).thenReturn(UNRESOLVED_IDENTITY);

        manager.handleLocation(gpsFix());

        verify(trackManager, never()).startOrResume(any(), any());
        verify(locationRepository, never())
                .insertRaw(any(), any(), any(), any());
    }

    @Test
    public void handleLocation_notifiesListenerWithTheCapturedSnapshot() {
        when(identityManager.resolveIdentity()).thenReturn(RESOLVED_IDENTITY);
        when(trackManager.getActiveSessionId()).thenReturn("session1");

        RawGnssCaptureManager.Listener listener = mock(RawGnssCaptureManager.Listener.class);
        RawGnssCaptureManager listeningManager = new RawGnssCaptureManager(
                RuntimeEnvironment.getApplication(), identityManager, trackManager,
                locationRepository, listener);

        listeningManager.handleLocation(gpsFix());

        ArgumentCaptor<RawGnssCapture> captor = ArgumentCaptor.forClass(RawGnssCapture.class);
        verify(listener).onRawGnssCaptured(captor.capture());
        assertEquals(-27.4705, captor.getValue().getLatitude(), 0.0001);
        assertEquals("gps", captor.getValue().getProvider());
    }

    @Test
    public void isRunning_defaultsFalse() {
        assertFalse(manager.isRunning());
    }

    private static Location gpsFix() {
        Location location = new Location("gps");
        location.setLatitude(-27.4705);
        location.setLongitude(153.0260);
        location.setAltitude(42.0);
        location.setAccuracy(5.0f);
        location.setTime(1700000000000L);
        return location;
    }
}
