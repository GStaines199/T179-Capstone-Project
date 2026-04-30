package com.atakmap.android.plugintemplate.grid;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SearchGridStateStoreTest {

    private static final String CELL_ID = "cell_42";
    private static final String PREF_KEY = "cell." + CELL_ID;

    @Mock
    private Context mockContext;

    @Mock
    private SharedPreferences mockPreferences;

    @Mock
    private SharedPreferences.Editor mockEditor;

    @Captor
    private ArgumentCaptor<String> keyCaptor;

    @Captor
    private ArgumentCaptor<String> valueCaptor;

    private SearchGridStateStore store;

    @Before
    public void setUp() {
        // SharedPreferences obtains Editor
        when(mockPreferences.edit()).thenReturn(mockEditor);
        // Editor chaining: putString/remove returns itself
        when(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor);
        when(mockEditor.remove(anyString())).thenReturn(mockEditor);

        // Context provides SharedPreferences
        when(mockContext.getSharedPreferences("sartak_search_grid_state", Context.MODE_PRIVATE))
                .thenReturn(mockPreferences);

        store = new SearchGridStateStore(mockContext);
    }

    @Test
    public void getStatus_whenNoValueStored_returnsNotStarted() {
        // By default mock returns null for getString with fallback
        when(mockPreferences.getString(PREF_KEY, SearchGridStatus.NOT_STARTED.name()))
                .thenReturn(SearchGridStatus.NOT_STARTED.name());

        SearchGridStatus result = store.getStatus(CELL_ID);
        assertEquals(SearchGridStatus.NOT_STARTED, result);
    }

    @Test
    public void getStatus_whenValueStored_returnsCorrectStatus() {
        when(mockPreferences.getString(PREF_KEY, SearchGridStatus.NOT_STARTED.name()))
                .thenReturn(SearchGridStatus.COMPLETE.name());

        SearchGridStatus result = store.getStatus(CELL_ID);
        assertEquals(SearchGridStatus.COMPLETE, result);
    }

    @Test
    public void getStatus_whenStoredValueIsInvalid_returnsNotStarted() {
        // An unknown string cannot be parsed to enum
        when(mockPreferences.getString(PREF_KEY, SearchGridStatus.NOT_STARTED.name()))
                .thenReturn("INVALID_VALUE");

        SearchGridStatus result = store.getStatus(CELL_ID);
        assertEquals(SearchGridStatus.NOT_STARTED, result);
    }

    @Test
    public void setStatus_putsCorrectKeyAndValue() {
        store.setStatus(CELL_ID, SearchGridStatus.IN_PROGRESS);

        verify(mockEditor).putString(keyCaptor.capture(), valueCaptor.capture());
        assertEquals(PREF_KEY, keyCaptor.getValue());
        assertEquals(SearchGridStatus.IN_PROGRESS.name(), valueCaptor.getValue());
        verify(mockEditor).apply();
    }

    @Test
    public void clearStatus_removesCorrectKey() {
        store.clearStatus(CELL_ID);

        verify(mockEditor).remove(keyCaptor.capture());
        assertEquals(PREF_KEY, keyCaptor.getValue());
        verify(mockEditor).apply();
    }
}