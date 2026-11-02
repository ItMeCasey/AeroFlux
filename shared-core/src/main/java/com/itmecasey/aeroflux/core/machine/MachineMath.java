package com.itmecasey.aeroflux.core.machine;

import com.itmecasey.aeroflux.core.Tier;

/**
 * How tiers change what a machine does: how fast it works, how much Flux it burns while it does,
 * and how much Flux it can hold. These are the built-in numbers, meant to be rebalanced in the
 * balance pass, so nothing else should hard-code them.
 *
 * <p>Higher tiers burn more Flux each tick but work so much faster that one finished item costs
 * less, which is what makes upgrading worth it. A copper machine spends 2 mB/t for 100 ticks per
 * smelt (200 mB); a fluxium one spends 8 mB/t for 17 ticks (136 mB).
 */
public final class MachineMath {
    /** Work speed relative to the vanilla furnace (which is 1x), copper to fluxium. */
    private static final int[] SPEED = {2, 3, 4, 6, 8, 12};
    /** Flux burned per tick while working, in mB, copper to fluxium. */
    private static final int[] FLUX_PER_TICK = {2, 3, 4, 5, 6, 8};
    /** Input tank capacity in buckets, copper to fluxium. */
    private static final int[] INPUT_BUCKETS = {4, 8, 16, 32, 64, 128};

    private static final int MB_PER_BUCKET = 1_000;

    private MachineMath() {
    }

    /** How many times faster than the vanilla furnace a machine of this tier works. */
    public static int speed(Tier tier) {
        return SPEED[tier.ordinal()];
    }

    /** Flux a machine of this tier burns each tick it is working, in mB. */
    public static int fluxPerTick(Tier tier) {
        return FLUX_PER_TICK[tier.ordinal()];
    }

    /** Capacity of a machine's Flux input tank, in mB. */
    public static int inputCapacity(Tier tier) {
        return INPUT_BUCKETS[tier.ordinal()] * MB_PER_BUCKET;
    }

    /**
     * How many ticks one job takes. The time a recipe asks for (200 ticks for a vanilla smelt) is
     * divided by the tier's speed and rounded up, and a job always takes at least a tick.
     */
    public static int ticksFor(int baseTicks, Tier tier) {
        int speed = speed(tier);
        return Math.max(1, (Math.max(baseTicks, 0) + speed - 1) / speed);
    }

    /** All the Flux one job costs, in mB, if it is never interrupted. */
    public static int fluxForJob(int baseTicks, Tier tier) {
        return ticksFor(baseTicks, tier) * fluxPerTick(tier);
    }

    /** How many ticks a machine can work on the Flux it holds. */
    public static int ticksOfFlux(long amount, Tier tier) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(amount, 0L) / fluxPerTick(tier));
    }
}
