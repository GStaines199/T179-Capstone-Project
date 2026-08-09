package com.atakmap.android.plugintemplate.runtime;

import com.atakmap.android.plugintemplate.grid.SearchGridStatus;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the CoT wire schema used by SearchGridCotWorkflow: which
 * attribute names are written for a "__sartak_grid" detail element
 * (toAttributes) and how they are read back, including the defensive
 * fallbacks applied to missing or malformed data (fromAttributes).
 * <p>
 * SearchGridCotWorkflow#createCotEvent/#fromCotEvent themselves are not
 * covered here - constructing a real CotEvent/CotDetail/GeoPoint fails
 * bytecode verification under plain JUnit (VerifyError) and also under
 * Robolectric 4.9 (its bundled ASM cannot parse this SDK's class file
 * version), so those two methods stay thin, ATAK-dependent wrappers around
 * the schema logic tested here - same convention as
 * AtakLocationStatus#evaluate vs #from(MapView) in GnssFixEvaluationTest.
 * <p>
 * Dependencies (build.gradle):
 *   testImplementation 'junit:junit:4.13.2'
 */
public class SearchGridCotWorkflowSchemaTest {

    private static final long CREATED = 1700000000000L;

    private static SearchGridCotMessage sampleMessage() {
        return new SearchGridCotMessage("sartak-grid-team1-cellA-uid1-"
                + CREATED, "team1", "uid1", "Alpha One", "cellA",
                SearchGridStatus.IN_PROGRESS, CREATED);
    }

    // -------------------------------------------------------------------------
    // toAttributes
    // -------------------------------------------------------------------------

    @Test
    public void toAttributes_writesEveryFieldUnderItsWireName() {
        Map<String, String> attributes = SearchGridCotWorkflow
                .toAttributes(sampleMessage());

        assertEquals("sartak-grid-team1-cellA-uid1-" + CREATED,
                attributes.get("messageUid"));
        assertEquals(SearchGridCotMessage.ACTION_GRID_STATUS,
                attributes.get("action"));
        assertEquals("team1", attributes.get("teamId"));
        assertEquals("uid1", attributes.get("senderUid"));
        assertEquals("Alpha One", attributes.get("senderCallsign"));
        assertEquals("cellA", attributes.get("cellId"));
        assertEquals("IN_PROGRESS", attributes.get("status"));
        assertEquals(String.valueOf(CREATED), attributes.get("created"));
    }

    // -------------------------------------------------------------------------
    // Round trip
    // -------------------------------------------------------------------------

    @Test
    public void fromAttributes_ofToAttributes_reconstructsEquivalentMessage() {
        SearchGridCotMessage original = sampleMessage();

        SearchGridCotMessage restored = SearchGridCotWorkflow.fromAttributes(
                SearchGridCotWorkflow.toAttributes(original), "uid1");

        assertEquals(original.getUid(), restored.getUid());
        assertEquals(original.getTeamId(), restored.getTeamId());
        assertEquals(original.getSenderUid(), restored.getSenderUid());
        assertEquals(original.getSenderCallsign(),
                restored.getSenderCallsign());
        assertEquals(original.getCellId(), restored.getCellId());
        assertEquals(original.getStatus(), restored.getStatus());
        assertEquals(original.getCreated(), restored.getCreated());
    }

    @Test
    public void fromAttributes_roundTripsEveryStatusValue() {
        for (SearchGridStatus status : SearchGridStatus.values()) {
            SearchGridCotMessage original = new SearchGridCotMessage(
                    "sartak-grid-team1-cellA-uid1-" + CREATED, "team1",
                    "uid1", "Alpha One", "cellA", status, CREATED);

            SearchGridCotMessage restored = SearchGridCotWorkflow
                    .fromAttributes(SearchGridCotWorkflow.toAttributes(
                            original), "uid1");

            assertEquals(status, restored.getStatus());
        }
    }

    // -------------------------------------------------------------------------
    // Defensive fallbacks on receive
    // -------------------------------------------------------------------------

    @Test
    public void fromAttributes_withUnknownStatus_fallsBackToNotStarted() {
        Map<String, String> attributes = SearchGridCotWorkflow
                .toAttributes(sampleMessage());
        attributes.put("status", "SOMETHING_A_NEWER_APP_VERSION_ADDED");

        SearchGridCotMessage restored = SearchGridCotWorkflow.fromAttributes(
                attributes, "uid1");

        assertEquals(SearchGridStatus.NOT_STARTED, restored.getStatus());
    }

    @Test
    public void fromAttributes_withMissingMessageUid_synthesizesOne() {
        Map<String, String> attributes = SearchGridCotWorkflow
                .toAttributes(sampleMessage());
        attributes.remove("messageUid");

        SearchGridCotMessage restored = SearchGridCotWorkflow.fromAttributes(
                attributes, "sender-uid-42");

        assertEquals("sender-uid-42-grid-" + CREATED, restored.getUid());
    }

    @Test
    public void fromAttributes_withUnparsableCreated_fallsBackToProvidedDefault() {
        Map<String, String> attributes = SearchGridCotWorkflow
                .toAttributes(sampleMessage());
        attributes.put("created", "not-a-number");

        long before = System.currentTimeMillis();
        SearchGridCotMessage restored = SearchGridCotWorkflow.fromAttributes(
                attributes, "uid1");
        long after = System.currentTimeMillis();

        assertTrue(restored.getCreated() >= before
                && restored.getCreated() <= after);
    }

    @Test
    public void fromAttributes_withNoAttributesAtAll_stillProducesAMessage() {
        SearchGridCotMessage restored = SearchGridCotWorkflow.fromAttributes(
                new LinkedHashMap<String, String>(), "sender-uid-42");

        assertEquals("", restored.getTeamId());
        assertEquals("", restored.getSenderUid());
        assertEquals("", restored.getSenderCallsign());
        assertEquals("", restored.getCellId());
        assertEquals(SearchGridStatus.NOT_STARTED, restored.getStatus());
        assertTrue(restored.getUid().startsWith("sender-uid-42-grid-"));
    }
}
