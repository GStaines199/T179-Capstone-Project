package com.atakmap.android.plugintemplate.runtime;

import android.os.Handler;

import com.atakmap.android.plugintemplate.grid.SearchTrackManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for LocationCaptureManager.
 *
 * The ATAK plumbing (MapView reading) lives behind the
 * {@link LocationCaptureManager.LocationFixSource} seam; the capture decision
 * logic is exercised through {@link LocationCaptureManager#captureWith}. The
 * Handler is injected so the object can be constructed on a plain JVM without
 * an Android Looper.
 *
 * Dependencies (build.gradle):
 *   testImplementation 'junit:junit:4.13.2'
 *   testImplementation 'org.mockito:mockito-core:4.11.0'
 */
@RunWith(MockitoJUnitRunner.class)
public class LocationCaptureManagerTest {

    private static final String UID = "ANDROID-1234567890abcdef";
    private static final String CALLSIGN = "ALPHA";

    @Mock
    private IdentityManager identityManager;

    @Mock
    private SearchTrackManager trackManager;

    @Mock
    private Handler handler;

    private PluginHealthManager healthManager;

    private LocationCaptureManager.LocationFixSource fixSource;

    private LocationCaptureManager.LocationFix fix;

    private int listenerCalls;

    private long now;

    private LocationCaptureManager.Listener listener;

    private LocationCaptureManager manager;

    @Before
    public void setUp() {
        healthManager = new PluginHealthManager();
        healthManager.start();
        healthManager.setStorageReady(true, "ready");

        now = System.currentTimeMillis();
        fix = LocationCaptureManager.LocationFix.available(-27.4705, 153.0260,
                42.0, 7.5, now, "GPS", 90.0, 1.2);
        fixSource = new LocationCaptureManager.LocationFixSource() {
            @Override
            public LocationCaptureManager.LocationFix readFix() {
                return fix;
            }
        };

        listenerCalls = 0;
        listener = new LocationCaptureManager.Listener() {
            @Override
            public void onLocationCaptured() {
                listenerCalls++;
            }
        };

        when(trackManager.isRecording()).thenReturn(true);

        manager = new LocationCaptureManager(fixSource, identityManager,
                trackManager, healthManager, listener, handler);
    }

    private IdentityManager.Identity resolvedIdentity() {
        return new IdentityManager.Identity(UID, CALLSIGN, "Identity: ALPHA",
                true);
    }

    // -------------------------------------------------------------------------
    // Identity not resolved
    // -------------------------------------------------------------------------

    @Test
    public void captureWith_identityUnresolved_recordsFailureAndNotifies() {
        IdentityManager.Identity unresolved = new IdentityManager.Identity(
                null, null, "Using device identity fallback", false);

        manager.captureWith(unresolved, fix);

        assertEquals("Identity unavailable; tracking degraded",
                healthManager.getLocationMessage());
        assertEquals(1, listenerCalls);
        verify(trackManager, never()).recordLocation(anyString(), anyString(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyLong());
    }

    // -------------------------------------------------------------------------
    // Fix not available
    // -------------------------------------------------------------------------

    @Test
    public void captureWith_fixUnavailable_recordsFailureAndNotifies() {
        LocationCaptureManager.LocationFix unavailable =
                LocationCaptureManager.LocationFix.unavailable("No GPS Signal");

        manager.captureWith(resolvedIdentity(), unavailable);

        assertEquals("No GPS Signal", healthManager.getLocationMessage());
        assertEquals(1, listenerCalls);
        verify(trackManager, never()).recordLocation(anyString(), anyString(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyLong());
    }

    // -------------------------------------------------------------------------
    // Successful capture
    //
    // The self-marker fix reaches health and status reporting only. It has
    // already been through ATAK's location fusion, so writing it to the track
    // would put a processed position in a log required to hold unmodified
    // device fixes; RawGnssCaptureManager is the only writer.
    // -------------------------------------------------------------------------

    @Test
    public void captureWith_success_reportsHealthAndWritesNoTrackPoint() {
        manager.captureWith(resolvedIdentity(), fix);

        verify(trackManager, never()).recordLocation(anyString(),
                anyString(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyLong());
        verify(trackManager).isRecording();
        assertEquals("GPS active | Source GPS | Accuracy 8 m",
                healthManager.getLocationMessage());
        assertEquals(PluginHealthState.ACTIVE, healthManager.getState());
    }

    @Test
    public void captureWith_success_notifiesListener() {
        manager.captureWith(resolvedIdentity(), fix);

        assertEquals(1, listenerCalls);
    }

    @Test
    public void captureWith_nullListener_noException() {
        LocationCaptureManager noListener = new LocationCaptureManager(
                fixSource, identityManager, trackManager, healthManager, null,
                handler);

        noListener.captureWith(resolvedIdentity(), fix);

        verify(trackManager, never()).recordLocation(anyString(),
                anyString(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyLong());
    }

    // -------------------------------------------------------------------------
    // captureNow delegation
    // -------------------------------------------------------------------------

    @Test
    public void captureNow_delegatesIdentityAndFix() {
        when(identityManager.resolveIdentity()).thenReturn(resolvedIdentity());

        manager.captureNow();

        verify(identityManager).resolveIdentity();
        verify(trackManager, never()).recordLocation(anyString(),
                anyString(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyLong());
        assertEquals(1, listenerCalls);
    }

    // -------------------------------------------------------------------------
    // start / stop scheduling
    // -------------------------------------------------------------------------

    @Test
    public void start_schedulesRepeatedCapture() {
        when(identityManager.resolveIdentity()).thenReturn(resolvedIdentity());

        manager.start();

        verify(handler).postDelayed(any(Runnable.class),
                eq(LocationCaptureManager.UPDATE_INTERVAL_MS));
    }

    @Test
    public void start_whenRunning_isIdempotent() {
        when(identityManager.resolveIdentity()).thenReturn(resolvedIdentity());

        manager.start();
        manager.start();

        // Only one postDelayed across both starts.
        verify(handler).postDelayed(any(Runnable.class),
                eq(LocationCaptureManager.UPDATE_INTERVAL_MS));
    }

    @Test
    public void stop_cancelsCallbacks() {
        when(identityManager.resolveIdentity()).thenReturn(resolvedIdentity());
        manager.start();

        manager.stop();

        verify(handler).removeCallbacks(any(Runnable.class));
    }
}
