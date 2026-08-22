package com.atakmap.android.plugintemplate.runtime;

import android.content.Context;
import android.database.Cursor;
import android.location.Location;

import com.atakmap.android.plugintemplate.database.DatabaseHelper;
import com.atakmap.android.plugintemplate.database.LocationRepository;
import com.atakmap.android.plugintemplate.grid.SearchTrackManager;

import org.junit.After;
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for raw GNSS capture: RawGnssCaptureManager#handleLocation - the
 * orchestration around a raw GNSS fix (resolving identity, respecting the
 * recording toggle, correlating with the active track session, persisting
 * the capture, notifying listeners) - plus an end-to-end check that the
 * accuracy metadata it hands to LocationRepository survives a real SQLite
 * round trip unmodified, per the Sprint 1 "preserve accuracy metadata
 * without modification" requirement.
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
        when(trackManager.isRecording()).thenReturn(true);
        manager = new RawGnssCaptureManager(context, identityManager, trackManager,
                locationRepository, null);
    }

    // -------------------------------------------------------------------
    // Orchestration (mocked dependencies)
    // -------------------------------------------------------------------

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
    public void handleLocation_whenNotRecording_doesNotPersistAnything() {
        when(identityManager.resolveIdentity()).thenReturn(RESOLVED_IDENTITY);
        when(trackManager.isRecording()).thenReturn(false);

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

    // -------------------------------------------------------------------
    // Persistence round trip (real LocationRepository/DatabaseHelper) -
    // confirms accuracy metadata reaches the location_points table
    // unmodified for the full handleLocation() -> insertRaw() pipeline.
    // -------------------------------------------------------------------

    private DatabaseHelper dbHelper;

    @After
    public void tearDown() {
        if (dbHelper != null)
            dbHelper.close();
    }

    @Test
    public void handleLocation_persistsRawAccuracyMetadataUnmodified() {
        RawGnssCaptureManager realManager = managerWithRealRepository();
        when(identityManager.resolveIdentity()).thenReturn(RESOLVED_IDENTITY);
        when(trackManager.getActiveSessionId()).thenReturn("sessionRaw1");

        Location location = gpsFix();
        location.setVerticalAccuracyMeters(2.5f);
        location.setBearingAccuracyDegrees(3.5f);
        location.setSpeedAccuracyMetersPerSecond(0.5f);

        realManager.handleLocation(location);

        RawAccuracyRow row = queryRawColumns("sessionRaw1");
        assertEquals("gps", row.provider);
        assertEquals(2.5, row.verticalAccuracy, 0.0001);
        assertEquals(3.5, row.bearingAccuracy, 0.0001);
        assertEquals(0.5, row.speedAccuracy, 0.0001);
    }

    @Test
    public void handleLocation_withUnavailableAccuracyExtras_persistsThemAsNull() {
        RawGnssCaptureManager realManager = managerWithRealRepository();
        when(identityManager.resolveIdentity()).thenReturn(RESOLVED_IDENTITY);
        when(trackManager.getActiveSessionId()).thenReturn("sessionRaw2");

        Location location = new Location("network");
        location.setLatitude(-27.47);
        location.setLongitude(153.02);
        location.setTime(1000L);
        // No vertical/bearing/speed accuracy set on this fix.

        realManager.handleLocation(location);

        RawAccuracyRow row = queryRawColumns("sessionRaw2");
        assertEquals("network", row.provider);
        assertNull(row.verticalAccuracy);
        assertNull(row.bearingAccuracy);
        assertNull(row.speedAccuracy);
    }

    private RawGnssCaptureManager managerWithRealRepository() {
        dbHelper = new DatabaseHelper(RuntimeEnvironment.getApplication());
        LocationRepository realLocationRepository = new LocationRepository(dbHelper);
        return new RawGnssCaptureManager(RuntimeEnvironment.getApplication(),
                identityManager, trackManager, realLocationRepository, null);
    }

    private RawAccuracyRow queryRawColumns(String sessionId) {
        Cursor cursor = dbHelper.getReadableDatabase().query(
                "location_points",
                new String[]{"provider", "vertical_accuracy_meters",
                        "bearing_accuracy_degrees", "speed_accuracy_mps"},
                "session_id = ?", new String[]{sessionId},
                null, null, null);
        assertTrue(cursor.moveToFirst());
        RawAccuracyRow row = new RawAccuracyRow();
        row.provider = cursor.getString(0);
        row.verticalAccuracy = cursor.isNull(1) ? null : cursor.getDouble(1);
        row.bearingAccuracy = cursor.isNull(2) ? null : cursor.getDouble(2);
        row.speedAccuracy = cursor.isNull(3) ? null : cursor.getDouble(3);
        cursor.close();
        return row;
    }

    private static class RawAccuracyRow {
        String provider;
        Double verticalAccuracy;
        Double bearingAccuracy;
        Double speedAccuracy;
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
