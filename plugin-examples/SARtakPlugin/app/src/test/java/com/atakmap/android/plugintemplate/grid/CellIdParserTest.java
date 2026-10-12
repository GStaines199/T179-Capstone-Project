package com.atakmap.android.plugintemplate.grid;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CellIdParserTest {

    @Test
    public void parsesZoneEastingAndNorthing() {
        CellIdParser.Parsed parsed = CellIdParser.parse(
                "utm-56J-c100-e500900-n6960000");

        assertEquals("56J", parsed.zone);
        assertEquals(500900L, parsed.easting);
        assertEquals(6960000L, parsed.northing);
        assertTrue(parsed.canonical);
    }

    @Test
    public void leadingZerosParseButAreNotCanonical() {
        CellIdParser.Parsed parsed = CellIdParser.parse(
                "utm-56J-c100-e0500900-n6960000");

        assertEquals(500900L, parsed.easting);
        assertFalse(parsed.canonical);
        assertTrue(CellIdParser.parse("utm-56J-c100-e0-n0").canonical);
    }

    @Test
    public void converterReadsCellsThroughIt() {
        SearchGridCell cell = new GridCoordinateConverter().parseCell(
                "utm-56J-c100-e500900-n6960000", SearchGridStatus.PARTIAL);

        assertEquals(500900.0, cell.getWest(), 0.0);
        assertEquals(6960100.0, cell.getNorth(), 0.0);
        assertEquals(SearchGridStatus.PARTIAL, cell.getStatus());
        assertNull(new GridCoordinateConverter().parseCell("cell_42",
                SearchGridStatus.PARTIAL));
    }

    @Test
    public void rejectsAnythingElse() {
        String[] bad = {null, "", "utm-", "utm--c100-e1-n1",
                "utm-56-J-c100-e1-n1", "utm-56J-c50-e1-n1",
                "utm-56J-c100-e-n1", "utm-56J-c100-e1-n", "utm-56J-c100-e1-n1x",
                "utm-56J-c100-e1x-n1", "UTM-56J-c100-e1-n1",
                "utm-56J-c100-e1234567890123456789-n1"};
        for (String id : bad)
            assertNull(id, CellIdParser.parse(id));
    }
}
