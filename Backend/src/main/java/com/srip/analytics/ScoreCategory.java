package com.srip.analytics;

/**
 * The four bands the teacher dashboard is organised around.
 *
 * <p>A teacher opening the dashboard does not want a list of forty students; it
 * wants to know who is in trouble. Bucketing by band answers that in one glance,
 * and every per-student list in the teacher API is a query for one of these.
 *
 * <p>The boundaries are configuration, not constants - see
 * {@code app.grading.category-bounds}. Only the resolution order is fixed here:
 * lowest band first, so a percentage exactly on a boundary falls into the
 * <em>higher</em> band. 50% is AVERAGE rather than CRITICAL, which is the way a
 * school would read "50 to 70".
 */
public enum ScoreCategory {

    /** Below the critical bound (default: under 50%). Needs intervention now. */
    CRITICAL("Students Below 50%"),

    /** Between the critical and average bounds (default: 50-70%). */
    AVERAGE("Students Between 50 and 70"),

    /** Between the average and good bounds (default: 70-85%). */
    GOOD("Students Between 70 and 85"),

    /** At or above the good bound (default: over 85%). */
    EXCELLENT("Students Above 85");

    private final String label;

    ScoreCategory(String label) {
        this.label = label;
    }

    /** Human-readable heading for this bucket, used as-is by the dashboards. */
    public String label() {
        return label;
    }
}
