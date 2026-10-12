package com.atakmap.android.plugintemplate.grid;

import com.atakmap.android.plugintemplate.database.SearcherRepository;
import com.atakmap.android.plugintemplate.runtime.AtakTeamContactDataSource;
import com.atakmap.android.plugintemplate.runtime.SearchTeamCotMessage;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * A peer's searcher_identity row is no longer rewritten on every 9.7 s
 * refresh (Task 2 finding T2-2): only when the peer first shows up, when
 * its callsign changes, or once a minute.
 */
public class SearcherWriteThrottleTest {

    private static final String TEAM = "TEAM-lead";

    private SearcherRepository repository;
    private SearchPartyAssignmentManager manager;

    @Before
    public void setUp() {
        repository = mock(SearcherRepository.class);
        manager = new SearchPartyAssignmentManager(repository);
        manager.setSelfIdentity("uid-lead", "QA-LEAD");
        manager.createTeam("Bravo", TEAM);
    }

    private List<SearchTeamCotMessage> presenceFrom(String callsign) {
        return Collections.singletonList(new SearchTeamCotMessage("p-1",
                SearchTeamCotMessage.ACTION_PRESENCE, TEAM, "Bravo",
                "uid-lead", "QA-LEAD", "uid-member", callsign, "", "",
                System.currentTimeMillis()));
    }

    private void refresh(String callsign) {
        manager.updateFromPresence(presenceFrom(callsign),
                Collections.<AtakTeamContactDataSource.ContactSnapshot>emptyList(),
                null, new GridCoordinateConverter());
    }

    @Test
    public void repeatedRefreshesWriteThePeerOnce() {
        for (int i = 0; i < 5; i++)
            refresh("QA-MEMBER");

        verify(repository, times(1)).insertOrUpdate(eq("uid-member"),
                eq("QA-MEMBER"), anyString(), anyLong(), anyLong(),
                anyBoolean());
    }

    @Test
    public void aNewCallsignIsWrittenStraightAway() {
        refresh("QA-MEMBER");
        refresh("Searcher 2");

        verify(repository).insertOrUpdate(eq("uid-member"),
                eq("Searcher 2"), anyString(), anyLong(), anyLong(),
                anyBoolean());
    }
}
