package com.atakmap.android.plugintemplate.grid;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GridCellRunsTest {

    @Test
    public void merge_emptyInput_returnsNoRectangles() {
        assertTrue(GridCellRuns.merge(new ArrayList<GridCellRuns.Cell>())
                .isEmpty());
        assertTrue(GridCellRuns.merge(null).isEmpty());
    }

    @Test
    public void merge_solidBlock_becomesOneRectangle() {
        List<GridCellRuns.Cell> cells = new ArrayList<>();
        for (int column = 10; column < 110; column++)
            for (int row = 50; row < 100; row++)
                cells.add(new GridCellRuns.Cell(column, row, "COMPLETE"));

        List<GridCellRuns.Rect> rects = GridCellRuns.merge(cells);

        assertEquals(1, rects.size());
        GridCellRuns.Rect rect = rects.get(0);
        assertEquals(10, rect.getFirstColumn());
        assertEquals(50, rect.getFirstRow());
        assertEquals(109, rect.getLastColumn());
        assertEquals(99, rect.getLastRow());
        assertEquals(5000, rect.getCellCount());
    }

    @Test
    public void merge_differentKeys_areNeverJoined() {
        List<GridCellRuns.Cell> cells = new ArrayList<>();
        cells.add(new GridCellRuns.Cell(0, 0, "COMPLETE"));
        cells.add(new GridCellRuns.Cell(1, 0, "PARTIAL"));
        cells.add(new GridCellRuns.Cell(0, 1, "PARTIAL"));
        cells.add(new GridCellRuns.Cell(1, 1, "COMPLETE"));

        List<GridCellRuns.Rect> rects = GridCellRuns.merge(cells);

        assertEquals(4, rects.size());
        assertCoversExactly(cells, rects);
    }

    @Test
    public void merge_rowGap_startsNewRectangle() {
        List<GridCellRuns.Cell> cells = new ArrayList<>();
        cells.add(new GridCellRuns.Cell(0, 0, "COMPLETE"));
        cells.add(new GridCellRuns.Cell(0, 2, "COMPLETE"));

        assertEquals(2, GridCellRuns.merge(cells).size());
    }

    @Test
    public void merge_duplicateCells_areCountedOnce() {
        List<GridCellRuns.Cell> cells = new ArrayList<>();
        cells.add(new GridCellRuns.Cell(3, 3, "COMPLETE"));
        cells.add(new GridCellRuns.Cell(3, 3, "COMPLETE"));
        cells.add(new GridCellRuns.Cell(4, 3, "COMPLETE"));

        List<GridCellRuns.Rect> rects = GridCellRuns.merge(cells);

        assertEquals(1, rects.size());
        assertEquals(2, rects.get(0).getCellCount());
    }

    @Test
    public void merge_randomShapes_coverEveryCellExactlyOnce() {
        Random random = new Random(42);
        for (int trial = 0; trial < 50; trial++) {
            Map<String, GridCellRuns.Cell> unique = new HashMap<>();
            for (int i = 0; i < 400; i++) {
                int column = random.nextInt(30);
                int row = random.nextInt(30);
                String key = random.nextBoolean() ? "COMPLETE" : "PARTIAL";
                String id = column + ":" + row;
                if (!unique.containsKey(id))
                    unique.put(id, new GridCellRuns.Cell(column, row, key));
            }
            List<GridCellRuns.Cell> cells = new ArrayList<>(unique.values());
            assertCoversExactly(cells, GridCellRuns.merge(cells));
        }
    }

    @Test
    public void merge_searchedLanes_needFarFewerItemsThanCells() {
        // Fifty 100 m rows of completed cells with a partial band on top: the
        // shape a few hours of north-south lane searching leaves behind.
        List<GridCellRuns.Cell> cells = new ArrayList<>();
        for (int column = 0; column < 100; column++) {
            for (int row = 0; row < 50; row++)
                cells.add(new GridCellRuns.Cell(column, row, "COMPLETE"));
            for (int row = 51; row < 55; row++)
                cells.add(new GridCellRuns.Cell(column, row, "PARTIAL"));
        }

        List<GridCellRuns.Rect> rects = GridCellRuns.merge(cells);

        assertEquals(2, rects.size());
        assertCoversExactly(cells, rects);
    }

    @Test
    public void rect_intersects_usesInclusiveBounds() {
        List<GridCellRuns.Cell> cells = new ArrayList<>();
        cells.add(new GridCellRuns.Cell(5, 5, "COMPLETE"));
        GridCellRuns.Rect rect = GridCellRuns.merge(cells).get(0);

        assertTrue(rect.intersects(5, 5, 5, 5));
        assertTrue(rect.intersects(0, 0, 5, 5));
        assertTrue(!rect.intersects(6, 0, 10, 10));
        assertTrue(!rect.intersects(0, 0, 4, 4));
    }

    private static void assertCoversExactly(List<GridCellRuns.Cell> cells,
            List<GridCellRuns.Rect> rects) {
        Map<String, String> expected = new HashMap<>();
        for (GridCellRuns.Cell cell : cells)
            expected.put(cell.column + ":" + cell.row, cell.key);
        Map<String, String> covered = new HashMap<>();
        for (GridCellRuns.Rect rect : rects) {
            for (int column = rect.getFirstColumn(); column <= rect
                    .getLastColumn(); column++) {
                for (int row = rect.getFirstRow(); row <= rect
                        .getLastRow(); row++) {
                    String id = column + ":" + row;
                    assertTrue("cell covered twice: " + id,
                            !covered.containsKey(id));
                    covered.put(id, rect.getKey());
                }
            }
        }
        assertEquals(expected, covered);
    }
}
