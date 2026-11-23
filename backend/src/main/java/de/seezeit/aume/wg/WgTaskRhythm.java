package de.seezeit.aume.wg;

/** Task interval in weeks; monthly means every four weeks. */
public enum WgTaskRhythm {
    WEEKLY(1), BIWEEKLY(2), MONTHLY(4);

    private final int weeks;

    WgTaskRhythm(int weeks) {
        this.weeks = weeks;
    }

    public int weeks() {
        return weeks;
    }
}
