package com.atakmap.android.plugintemplate.grid;

/** Panel text for how much of the planned area and route is left. */
public final class RemainingCellsFormatter {

    private RemainingCellsFormatter() {
    }

    public static String areaRemaining(SearchGridManager.AreaProgress progress) {
        if (progress == null || progress.totalCells <= 0)
            return "Area left: no planned area";
        int remaining = Math.max(0, progress.totalCells - progress.complete);
        if (remaining == 0)
            return "Area left: all " + progress.totalCells
                    + " cells complete";
        StringBuilder builder = new StringBuilder("Area left: ")
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
