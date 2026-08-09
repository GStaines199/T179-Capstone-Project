package com.atakmap.android.plugintemplate.runtime;

import com.atakmap.android.plugintemplate.grid.SearchLineColorOption;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the CoT wire schema used by SearchLineCotWorkflow: which
 * attribute names are written for a "__sartak_line" detail element
 * (toAttributes) and how they are read back, including the defensive
 * fallbacks applied to missing or malformed data (fromAttributes).
 * <p>
 * The one-way contact/uid/__group details added by
 * addStandardContactDetails are ATAK/WinTAK presentation metadata that
 * fromCotEvent never reads back, so they are out of scope here.
 * <p>
 * SearchLineCotWorkflow#createCotEvent/#fromCotEvent themselves are not
 * covered - constructing a real CotEvent/CotDetail/GeoPoint fails bytecode
 * verification under plain JUnit and also under Robolectric 4.9 (its
 * bundled ASM cannot parse this SDK's class file version), so those two
 * methods stay thin, ATAK-dependent wrappers around the schema logic tested
 * here - same convention as SearchGridCotWorkflowSchemaTest and
 * AtakLocationStatus#evaluate vs #from(MapView) in GnssFixEvaluationTest.
 * <p>
 * Dependencies (build.gradle):
 *   testImplementation 'junit:junit:4.13.2'
 */
public class SearchLineCotWorkflowSchemaTest {

    private static final long CREATED = 1700000000000L;

    private static SearchLineCotMessage sampleMessage() {
        return new SearchLineCotMessage(
                "sartak-line-line_update-team1-uid1-" + CREATED,
                SearchLineCotMessage.ACTION_UPDATE, "team1", "uid1",
                "Alpha One", "Zone A", "agg1", "cellA", 2, 3, 152.9, -27.5,
                153.1, -27.3, 12.5, SearchLineColorOption.MAGENTA, 8.0,
                CREATED);
    }

    // -------------------------------------------------------------------------
    // toAttributes
    // -------------------------------------------------------------------------

    @Test
    public void toAttributes_writesEveryFieldUnderItsWireName() {
        Map<String, String> attributes = SearchLineCotWorkflow
                .toAttributes(sampleMessage());

        assertEquals(SearchLineCotMessage.ACTION_UPDATE,
                attributes.get("action"));
        assertEquals("team1", attributes.get("teamId"));
        assertEquals("uid1", attributes.get("senderUid"));
        assertEquals("Alpha One", attributes.get("senderCallsign"));
        assertEquals("Zone A", attributes.get("zone"));
        assertEquals("agg1", attributes.get("aggregateId"));
        assertEquals("cellA", attributes.get("cellId"));
        assertEquals("2", attributes.get("row"));
        assertEquals("3", attributes.get("column"));
        assertEquals(String.valueOf(152.9), attributes.get("west"));
        assertEquals(String.valueOf(-27.5), attributes.get("south"));
        assertEquals(String.valueOf(153.1), attributes.get("east"));
        assertEquals(String.valueOf(-27.3), attributes.get("north"));
        assertEquals(String.valueOf(12.5), attributes.get("lineNorthing"));
        assertEquals("MAGENTA", attributes.get("color"));
        assertEquals(String.valueOf(8.0), attributes.get("tolerance"));
        assertEquals(String.valueOf(CREATED), attributes.get("created"));
    }

    @Test
    public void toAttributes_withNoZoneOrCellId_omitsCellFields() {
        SearchLineCotMessage message = new SearchLineCotMessage(
                "sartak-line-line_start-team1-uid1-" + CREATED,
                SearchLineCotMessage.ACTION_START, "team1", "uid1",
                "Alpha One", "", "", "", 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0,
                SearchLineColorOption.CYAN, 0.0, CREATED);

        Map<String, String> attributes = SearchLineCotWorkflow
                .toAttributes(message);

        assertEquals(null, attributes.get("aggregateId"));
        assertEquals(null, attributes.get("cellId"));
        assertEquals(null, attributes.get("row"));
    }

    // -------------------------------------------------------------------------
    // Round trip
    // -------------------------------------------------------------------------

    @Test
    public void fromAttributes_ofToAttributes_reconstructsEquivalentMessage() {
        SearchLineCotMessage original = sampleMessage();

        SearchLineCotMessage restored = SearchLineCotWorkflow.fromAttributes(
                SearchLineCotWorkflow.toAttributes(original), "uid1");

        assertEquals(original.getAction(), restored.getAction());
        assertEquals(original.getTeamId(), restored.getTeamId());
        assertEquals(original.getSenderUid(), restored.getSenderUid());
        assertEquals(original.getSenderCallsign(),
                restored.getSenderCallsign());
        assertEquals(original.getZoneDescriptor(),
                restored.getZoneDescriptor());
        assertEquals(original.toCell().getAggregateId(),
                restored.toCell().getAggregateId());
        assertEquals(original.toCell().getId(), restored.toCell().getId());
        assertEquals(original.toCell().getRow(), restored.toCell().getRow());
        assertEquals(original.toCell().getColumn(),
                restored.toCell().getColumn());
        assertEquals(original.toCell().getWest(), restored.toCell().getWest(),
                0.0001);
        assertEquals(original.toCell().getSouth(),
                restored.toCell().getSouth(), 0.0001);
        assertEquals(original.toCell().getEast(), restored.toCell().getEast(),
                0.0001);
        assertEquals(original.toCell().getNorth(),
                restored.toCell().getNorth(), 0.0001);
        assertEquals(original.getLineNorthing(), restored.getLineNorthing(),
                0.0001);
        assertEquals(original.getColorOption(), restored.getColorOption());
        assertEquals(original.getToleranceMeters(),
                restored.getToleranceMeters(), 0.0001);
        assertEquals(original.getCreated(), restored.getCreated());
    }

    @Test
    public void fromAttributes_roundTripsEveryColorOption() {
        for (SearchLineColorOption color : SearchLineColorOption.values()) {
            SearchLineCotMessage original = new SearchLineCotMessage(
                    "sartak-line-line_update-team1-uid1-" + CREATED,
                    SearchLineCotMessage.ACTION_UPDATE, "team1", "uid1",
                    "Alpha One", "Zone A", "agg1", "cellA", 1, 1, 0.0, 0.0,
                    0.0, 0.0, 0.0, color, 5.0, CREATED);

            SearchLineCotMessage restored = SearchLineCotWorkflow
                    .fromAttributes(SearchLineCotWorkflow.toAttributes(
                            original), "uid1");

            assertEquals(color, restored.getColorOption());
        }
    }

    // -------------------------------------------------------------------------
    // Defensive fallbacks on receive
    // -------------------------------------------------------------------------

    @Test
    public void fromAttributes_withUnknownColor_fallsBackToCyan() {
        Map<String, String> attributes = SearchLineCotWorkflow
                .toAttributes(sampleMessage());
        attributes.put("color", "SOMETHING_A_NEWER_APP_VERSION_ADDED");

        SearchLineCotMessage restored = SearchLineCotWorkflow.fromAttributes(
                attributes, "uid1");

        assertEquals(SearchLineColorOption.CYAN, restored.getColorOption());
    }

    @Test
    public void fromAttributes_withMissingMessageUid_synthesizesOne() {
        Map<String, String> attributes = SearchLineCotWorkflow
                .toAttributes(sampleMessage());
        attributes.remove("messageUid");

        SearchLineCotMessage restored = SearchLineCotWorkflow.fromAttributes(
                attributes, "sender-uid-42");

        assertEquals("sender-uid-42-line-" + CREATED, restored.getUid());
    }

    @Test
    public void fromAttributes_withUnparsableNumericFields_fallToZero() {
        Map<String, String> attributes = SearchLineCotWorkflow
                .toAttributes(sampleMessage());
        attributes.put("row", "not-a-number");
        attributes.put("west", "not-a-number");
        attributes.put("lineNorthing", "not-a-number");
        attributes.put("tolerance", "not-a-number");

        SearchLineCotMessage restored = SearchLineCotWorkflow.fromAttributes(
                attributes, "uid1");

        assertEquals(0, restored.toCell().getRow());
        assertEquals(0.0, restored.toCell().getWest(), 0.0001);
        assertEquals(0.0, restored.getLineNorthing(), 0.0001);
        assertEquals(0.0, restored.getToleranceMeters(), 0.0001);
    }

    @Test
    public void fromAttributes_withNoAttributesAtAll_stillProducesAMessage() {
        SearchLineCotMessage restored = SearchLineCotWorkflow.fromAttributes(
                new LinkedHashMap<String, String>(), "sender-uid-42");

        assertEquals("", restored.getAction());
        assertEquals("", restored.getTeamId());
        assertEquals(SearchLineColorOption.CYAN, restored.getColorOption());
        assertTrue(restored.getUid().startsWith("sender-uid-42-line-"));
    }
}
