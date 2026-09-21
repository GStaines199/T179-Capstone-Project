package com.atakmap.android.plugintemplate.grid;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SearchGridDisplayFormatter {

    private static final Pattern CELL_ID_PATTERN = Pattern.compile(
            "^utm-([^-]+)-c100-e(\\d+)-n(\\d+)$");

    private SearchGridDisplayFormatter() {
    }

    public static String formatCellSummary(SearchGridCell cell) {
        if (cell == null)
            return "No cell selected";
        return "UTM: " + formatParentUtm(cell) + "\nCell: "
                + formatLocalCell(cell);
    }

    public static String formatCellSummaryWithStatus(SearchGridCell cell) {
        if (cell == null)
            return "No cell selected";
        return formatCellSummary(cell) + "\nStatus: "
                + formatStatus(cell.getStatus());
    }

    public static String formatCellCompact(SearchGridCell cell) {
        if (cell == null)
            return "No cell selected";
        return "UTM " + formatParentUtm(cell) + " / Cell "
                + formatLocalCell(cell);
    }

    public static String formatCellReference(String cellId) {
        ParsedCellId parsed = parse(cellId);
        if (parsed == null)
            return cellId == null || cellId.length() == 0
                    ? "No cell selected" : cellId;
        double parentWest = floorToGrid(parsed.west,
                GridCoordinateConverter.AGGREGATE_GRID_SIZE_METERS);
        double parentSouth = floorToGrid(parsed.south,
                GridCoordinateConverter.AGGREGATE_GRID_SIZE_METERS);
        int column = clamp((int) Math.floor((parsed.west - parentWest)
                / GridCoordinateConverter.BASE_CELL_SIZE_METERS), 0,
                GridCoordinateConverter.AGGREGATE_CELLS_PER_SIDE - 1);
        int rowFromSouth = clamp((int) Math.floor((parsed.south - parentSouth)
                / GridCoordinateConverter.BASE_CELL_SIZE_METERS), 0,
                GridCoordinateConverter.AGGREGATE_CELLS_PER_SIDE - 1);
        return String.format(Locale.US, "%s %.0fE %.0fN / Cell %s",
                parsed.zone, parentWest, parentSouth,
                formatLocalCell(column, rowFromSouth));
    }

    public static String formatParentUtm(SearchGridCell cell) {
        if (cell == null)
            return "";
        return String.format(Locale.US, "%s %.0fE %.0fN",
                cell.getZoneDescriptor(),
                floorToGrid(cell.getWest(),
                        GridCoordinateConverter.AGGREGATE_GRID_SIZE_METERS),
                floorToGrid(cell.getSouth(),
                        GridCoordinateConverter.AGGREGATE_GRID_SIZE_METERS));
    }

    public static String formatStatus(SearchGridStatus status) {
        if (status == null)
            return "";
        switch (status) {
            case NOT_STARTED:
                return "Not Started";
            case IN_PROGRESS:
                return "In Progress";
            case PARTIAL:
                return "Partial";
            case COMPLETE:
                return "Complete";
            default:
                return status.name();
        }
    }

    private static String formatLocalCell(SearchGridCell cell) {
        return formatLocalCell(cell.getColumn(), cell.getRow());
    }

    private static String formatLocalCell(int column, int rowFromSouth) {
        // The internal row index starts at the southern edge because UTM
        // northing increases northward. The field label is reversed so A1 is
        // the north-west/top-left 100 m cell in the 1 km parent square.
        char columnLetter = (char) ('A' + clamp(column, 0,
                GridCoordinateConverter.AGGREGATE_CELLS_PER_SIDE - 1));
        int rowNumber = GridCoordinateConverter.AGGREGATE_CELLS_PER_SIDE
                - clamp(rowFromSouth, 0,
                        GridCoordinateConverter.AGGREGATE_CELLS_PER_SIDE - 1);
        return columnLetter + String.valueOf(rowNumber);
    }

    private static ParsedCellId parse(String cellId) {
        if (cellId == null)
            return null;
        Matcher matcher = CELL_ID_PATTERN.matcher(cellId);
        if (!matcher.matches())
            return null;
        try {
            return new ParsedCellId(matcher.group(1),
                    Double.parseDouble(matcher.group(2)),
                    Double.parseDouble(matcher.group(3)));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static double floorToGrid(double value, double gridSize) {
        return Math.floor(value / gridSize) * gridSize;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class ParsedCellId {
        final String zone;
        final double west;
        final double south;

        ParsedCellId(String zone, double west, double south) {
            this.zone = zone;
            this.west = west;
            this.south = south;
        }
    }
}
