package com.atakmap.android.plugintemplate.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compact form of a route's cell list for sync.
 *
 * <p>A route is mostly straight runs of neighbouring 100 m cells: a serpentine
 * route over a planned area is one run per column. Each run is sent as
 * {@code "<first cell id>~<last cell id>"} instead of every id in between, so
 * a route over a 20 km x 20 km area is about 200 strings rather than 40,401.
 * Cells that do not sit on a run, or whose ids are not 100 m UTM cell ids,
 * are sent on their own unchanged.
 */
public final class RouteCellRuns {

    /** Separates the first and last cell of a run. Never appears in a cell id. */
    static final String RUN_SEPARATOR = "~";

    /** Refuses to expand a damaged or hostile document into millions of ids. */
    static final int MAX_DECODED_CELLS = 250_000;

    private static final Pattern CELL_ID_PATTERN = Pattern.compile(
            "^utm-([^-]+)-c100-e(\\d+)-n(\\d+)$");
    private static final long CELL_SIZE_METERS = 100L;

    private RouteCellRuns() {
    }

    public static List<String> encode(List<String> cellIds) {
        List<String> runs = new ArrayList<>();
        if (cellIds == null)
            return runs;
        int i = 0;
        while (i < cellIds.size()) {
            String first = cellIds.get(i);
            ParsedId start = ParsedId.parse(first);
            int end = i;
            if (start != null && i + 1 < cellIds.size()) {
                ParsedId next = ParsedId.parse(cellIds.get(i + 1));
                long stepE = next == null ? 0 : next.easting - start.easting;
                long stepN = next == null ? 0 : next.northing - start.northing;
                if (next != null && next.zone.equals(start.zone)
                        && isUnitStep(stepE, stepN)) {
                    ParsedId previous = next;
                    end = i + 1;
                    while (end + 1 < cellIds.size()) {
                        ParsedId candidate = ParsedId.parse(
                                cellIds.get(end + 1));
                        if (candidate == null
                                || !candidate.zone.equals(start.zone)
                                || candidate.easting - previous.easting != stepE
                                || candidate.northing - previous.northing
                                        != stepN)
                            break;
                        previous = candidate;
                        end++;
                    }
                }
            }
            runs.add(end == i ? first
                    : first + RUN_SEPARATOR + cellIds.get(end));
            i = end + 1;
        }
        return runs;
    }

    public static List<String> decode(List<String> runs) {
        return decode(runs, null);
    }

    /**
     * @param acceptedRuns when not null, receives the runs that were expanded,
     *        so a caller can tell whether the input described the result
     *        exactly or had damaged runs skipped
     */
    public static List<String> decode(List<String> runs,
            List<String> acceptedRuns) {
        List<String> cellIds = new ArrayList<>();
        if (runs == null)
            return cellIds;
        for (String run : runs) {
            if (run == null || run.length() == 0)
                continue;
            int separator = run.indexOf(RUN_SEPARATOR);
            if (separator < 0) {
                if (cellIds.size() >= MAX_DECODED_CELLS)
                    break;
                cellIds.add(run);
                if (acceptedRuns != null)
                    acceptedRuns.add(run);
                continue;
            }
            ParsedId start = ParsedId.parse(run.substring(0, separator));
            ParsedId end = ParsedId.parse(run.substring(separator + 1));
            if (start == null || end == null || !start.zone.equals(end.zone))
                continue;
            long deltaE = end.easting - start.easting;
            long deltaN = end.northing - start.northing;
            if ((deltaE != 0 && deltaN != 0)
                    || deltaE % CELL_SIZE_METERS != 0
                    || deltaN % CELL_SIZE_METERS != 0)
                continue;
            long steps = Math.abs(deltaE + deltaN) / CELL_SIZE_METERS;
            if (cellIds.size() + steps + 1 > MAX_DECODED_CELLS)
                break;
            long stepE = Long.signum(deltaE) * CELL_SIZE_METERS;
            long stepN = Long.signum(deltaN) * CELL_SIZE_METERS;
            for (long s = 0; s <= steps; s++)
                cellIds.add(cellId(start.zone, start.easting + s * stepE,
                        start.northing + s * stepN));
            if (acceptedRuns != null)
                acceptedRuns.add(run);
        }
        return cellIds;
    }

    /**
     * The cells at the ends of each run, in route order. A line through these
     * is the same line as one through every cell, because the cells inside a
     * run sit evenly along a straight stretch.
     */
    public static List<String> endpoints(List<String> runs) {
        List<String> ids = new ArrayList<>();
        for (String run : runs) {
            int separator = run.indexOf(RUN_SEPARATOR);
            if (separator < 0) {
                ids.add(run);
            } else {
                ids.add(run.substring(0, separator));
                ids.add(run.substring(separator + 1));
            }
        }
        return ids;
    }

    private static boolean isUnitStep(long stepE, long stepN) {
        return (Math.abs(stepE) == CELL_SIZE_METERS && stepN == 0)
                || (stepE == 0 && Math.abs(stepN) == CELL_SIZE_METERS);
    }

    /** Same format as GridCoordinateConverter's cell ids. */
    private static String cellId(String zone, long easting, long northing) {
        return "utm-" + zone + "-c100-e" + easting + "-n" + northing;
    }

    private static final class ParsedId {
        final String zone;
        final long easting;
        final long northing;

        private ParsedId(String zone, long easting, long northing) {
            this.zone = zone;
            this.easting = easting;
            this.northing = northing;
        }

        static ParsedId parse(String cellId) {
            if (cellId == null)
                return null;
            Matcher matcher = CELL_ID_PATTERN.matcher(cellId);
            if (!matcher.matches())
                return null;
            try {
                ParsedId parsed = new ParsedId(matcher.group(1),
                        Long.parseLong(matcher.group(2)),
                        Long.parseLong(matcher.group(3)));
                // Only ids that decode back to exactly themselves can be
                // folded into a run; "e0502000" would come back as "e502000".
                return cellId(parsed.zone, parsed.easting, parsed.northing)
                        .equals(cellId) ? parsed : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }
}
