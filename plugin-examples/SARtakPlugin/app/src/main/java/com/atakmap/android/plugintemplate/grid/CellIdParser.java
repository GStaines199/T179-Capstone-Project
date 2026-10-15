package com.atakmap.android.plugintemplate.grid;

/**
 * Parses 100 m cell ids, {@code utm-<zone>-c100-e<easting>-n<northing>}, by
 * hand.
 *
 * <p>Ids are parsed once per stored status or route cell, tens of thousands
 * of times on a large area, on the main thread. A regex did this before;
 * Android's regex goes through ICU, and over a 20 km Auto Route
 * (40,401 ids) that alone cost 200-600 ms on the emulator.
 *
 * <p>Accepts what the old pattern {@code ^utm-([^-]+)-c100-e(\d+)-n(\d+)$}
 * accepted, except numbers longer than 18 digits, which no real cell has.
 */
public final class CellIdParser {

    private static final String PREFIX = "utm-";
    private static final String SIZE_AND_EASTING = "-c100-e";
    private static final String NORTHING = "-n";
    private static final int MAX_DIGITS = 18;

    /** A parsed id. */
    public static final class Parsed {
        public final String zone;
        public final long easting;
        public final long northing;
        /**
         * False when a number has a leading zero, so the id does not rebuild
         * to exactly itself ("e0502000" comes back as "e502000").
         */
        public final boolean canonical;

        Parsed(String zone, long easting, long northing, boolean canonical) {
            this.zone = zone;
            this.easting = easting;
            this.northing = northing;
            this.canonical = canonical;
        }
    }

    private CellIdParser() {
    }

    /** The parsed id, or null when it is not a 100 m cell id. */
    public static Parsed parse(String cellId) {
        if (cellId == null || !cellId.startsWith(PREFIX))
            return null;
        int zoneEnd = cellId.indexOf('-', PREFIX.length());
        if (zoneEnd <= PREFIX.length()
                || !cellId.startsWith(SIZE_AND_EASTING, zoneEnd))
            return null;
        int eastingStart = zoneEnd + SIZE_AND_EASTING.length();
        int eastingEnd = digitsEnd(cellId, eastingStart);
        if (!cellId.startsWith(NORTHING, eastingEnd))
            return null;
        int northingStart = eastingEnd + NORTHING.length();
        int northingEnd = digitsEnd(cellId, northingStart);
        if (northingEnd != cellId.length())
            return null;
        long easting = number(cellId, eastingStart, eastingEnd);
        long northing = number(cellId, northingStart, northingEnd);
        if (easting < 0 || northing < 0)
            return null;
        boolean canonical = !hasLeadingZero(cellId, eastingStart, eastingEnd)
                && !hasLeadingZero(cellId, northingStart, northingEnd);
        return new Parsed(cellId.substring(PREFIX.length(), zoneEnd),
                easting, northing, canonical);
    }

    private static int digitsEnd(String text, int from) {
        int index = from;
        while (index < text.length() && text.charAt(index) >= '0'
                && text.charAt(index) <= '9')
            index++;
        return index;
    }

    /** The digits as a number, or -1 when there are none or too many. */
    private static long number(String text, int from, int to) {
        int length = to - from;
        if (length <= 0 || length > MAX_DIGITS)
            return -1;
        long value = 0;
        for (int index = from; index < to; index++)
            value = value * 10 + (text.charAt(index) - '0');
        return value;
    }

    private static boolean hasLeadingZero(String text, int from, int to) {
        return to - from > 1 && text.charAt(from) == '0';
    }
}
