package com.itmecasey.aeroflux.core.machine;

/**
 * Carries a whole number through menu data slots, which only hold 16 bits. Flux amounts go well
 * past 32,767 (a fluxium machine holds 128,000 mB), so each one is sent as two halves in two
 * slots and put back together on the client.
 *
 * <p>The halves are unsigned: a slot's value comes back from the network sign-extended, so
 * {@link #join} masks it before use.
 */
public final class SplitInt {
    private static final int HALF = 0xFFFF;

    private SplitInt() {
    }

    /** The lower 16 bits of a number. */
    public static int low(int value) {
        return value & HALF;
    }

    /** The upper 16 bits of a number. */
    public static int high(int value) {
        return (value >>> 16) & HALF;
    }

    /** Puts the two halves back together. Each may arrive sign-extended; only its low 16 bits count. */
    public static int join(int low, int high) {
        return ((high & HALF) << 16) | (low & HALF);
    }
}
