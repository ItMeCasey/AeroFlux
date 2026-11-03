package com.itmecasey.aeroflux.core.converter;

import com.itmecasey.aeroflux.core.Tier;

/**
 * The numbers behind the Flux Stress converter, which turns Flux into rotation for Create.
 *
 * <p>Create measures a generator by its speed (RPM) and its stress capacity (SU, the stress units
 * the machines on its shaft may draw in total). Each converter tier turns its shaft faster and
 * supplies twice the capacity of the one below, and at full load burns Flux at the same rate as
 * an FE converter of its tier: {@link #SU_PER_MB} SU for each mB per tick. With less load it
 * burns less, in step with the share of the capacity in use, so a shaft that drives nothing costs
 * nothing.
 */
public final class StressMath {
    /** Stress capacity one mB of Flux per tick pays for at full load. */
    public static final int SU_PER_MB = 256;
    /** The shaft speed of each tier, copper first: from a slow water wheel up to Create's top speed. */
    private static final int[] RPM = {32, 48, 64, 96, 128, 256};

    private StressMath() {
    }

    /** How fast the converter of this tier turns its shaft, in RPM. */
    public static int rpm(Tier tier) {
        return RPM[tier.ordinal()];
    }

    /** The stress capacity of this tier at the built-in balance, in SU: 256 at copper, doubling each tier. */
    public static int capacity(Tier tier) {
        return ConverterMath.mbPerTick(tier) * SU_PER_MB;
    }

    /**
     * Flux to burn this tick, in mB (fractional; the converter carries the remainder over).
     *
     * @param tier     the converter's tier
     * @param used     stress the network draws right now, in SU
     * @param capacity stress the network can take in total, in SU; the converter's own share is part of it
     */
    public static double burn(Tier tier, double used, double capacity) {
        if (capacity <= 0 || used <= 0) {
            return 0;
        }
        return ConverterMath.mbPerTick(tier) * Math.min(1.0, used / capacity);
    }
}
