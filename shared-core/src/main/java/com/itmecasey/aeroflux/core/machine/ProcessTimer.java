package com.itmecasey.aeroflux.core.machine;

/**
 * Counts a machine's progress through one job. The machine decides each tick whether it can work
 * (it has Flux, the output has room) and calls {@link #advance} only then, so a machine that runs
 * dry simply pauses where it was.
 */
public final class ProcessTimer {
    private int progress;
    private int total;

    /** Sets how many ticks the current job needs. Progress already made is kept, up to the new total. */
    public void setTotal(int total) {
        this.total = Math.max(total, 0);
        this.progress = Math.min(progress, this.total);
    }

    /**
     * Works for one tick.
     *
     * @return true if the job is now finished; the caller hands over the product and calls {@link #reset}
     */
    public boolean advance() {
        if (total <= 0) {
            return false;
        }
        progress = Math.min(progress + 1, total);
        return progress >= total;
    }

    /** Forgets the job: no progress and no total. */
    public void reset() {
        progress = 0;
        total = 0;
    }

    /** Restores saved values. Damaged ones are clamped so progress never exceeds the total. */
    public void restore(int progress, int total) {
        this.total = Math.max(total, 0);
        this.progress = Math.max(0, Math.min(progress, this.total));
    }

    /** Ticks of work done on the current job. */
    public int progress() {
        return progress;
    }

    /** Ticks the current job needs, or 0 when there is none. */
    public int total() {
        return total;
    }

    /** Ticks still to go. */
    public int remaining() {
        return total - progress;
    }

    /** How far along the job is, from 0.0 to 1.0; 0.0 when there is no job. */
    public double fraction() {
        return fraction(progress, total);
    }

    /** The fraction for a progress and total pair, such as the numbers an open screen was sent. */
    public static double fraction(int progress, int total) {
        if (total <= 0 || progress <= 0) {
            return 0.0;
        }
        return Math.min(1.0, (double) progress / total);
    }
}
