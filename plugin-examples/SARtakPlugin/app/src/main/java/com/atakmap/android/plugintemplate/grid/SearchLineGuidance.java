package com.atakmap.android.plugintemplate.grid;

import java.util.Locale;

/**
 * Turns a searcher's offset from the search line into the words shown to them,
 * and decides when pace and bearing may be shown at all.
 *
 * <p>Two rules are enforced here rather than at the call sites:
 *
 * <p><b>Guidance is two-sided.</b> The line previously warned only about
 * searchers who had run ahead. A searcher falling behind opens a gap in the
 * swept ground just as surely, and was told nothing. {@link Band} names both
 * failures and the tolerable drift between them.
 *
 * <p><b>Nothing is reported that was not measured.</b> An absent pace arrives
 * as NaN, never as zero -- a stopped searcher and a searcher whose speed ATAK
 * never reported are different facts, and zero cannot mean both. Same for an
 * unreliable bearing, which is withheld rather than rounded to due north. This
 * mirrors {@link MemberPositionPolicy}, which guards positions the same way.
 *
 * <p>Free of ATAK types so a plain JVM test can reach it.
 */
public final class SearchLineGuidance {

    /** Within this distance a searcher counts as on the line. */
    public static final double ON_LINE_TOLERANCE_METERS = 2.0;

    /**
     * Beyond this the drift stops being tolerable and becomes an instruction.
     * Shared with {@link SearchLineManager#SLOW_DOWN_THRESHOLD_METERS} so the
     * warning list and the per-searcher label can never disagree about who is
     * out of position.
     */
    public static final double CORRECTION_THRESHOLD_METERS =
            SearchLineManager.SLOW_DOWN_THRESHOLD_METERS;

    /**
     * Below this a reported pace is shown as stationary rather than as a
     * figure. ATAK reports small non-zero speeds for a standing searcher as
     * GPS noise moves the fix around; printing "0.3 m/min" implies a precision
     * the reading does not have.
     */
    public static final double STATIONARY_PACE_METERS_PER_MINUTE = 0.5;

    public static final String UNAVAILABLE_LABEL = "unavailable";
    public static final String STATIONARY_LABEL = "Stationary";

    /** Where a searcher sits relative to the line, in travel-axis terms. */
    public enum Band {
        /** Ahead of the line by more than the correction threshold. */
        HOLD("Hold"),
        /** Ahead, but within tolerable drift. */
        AHEAD("Ahead"),
        /** On the line. */
        ON_LINE("On line"),
        /** Behind, but within tolerable drift. */
        BEHIND("Behind"),
        /** Behind the line by more than the correction threshold. */
        CATCH_UP("Catch up"),
        /** No position was reported, so no band can be claimed. */
        UNKNOWN(MemberPositionPolicy.UNKNOWN_POSITION_LABEL);

        private final String label;

        Band(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        /** Whether this band calls for the searcher to change what they do. */
        public boolean needsCorrection() {
            return this == HOLD || this == CATCH_UP;
        }
    }

    private SearchLineGuidance() {
    }

    /**
     * Classifies a searcher's signed distance from the line, where positive is
     * ahead in the direction of travel.
     *
     * <p>{@code positionKnown} is a separate argument rather than inferred from
     * the number because {@code Math.abs(NaN)} compares false against every
     * threshold, which would quietly land an unlocatable searcher in the
     * {@code HOLD} band -- or, worse, fall through to {@code ON_LINE}.
     */
    public static Band bandFor(boolean positionKnown, double progressMeters) {
        if (!positionKnown || Double.isNaN(progressMeters))
            return Band.UNKNOWN;
        if (Math.abs(progressMeters) <= ON_LINE_TOLERANCE_METERS)
            return Band.ON_LINE;
        if (progressMeters > CORRECTION_THRESHOLD_METERS)
            return Band.HOLD;
        if (progressMeters < -CORRECTION_THRESHOLD_METERS)
            return Band.CATCH_UP;
        return progressMeters > 0 ? Band.AHEAD : Band.BEHIND;
    }

    /**
     * The full guidance line for a searcher: how far off they are and, when it
     * matters, what to do about it.
     *
     * <p>Distances are shown unsigned with an explicit "ahead"/"behind" word.
     * A signed metre count reads as a coordinate, and in the field the word is
     * what gets acted on.
     */
    public static String guidanceLabel(boolean positionKnown,
            double progressMeters) {
        Band band = bandFor(positionKnown, progressMeters);
        if (band == Band.UNKNOWN)
            return MemberPositionPolicy.UNKNOWN_POSITION_LABEL;
        if (band == Band.ON_LINE)
            return Band.ON_LINE.getLabel();
        long metres = Math.round(Math.abs(progressMeters));
        String where = metres + " m "
                + (progressMeters > 0 ? "ahead" : "behind");
        if (!band.needsCorrection())
            return where;
        return where + " - " + (band == Band.HOLD ? "slow down" : "catch up");
    }

    /**
     * A pace in metres per minute, or {@link #UNAVAILABLE_LABEL} when ATAK
     * reported none.
     *
     * <p>NaN means unreported. Zero means reported and stopped. Collapsing the
     * two -- as printing "unavailable" for any pace at or below zero did --
     * tells a leader their searcher's radio is failing when the searcher has
     * simply halted, and hides a genuinely missing reading behind a plausible
     * one.
     */
    public static String paceLabel(double paceMetersPerMinute) {
        if (Double.isNaN(paceMetersPerMinute)
                || Double.isInfinite(paceMetersPerMinute)
                || paceMetersPerMinute < 0.0)
            return UNAVAILABLE_LABEL;
        if (paceMetersPerMinute < STATIONARY_PACE_METERS_PER_MINUTE)
            return STATIONARY_LABEL;
        return String.format(Locale.US, "%.1f m/min", paceMetersPerMinute);
    }

    /**
     * A true bearing, or {@link #UNAVAILABLE_LABEL} when the heading is not
     * trustworthy.
     *
     * <p>ATAK derives heading from successive fixes, so a stationary or
     * drifting device produces a heading that is arithmetically valid and
     * physically meaningless. {@code reliable} carries that judgement in from
     * {@link SearchTeamMember#hasReliableHeading()}; this method never tries to
     * re-derive it from the angle, which looks identical either way.
     */
    public static String bearingLabel(boolean reliable, double degrees) {
        if (!reliable || Double.isNaN(degrees) || Double.isInfinite(degrees))
            return UNAVAILABLE_LABEL;
        double normalised = ((degrees % 360.0) + 360.0) % 360.0;
        return String.format(Locale.US, "%03d deg T", Math.round(normalised));
    }
}
