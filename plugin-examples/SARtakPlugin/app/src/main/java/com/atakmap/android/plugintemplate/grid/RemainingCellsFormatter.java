package com.atakmap.android.plugintemplate.grid;

/** Panel text for how much of the planned area and route is left. */
public final class RemainingCellsFormatter {

    private RemainingCellsFormatter() {
    }

    /** What the Grid tab counts when no planned area is set. */
    static final String SELECTED_SQUARE_LABEL =
            "1 km square of selected cell";

    public static String areaRemaining(SearchGridManager.AreaProgress progress) {
        if (progress == null || progress.totalCells <= 0)
            return "Area left: no planned area";
        return remaining("Area left: ", progress);
    }

    /**
     * Grid tab progress for the planned area, or, with none planned, for the
     * 1 km square the selected cell sits in. Calling that square the planned
     * area made it look as if a 100-cell area had been planned.
     */
    public static String areaProgress(SearchGridManager.AreaProgress progress,
            boolean plannedArea) {
        if (progress == null)
            return plannedArea ? "Planned area: no cells"
                    : SELECTED_SQUARE_LABEL + ": no cells";
        String counts = progress.complete + " complete, " + progress.partial
                + " partial, " + progress.inProgress + " in progress, "
                + progress.totalCells + " cells total";
        if (plannedArea)
            return "Planned area: " + counts + "\n"
                    + areaRemaining(progress);
        return SELECTED_SQUARE_LABEL + " (no planned area): " + counts
                + "\n" + (progress.totalCells <= 0 ? "Square left: no cells"
                        : remaining("Square left: ", progress));
    }

    private static String remaining(String prefix,
            SearchGridManager.AreaProgress progress) {
        int remaining = Math.max(0, progress.totalCells - progress.complete);
        if (remaining == 0)
            return prefix + "all " + progress.totalCells
                    + " cells complete";
        StringBuilder builder = new StringBuilder(prefix)
                .append(remaining).append(" of ")
                .append(progress.totalCells).append(" cells not complete");
        if (progress.partial > 0)
            builder.append(" (").append(progress.partial)
                    .append(" partial)");
        return builder.toString();
    }

    /**
     * @param nextCellText display text for the next route cell, or null to
     *                     fall back to its id
     */
    public static String routeRemaining(RouteProgressTracker.Progress progress,
            String nextCellText) {
        if (progress == null || progress.totalCells <= 0)
            return "Route left: no route planned";
        if (progress.isComplete())
            return "Route left: all " + progress.totalCells
                    + " cells complete";
        String next = nextCellText == null || nextCellText.length() == 0
                ? progress.nextCellId : nextCellText;
        return "Route left: " + progress.getRemaining() + " of "
                + progress.totalCells + " cells\nNext route cell (#"
                + progress.nextPosition + "): " + next;
    }
}
