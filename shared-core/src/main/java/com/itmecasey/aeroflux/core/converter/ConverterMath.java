package com.itmecasey.aeroflux.core.converter;

import com.itmecasey.aeroflux.core.Tier;

/**
 * The numbers behind the Flux converters. One mB of Flux, either kind, makes {@link #FE_PER_MB}
 * FE, or half as much AE, which is AE2's own rate of two FE to one AE. Each tier burns twice as
 * much Flux per tick as the one below, holds twice as much, and keeps a buffer of ten seconds of
 * its full output.
 */
public final class ConverterMath {
    /** Forge Energy made from one mB of Flux. */
    public static final int FE_PER_MB = 100;
    /** AE2 power made from one mB of Flux: AE2 counts two FE as one AE. */
    public static final int AE_PER_MB = FE_PER_MB / 2;
    /** How many ticks of full output the buffer holds. */
    private static final int BUFFER_TICKS = 200;

    private ConverterMath() {
    }

    /** The most Flux a converter of this tier burns in one tick, in mB: 1, 2, 4, 8, 16, 32. */
    public static int mbPerTick(Tier tier) {
        return 1 << tier.ordinal();
    }

    /** The FE a converter of this tier makes per tick at full speed. */
    public static int fePerTick(Tier tier) {
        return mbPerTick(tier) * FE_PER_MB;
    }

    /** The AE a converter of this tier makes per tick at full speed. */
    public static int aePerTick(Tier tier) {
        return mbPerTick(tier) * AE_PER_MB;
    }

    /** The Flux tank, in mB: 8 buckets at copper, doubling each tier. */
    public static int tankCapacity(Tier tier) {
        return 8_000 << tier.ordinal();
    }

    /** The FE (or AE, for the AE converter) the output buffer holds. */
    public static int bufferCapacity(Tier tier, ConverterType type) {
        int perTick = type == ConverterType.AE ? aePerTick(tier) : fePerTick(tier);
        return perTick * BUFFER_TICKS;
    }

    /**
     * How much Flux to burn this tick: as much as the tier allows, but no more than the tank holds
     * or the buffer has room to take.
     *
     * @param stored     Flux in the tank, mB
     * @param bufferRoom free space in the output buffer, in output units
     * @param perMb      output units made from one mB
     */
    public static int mbToBurn(Tier tier, int stored, long bufferRoom, int perMb) {
        long fits = bufferRoom / perMb;
        return (int) Math.max(0, Math.min(Math.min(mbPerTick(tier), stored), fits));
    }
}
