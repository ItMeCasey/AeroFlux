package com.itmecasey.aeroflux.core.machine;

import com.itmecasey.aeroflux.core.Tier;

/**
 * How far the machines that act on the world around them reach, and how often the Sky Controller
 * may be used, by tier. Pure numbers, so they are unit tested.
 */
public final class MachineReach {
    /** The Sun and Moon Lamps and the Flux Magnet: 4 blocks for copper up to 16 for fluxium. */
    private static final int[] WIDE = {4, 6, 8, 10, 13, 16};
    /** The Animal Breeder: 3 blocks for copper up to 9 for fluxium. */
    private static final int[] PEN = {3, 4, 5, 6, 8, 9};

    /** The Sky Controller waits this long after copper's use; each tier halves it. */
    public static final int SKY_COOLDOWN_COPPER = 2400;

    private MachineReach() {
    }

    /** Radius of a lamp's light, in blocks. */
    public static int lampRadius(Tier tier) {
        return WIDE[tier.ordinal()];
    }

    /** Radius a magnet pulls items from, in blocks. */
    public static int magnetRadius(Tier tier) {
        return WIDE[tier.ordinal()];
    }

    /** Horizontal radius of the pen a breeder feeds, in blocks. */
    public static int breederRadius(Tier tier) {
        return PEN[tier.ordinal()];
    }

    /** Ticks the Sky Controller waits after a use. */
    public static int skyCooldown(Tier tier) {
        return SKY_COOLDOWN_COPPER >> tier.ordinal();
    }

    /** Crops a lamp nudges per second: one for copper, one more each tier. */
    public static int lampGrowthPerSecond(Tier tier) {
        return tier.ordinal() + 1;
    }
}
