package com.atakmap.android.plugintemplate.grid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges 100 m cells that share the same style into as few rectangles as
 * possible, so a searched area of thousands of cells becomes a handful of map
 * items instead of one map item per cell.
 * <p>
 * Cells are addressed by integer column/row (UTM easting/northing divided by
 * the cell size). Pure Java so it can be unit tested on a plain JVM.
 */
public final class GridCellRuns {

    /** One cell to merge; {@code key} is anything that must match to merge. */
    public static final class Cell {
        final int column;
        final int row;
        final String key;

        public Cell(int column, int row, String key) {
            this.column = column;
            this.row = row;
            this.key = key == null ? "" : key;
        }
    }

    /** Inclusive rectangle of cells that all share {@link #getKey()}. */
    public static final class Rect {
        private final int firstColumn;
        private final int firstRow;
        private final int lastColumn;
        private int lastRow;
        private final String key;

        Rect(int firstColumn, int firstRow, int lastColumn, int lastRow,
                String key) {
            this.firstColumn = firstColumn;
            this.firstRow = firstRow;
            this.lastColumn = lastColumn;
            this.lastRow = lastRow;
            this.key = key;
        }

        public int getFirstColumn() {
            return firstColumn;
        }

        public int getFirstRow() {
            return firstRow;
        }

        public int getLastColumn() {
            return lastColumn;
        }

        public int getLastRow() {
            return lastRow;
        }

        public String getKey() {
            return key;
        }

        public int getCellCount() {
            return (lastColumn - firstColumn + 1) * (lastRow - firstRow + 1);
        }

        public boolean intersects(int minColumn, int minRow, int maxColumn,
                int maxRow) {
            return firstColumn <= maxColumn && lastColumn >= minColumn
                    && firstRow <= maxRow && lastRow >= minRow;
        }
    }

    private GridCellRuns() {
    }

    /**
     * Joins horizontally adjacent cells with the same key into runs, then
     * stacks identical runs from consecutive rows into rectangles. Duplicate
     * cells are tolerated; the first key seen for a column/row wins.
     */
    public static List<Rect> merge(List<Cell> cells) {
        List<Rect> result = new ArrayList<>();
        if (cells == null || cells.isEmpty())
            return result;
        List<Cell> sorted = new ArrayList<>(cells);
        Collections.sort(sorted, new Comparator<Cell>() {
            @Override
            public int compare(Cell first, Cell second) {
                if (first.row != second.row)
                    return first.row < second.row ? -1 : 1;
                if (first.column != second.column)
                    return first.column < second.column ? -1 : 1;
                return 0;
            }
        });

        Map<String, Rect> open = new HashMap<>();
        int index = 0;
        while (index < sorted.size()) {
            int row = sorted.get(index).row;
            Map<String, Rect> stillOpen = new HashMap<>();
            while (index < sorted.size() && sorted.get(index).row == row) {
                Cell start = sorted.get(index);
                int lastColumn = start.column;
                index++;
                while (index < sorted.size()) {
                    Cell next = sorted.get(index);
                    if (next.row != row)
                        break;
                    if (next.column == lastColumn) {
                        index++;
                        continue;
                    }
                    if (next.column != lastColumn + 1
                            || !next.key.equals(start.key))
                        break;
                    lastColumn = next.column;
                    index++;
                }
                String runKey = start.column + ":" + lastColumn + ":"
                        + start.key;
                Rect rect = open.remove(runKey);
                if (rect != null && rect.lastRow == row - 1) {
                    rect.lastRow = row;
                } else {
                    if (rect != null)
                        result.add(rect);
                    rect = new Rect(start.column, row, lastColumn, row,
                            start.key);
                }
                stillOpen.put(runKey, rect);
            }
            result.addAll(open.values());
            open = stillOpen;
        }
        result.addAll(open.values());
        return result;
    }
}
