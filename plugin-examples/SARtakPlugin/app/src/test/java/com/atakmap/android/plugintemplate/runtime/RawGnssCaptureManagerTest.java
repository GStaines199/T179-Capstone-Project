package com.atakmap.android.plugintemplate.runtime;

import android.content.Context;
import android.database.Cursor;
import android.location.Location;

import com.atakmap.android.plugintemplate.database.DatabaseHelper;
import com.atakmap.android.plugintemplate.database.LocationRepository;
import com.atakmap.android.plugintemplate.database.TrackSessionRepository;
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
 * recording toggle, handing the fix to the track manager, notifying
 * listeners) - plus end-to-end checks against a real SearchTrackManager and
 * SQLite database: accuracy metadata survives the round trip unmodified, per
 * the Sprint 1 "preserve accuracy metadata without modification" requirement,
 * and the Track tab's distance follows the raw fixes as they arrive.
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
    private static final String OPERATION_ID = "op-raw";

    private IdentityManager identityManager;
    private SearchTrackManager trackManager;
    private RawGnssCaptureManager manager;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        identityManager = mock(IdentityManager.class);
        trackManager = mock(SearchTrackManager.class);
        when(trackManager.isRecording()).thenReturn(true);
        manager = new RawGnssCaptureManager(context, identityManager, trackManager,
                null);
    }

    // -------------------------------------------------------------------
    // Orchestration (mocked dependencies)
    // -------------------------------------------------------------------

    @Test
    public void handleLocation_recordsTheFixThroughTheTrackManager() {
        when(identityManager.resolveIdentity()).thenReturn(RESOLVED_IDENTITY);

        manager.handleLocation(gpsFix());

        verify(trackManager).recordRawCapture(eq("uid1"), eq("Alpha"),
                any(RawGnssCapture.class));
    }

    @Test
    public void handleLocation_withUnresolvedIdentity_doesNotPersistAnything() {
        when(identityManager.resolveIdentity()).thenReturn(UNRESOLVED_IDENTITY);

        manager.handleLocation(gpsFix());

        verify(trackManager, never()).recordRawCapture(any(), any(), any());
    }

    @Test
    public void handleLocation_whenNotRecording_doesNotPersistAnything() {
        when(identityManager.resolveIdentity()).thenReturn(RESOLVED_IDENTITY);
        when(trackManager.isRecording()).thenReturn(false);

        manager.handleLocation(gpsFix());

        verify(trackManager, never()).recordRawCapture(any(), any(), any());
    }

    @Test
    public void handleLocation_notifiesListenerWithTheCapturedSnapshot() {
        when(identityManager.resolveIdentity()).thenReturn(RESOLVED_IDENTITY);

        RawGnssCaptureManager.Listener listener = mock(RawGnssCaptureManager.Listener.class);
        RawGnssCaptureManager listeningManager = new RawGnssCaptureManager(
                RuntimeEnvironment.getApplication(), identityManager, trackManager,
                listener);

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
    // Persistence round trip (real SearchTrackManager, LocationRepository
    // and DatabaseHelper) for the full handleLocation() pipeline.
    // -------------------------------------------------------------------

    private DatabaseHelper dbHelper;
    private SearchTrackManager realTrackManager;

    @After
    public void tearDown() {
        if (dbHelper != null)
            dbHelper.close();
    }

    @Test
    public void handleLocation_persistsRawAccuracyMetadataUnmodified() {
        RawGnssCaptureManager realManager = managerWithRealTrackManager();
        when(identityManager.resolveIdentity()).thenReturn(RESOLVED_IDENTITY);

        Location location = gpsFix();
        location.setVerticalAccuracyMeters(2.5f);
        location.setBearingAccuracyDegrees(3.5f);
        location.setSpeedAccuracyMetersPerSecond(0.5f);

        realManager.handleLocation(location);

        RawAccuracyRow row = queryRawColumns(realTrackManager.getActiveSessionId());
        assertEquals("gps", row.provider);
        assertEquals(2.5, row.verticalAccuracy, 0.0001);
        assertEquals(3.5, row.bearingAccuracy, 0.0001);
        assertEquals(0.5, row.speedAccuracy, 0.0001);
    }

    @Test
    public void handleLocation_withUnavailableAccuracyExtras_persistsThemAsNull() {
        RawGnssCaptureManager realManager = managerWithRealTrackManager();
        when(identityManager.resolveIdentity()).thenReturn(RESOLVED_IDENTITY);

        Location location = new Location("network");
        location.setLatitude(-27.47);
        location.setLongitude(153.02);
        location.setTime(1000L);
        // No vertical/bearing/speed accuracy set on this fix.

        realManager.handleLocation(location);

        RawAccuracyRow row = queryRawColumns(realTrackManager.getActiveSessionId());
        assertEquals("network", row.provider);
        assertNull(row.verticalAccuracy);
        assertNull(row.bearingAccuracy);
        assertNull(row.speedAccuracy);
    }

    /**
     * The successor to the fused path's gap test: the raw writer stores one
     * row per fix Android delivers and never invents one in between, so two
     * fixes either side of an outage leave exactly two rows.
     */
    @Test
    public void handleLocation_storesOneRowPerFixAndNothingInBetween() {
        RawGnssCaptureManager realManager = managerWithRealTrackManager();
        when(identityManager.resolveIdentity()).thenReturn(RESOLVED_IDENTITY);

        Location first = gpsFix();
        first.setTime(1000L);
        Location second = gpsFix();
        second.setLatitude(-27.4800);
        second.setLongitude(153.0400);
        second.setTime(40000L);

        realManager.handleLocation(first);
        realManager.handleLocation(second);

        Cursor cursor = dbHelper.getReadableDatabase().query(
                "location_points", new String[]{"latitude", "timestamp"},
                "session_id = ?", new String[]{realTrackManager.getActiveSessionId()},
                null, null, "timestamp ASC");
        assertEquals(2, cursor.getCount());
        assertTrue(cursor.moveToFirst());
        assertEquals(-27.4705, cursor.getDouble(0), 0.000001);
        assertTrue(cursor.moveToNext());
        assertEquals(-27.4800, cursor.getDouble(0), 0.000001);
        cursor.close();
    }

    /**
     * The field bug: once the Track tab had been drawn, raw fixes went into
     * the database but the distance never moved, because the track manager's
     * cache never heard about them.
     */
    @Test
    public void handleLocation_afterTheTrackTabHasLoaded_stillAdvancesTheDistance() {
        RawGnssCaptureManager realManager = managerWithRealTrackManager();
        when(identityManager.resolveIdentity()).thenReturn(RESOLVED_IDENTITY);

        realManager.handleLocation(fixAt(-27.4698, 153.0250, 1000L));
        assertEquals(0.0, realTrackManager.getDistanceMeters(), 0.001);
        realTrackManager.getDetailsSummary(); // what the Track tab does

        realManager.handleLocation(fixAt(-27.4698, 153.0280, 6000L));
        realManager.handleLocation(fixAt(-27.4698, 153.0310, 11000L));

        double expected = 2 * SearchTrackManager.haversineDistance(
                -27.4698, 153.0250, -27.4698, 153.0280);
        assertEquals(expected, realTrackManager.getDistanceMeters(), 0.01);
        assertTrue(realTrackManager.getDetailsSummary().contains(
                String.format(java.util.Locale.US, "Distance: %.2f km",
                        expected / 1000.0)));
    }

    @Test
    public void handleLocation_countsEachFixAgainstTheSession() {
        RawGnssCaptureManager realManager = managerWithRealTrackManager();
        when(identityManager.resolveIdentity()).thenReturn(RESOLVED_IDENTITY);

        realManager.handleLocation(fixAt(-27.4698, 153.0250, 1000L));
        realManager.handleLocation(fixAt(-27.4698, 153.0260, 6000L));

        Cursor cursor = dbHelper.getReadableDatabase().query("track_sessions",
                new String[]{"point_count"}, "session_id = ?",
                new String[]{realTrackManager.getActiveSessionId()}, null, null,
                null);
        assertTrue(cursor.moveToFirst());
        assertEquals(2, cursor.getInt(0));
        cursor.close();
    }

    /**
     * Without an operation there is no track session. Fixes used to be
     * written anyway with a NULL session id, which nothing ever read.
     */
    @Test
    public void handleLocation_withoutAnOperation_storesNothing() {
        RawGnssCaptureManager realManager = managerWithRealTrackManager();
        realTrackManager.setOperationId("");
        when(identityManager.resolveIdentity()).thenReturn(RESOLVED_IDENTITY);

        realManager.handleLocation(gpsFix());

        Cursor cursor = dbHelper.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM location_points", null);
        assertTrue(cursor.moveToFirst());
        assertEquals(0, cursor.getInt(0));
        cursor.close();
        assertNull(realTrackManager.getActiveSessionId());
    }

    private RawGnssCaptureManager managerWithRealTrackManager() {
        dbHelper = new DatabaseHelper(RuntimeEnvironment.getApplication());
        realTrackManager = new SearchTrackManager(
                new TrackSessionRepository(dbHelper),
                new LocationRepository(dbHelper));
        realTrackManager.setOperationId(OPERATION_ID);
        RawGnssCaptureManager realManager = new RawGnssCaptureManager(
                RuntimeEnvironment.getApplication(), identityManager,
                realTrackManager, null);
        return realManager;
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

    private static Location fixAt(double latitude, double longitude, long time) {
        Location location = gpsFix();
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setTime(time);
        return location;
    }
}
