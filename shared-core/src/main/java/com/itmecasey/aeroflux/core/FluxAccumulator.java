package com.itmecasey.aeroflux.core;

/**
 * Turns a fractional per-tick rate into whole millibuckets.
 *
 * <p>A collector can make, say, 2.4 mB per tick in the rain. Fluid comes in whole millibuckets,
 * so the fraction is carried over to the next tick instead of being rounded away, and the
 * long-run total matches the rate exactly.
 */
public final class FluxAccumulator {
    private double carry;

    /**
     * Adds one tick of production.
     *
     * @param perTick the fluid produced this tick, in mB (may be fractional)
     * @return the whole millibuckets now ready to be stored; the remainder stays carried
     */
    public int add(double perTick) {
        carry += perTick;
        int whole = (int) carry;
        carry -= whole;
        return whole;
    }

    /** Drops the carried fraction, for when a full tank means the extra is lost anyway. */
    public void reset() {
        carry = 0.0;
    }

    /** The fraction currently carried over, always in the range [0, 1) after {@link #add}. */
    public double carry() {
        return carry;
    }

    /** Restores a saved fraction. Out-of-range values (a damaged save) are clamped. */
    public void setCarry(double carry) {
        this.carry = Math.max(0.0, Math.min(carry, 0.999_999));
    }
}
