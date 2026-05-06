package com.atakmap.android.test;

import android.content.Context;

import com.atakmap.android.plugintemplate.database.DatabaseHelper;
import com.atakmap.android.plugintemplate.database.LocationRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, manifest = Config.NONE)
public class LocationRepositoryTest {

    private LocationRepository repo;

    private DatabaseHelper dbHelper;


    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        dbHelper = new DatabaseHelper(context);
        repo = new LocationRepository(dbHelper);
    }

    @After
    public void tearDown() {
        dbHelper.close();
    }

    @Test
    public void insert_andQueryBySession_returnsInsertedPoint() {
        repo.insert("uid1", "Alice", -27.47, 153.02, 10.0f,
                5.0f, 90.0f, 1.5f, 1000L, "session1");

        List<double[]> points = repo.getPointsForSession("session1");
        assertEquals(1, points.size());
        assertEquals(-27.47, points.get(0)[0], 0.0001);
        assertEquals(153.02, points.get(0)[1], 0.0001);
    }

    @Test
    public void insert_multiplePoints_returnsInChronologicalOrder() {
        repo.insert("uid1", "Alice", -27.47, 153.02, 10.0f,
                5.0f, 0f, 0f, 3000L, "session2");
        repo.insert("uid1", "Alice", -27.48, 153.03, 10.0f,
                5.0f, 0f, 0f, 1000L, "session2");
        repo.insert("uid1", "Alice", -27.49, 153.04, 10.0f,
                5.0f, 0f, 0f, 2000L, "session2");

        List<double[]> points = repo.getPointsForSession("session2");
        assertEquals(3, points.size());
        // Should be ordered by timestamp ascending
        assertEquals(1000L, (long) points.get(0)[2]);
        assertEquals(2000L, (long) points.get(1)[2]);
        assertEquals(3000L, (long) points.get(2)[2]);
    }

    @Test
    public void countPoints_returnsCorrectCount() {
        repo.insert("uid1", "Alice", -27.47, 153.02, 10.0f,
                5.0f, 0f, 0f, 1000L, "session3");
        repo.insert("uid1", "Alice", -27.48, 153.03, 10.0f,
                5.0f, 0f, 0f, 2000L, "session3");

        assertEquals(2, repo.countPointsInSession("session3"));
    }

    @Test
    public void deleteSession_removesAllPoints() {
        repo.insert("uid1", "Alice", -27.47, 153.02, 10.0f,
                5.0f, 0f, 0f, 1000L, "session4");
        repo.insert("uid1", "Alice", -27.48, 153.03, 10.0f,
                5.0f, 0f, 0f, 2000L, "session4");

        repo.deleteSession("session4");
        assertEquals(0, repo.countPointsInSession("session4"));
    }

    @Test
    public void querySession_withNoPoints_returnsEmptyList() {
        List<double[]> points = repo.getPointsForSession("nonexistent_session");
        assertNotNull(points);
        assertEquals(0, points.size());
    }

    @Test
    public void insert_preservesAccuracyValue() {
        repo.insert("uid1", "Alice", -27.47, 153.02, 10.0f,
                12.5f, 0f, 0f, 1000L, "session5");

        List<double[]> points = repo.getPointsForSession("session5");
        assertEquals(12.5, points.get(0)[3], 0.001);
    }

    @Test
    public void pointsFromDifferentSessions_areIsolated() {
        repo.insert("uid1", "Alice", -27.47, 153.02, 10.0f,
                5.0f, 0f, 0f, 1000L, "sessionA");
        repo.insert("uid1", "Alice", -27.48, 153.03, 10.0f,
                5.0f, 0f, 0f, 2000L, "sessionB");

        assertEquals(1, repo.countPointsInSession("sessionA"));
        assertEquals(1, repo.countPointsInSession("sessionB"));
    }

}