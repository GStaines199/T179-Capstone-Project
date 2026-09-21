package com.atakmap.android.plugintemplate.grid;

/**
 * The compass direction a search line advances in.
 *
 * <p>A search line is a rank of searchers walking abreast. Two axes follow from
 * the direction of travel and they must not be confused:
 *
 * <ul>
 *   <li>the <em>travel axis</em>, which the line moves along, and along which a
 *       searcher is ahead of or behind the line;</li>
 *   <li>the <em>line axis</em>, perpendicular to it, which the line is drawn
 *       along and across which the lanes are divided.</li>
 * </ul>
 *
 * <p>For {@link #NORTH} and {@link #SOUTH} the travel axis is northing and the
 * line axis is easting; for {@link #EAST} and {@link #WEST} they swap. The
 * search line was originally hard-coded to north-only, which left the two axes
 * indistinguishable in the code -- northing meant "progress" and easting meant
 * "lane" everywhere. This enum is where that assumption is isolated, so the
 * rest of the search line asks which axis is which instead of assuming.
 *
 * <p>Deliberately free of ATAK types. {@code GeoPoint} and {@code UTMPoint} come
 * from an SDK jar that predates stackmap frames, so the geometry lives here
 * where a plain JVM test can reach it.
 */
public enum SearchLineDirection {

    NORTH("North", 0.0, true, 1.0),
    EAST("East", 90.0, false, 1.0),
    SOUTH("South", 180.0, true, -1.0),
    WEST("West", 270.0, false, -1.0);

    private final String label;
    private final double compassDegrees;
    private final boolean northSouth;
    private final double sign;

    SearchLineDirection(String label, double compassDegrees,
            boolean northSouth, double sign) {
        this.label = label;
        this.compassDegrees = compassDegrees;
        this.northSouth = northSouth;
        this.sign = sign;
    }

    public String getLabel() {
        return label;
    }

    /** True north bearing of travel, for display beside a heading. */
    public double getCompassDegrees() {
        return compassDegrees;
    }

    /**
     * Whether the line advances along northing (north or south) rather than
     * easting. Callers use this to pick which coordinate of a UTM pair is the
     * travel axis and which is the line axis.
     */
    public boolean isNorthSouth() {
        return northSouth;
    }

    /** Cycles through the four directions, for a single-tap map control. */
    public SearchLineDirection next() {
        SearchLineDirection[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /**
     * The coordinate that measures progress: northing when travelling north or
     * south, easting when travelling east or west.
     */
    public double travelCoordinate(double easting, double northing) {
        return northSouth ? northing : easting;
    }

    /**
     * The coordinate that positions a searcher across the line, used to place
     * lanes. The perpendicular partner of {@link #travelCoordinate}.
     */
    public double lineCoordinate(double easting, double northing) {
        return northSouth ? easting : northing;
    }

    /**
     * How far past the line a searcher is, positive when ahead in the
     * direction of travel and negative when behind.
     *
     * <p>The sign flip is the whole point: at a fixed northing a searcher to
     * the north is ahead of a NORTH line and behind a SOUTH one. Reporting a
     * raw coordinate difference would tell a south-bound searcher to speed up
     * when they had in fact overrun the line.
     */
    public double progressMeters(double easting, double northing,
            double lineOffset) {
        return sign * (travelCoordinate(easting, northing) - lineOffset);
    }

    /**
     * An offset {@code meters} further along the travel axis. Negative values
     * step backwards. Used to place the direction arrow ahead of the line.
     */
    public double advanceOffset(double lineOffset, double meters) {
        return lineOffset + sign * meters;
    }

    /** The lower bound of the travel axis within a cell. */
    public double travelMin(SearchGridCell cell) {
        return northSouth ? cell.getSouth() : cell.getWest();
    }

    /** The upper bound of the travel axis within a cell. */
    public double travelMax(SearchGridCell cell) {
        return northSouth ? cell.getNorth() : cell.getEast();
    }

    /** The lower bound of the line axis within a cell, where lane 1 starts. */
    public double lineMin(SearchGridCell cell) {
        return northSouth ? cell.getWest() : cell.getSouth();
    }

    /** The upper bound of the line axis within a cell. */
    public double lineMax(SearchGridCell cell) {
        return northSouth ? cell.getEast() : cell.getNorth();
    }

    /**
     * Rebuilds a UTM easting from a point expressed as (along-line, travel)
     * offsets. Paired with {@link #northingAt} so callers can lay a point out
     * in line-relative terms and convert once at the end.
     */
    public double eastingAt(double alongLine, double travelOffset) {
        return northSouth ? alongLine : travelOffset;
    }

    /** The northing partner of {@link #eastingAt}. */
    public double northingAt(double alongLine, double travelOffset) {
        return northSouth ? travelOffset : alongLine;
    }

    /**
     * Parses a stored or transmitted name, falling back to {@link #NORTH}.
     *
     * <p>North is the right fallback rather than a failure: it is what every
     * search line did before directions existed, so a message from a peer on an
     * older build -- which carries no direction at all -- is interpreted the
     * way that peer actually meant it.
     */
    public static SearchLineDirection fromName(String name) {
        if (name == null)
            return NORTH;
        for (SearchLineDirection direction : values()) {
            if (direction.name().equalsIgnoreCase(name.trim()))
                return direction;
        }
        return NORTH;
    }
}
