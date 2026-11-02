package com.itmecasey.aeroflux.core;

/**
 * How full a tank looks. The fluid inside a tank is drawn on the client, which only needs to
 * hear about the tank when the picture would change, so the fill is rounded up into a small
 * number of steps and the server resends the tank only when the step (or the fluid) changes.
 */
public final class FillLevel {
    /** Number of visible steps between empty and full. */
    public static final int STEPS = 16;

    private FillLevel() {
    }

    /**
     * The step a fill amount rounds up to: 0 only for an empty tank, so even a few millibuckets
     * show a sliver, and {@link #STEPS} only for a completely full one.
     */
    public static int step(long amount, long capacity) {
        if (amount <= 0 || capacity <= 0) {
            return 0;
        }
        if (amount >= capacity) {
            return STEPS;
        }
        int step = (int) Math.ceil((double) amount * STEPS / capacity);
        return Math.max(1, Math.min(step, STEPS - 1));
    }

    /** The drawn height as a fraction of the tank's inside, from 0.0 to 1.0. */
    public static double fraction(long amount, long capacity) {
        if (amount <= 0 || capacity <= 0) {
            return 0.0;
        }
        return Math.min(1.0, (double) amount / capacity);
    }

    /**
     * The signal a comparator reads from a tank, worked out the way vanilla does it for a chest:
     * 0 when empty, 1 for any fluid at all, rising to 15 only when the tank is full.
     */
    public static int comparatorSignal(long amount, long capacity) {
        if (amount <= 0 || capacity <= 0) {
            return 0;
        }
        return 1 + (int) Math.floor(fraction(amount, capacity) * 14);
    }

    /**
     * A value that changes exactly when the tank's picture should be resent: the step, plus which
     * flux it is, since a swap from Aero to Moon Flux at the same level still changes the color.
     */
    public static int syncKey(long amount, long capacity, FluxType type) {
        return step(amount, capacity) * FluxType.values().length + type.ordinal();
    }
}
