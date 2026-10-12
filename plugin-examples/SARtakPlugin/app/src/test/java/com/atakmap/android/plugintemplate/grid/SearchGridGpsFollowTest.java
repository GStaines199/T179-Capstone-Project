package com.atakmap.android.plugintemplate.grid;

import com.atakmap.coremap.maps.coords.GeoPoint;

import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A cell picked from the Grid tab list must stay selected while the device
 * stands still, and the cell marked partial when a running line moves on
 * must be the one the device walked out of (Task 3 finding T3-2).
 */
public class SearchGridGpsFollowTest {

    private static final String ZONE = "55H";

    private final Map<String, SearchGridStatus> statuses = new HashMap<>();
    private GridCoordinateConverter converter;
    private SearchGridManager manager;

    @Before
    public void setUp() {
        converter = new GridCoordinateConverter();
        SearchGridStateStore store = mock(SearchGridStateStore.class);
        when(store.getStatus(anyString())).thenAnswer(
                new Answer<SearchGridStatus>() {
                    @Override
                    public SearchGridStatus answer(InvocationOnMock call) {
                        SearchGridStatus status = statuses.get(
                                (String) call.getArgument(0));
                        return status == null ? SearchGridStatus.NOT_STARTED
                                : status;
                    }
                });
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock call) {
                statuses.put((String) call.getArgument(0),
                        (SearchGridStatus) call.getArgument(1));
                return null;
            }
        }).when(store).setStatus(anyString(), any(SearchGridStatus.class));
        manager = new SearchGridManager(converter, store);
    }

    /** The middle of the 100 m cell at this column and row. */
    private GeoPoint inCell(int column, int row) {
        return converter.toGeoPoint(ZONE, 300_050.0 + column * 100.0,
                6_200_050.0 + row * 100.0);
    }

    private String cellId(int column, int row) {
        return manager.cellAt(inCell(column, row)).getId();
    }

    @Test
    public void pickedCellIsKeptWhileTheDeviceStaysPut() {
        manager.updateCurrentCellAt(inCell(0, 0), false);
        manager.selectCell(manager.cellAt(inCell(5, 5)));

        // Background refreshes with the device in the same place.
        manager.updateCurrentCellAt(inCell(0, 0), false);
        manager.updateCurrentCellAt(inCell(0, 0), false);

        assertEquals(cellId(5, 5), manager.getSelectedCell().getId());
    }

    @Test
    public void selectionFollowsTheDeviceIntoANewCell() {
        manager.updateCurrentCellAt(inCell(0, 0), false);
        manager.selectCell(manager.cellAt(inCell(5, 5)));

        manager.updateCurrentCellAt(inCell(1, 0), false);

        assertEquals(cellId(1, 0), manager.getSelectedCell().getId());
    }

    @Test
    public void walkedCellIsMarkedPartialNotThePickedOne() {
        manager.updateCurrentCellAt(inCell(0, 0), true);
        manager.selectCell(manager.cellAt(inCell(5, 5)));
        statuses.put(cellId(5, 5), SearchGridStatus.IN_PROGRESS);

        manager.updateCurrentCellAt(inCell(1, 0), true);

        assertEquals(SearchGridStatus.PARTIAL, statuses.get(cellId(0, 0)));
        assertEquals(SearchGridStatus.IN_PROGRESS,
                statuses.get(cellId(5, 5)));
        assertEquals(cellId(0, 0), manager.getPendingReviewCell().getId());
    }

    @Test
    public void walkingWithNothingPickedStillMarksThePreviousCell() {
        manager.updateCurrentCellAt(inCell(0, 0), true);

        manager.updateCurrentCellAt(inCell(0, 1), true);

        assertEquals(SearchGridStatus.PARTIAL, statuses.get(cellId(0, 0)));
        assertEquals(cellId(0, 1), manager.getSelectedCell().getId());
    }

    @Test
    public void firstFixAfterARestoredSelectionMovesToTheGpsCell() {
        manager.selectCell(manager.cellAt(inCell(5, 5)));

        manager.updateCurrentCellAt(inCell(0, 0), false);

        assertEquals(cellId(0, 0), manager.getSelectedCell().getId());
    }
}
