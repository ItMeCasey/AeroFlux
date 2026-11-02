package com.itmecasey.aeroflux.core.conduit;

import com.itmecasey.aeroflux.core.Tier;

/**
 * How much each kind of pipe can move at each tier. These are the built-in numbers from the
 * plan, meant to be rebalanced later, so nothing else should hard-code them.
 *
 * <p>Fluid, energy and gas pipes move an amount per tick. Item pipes move a batch of items every
 * so many ticks. Redstone pipes carry a number of independent channels. ME pipes have no tiers.
 */
public final class PipeTiers {
    /** Fluid per tick in mB, copper to fluxium. */
    private static final int[] FLUID = {100, 250, 650, 1_800, 4_600, 12_000};
    /** Energy per tick in FE, copper to fluxium. */
    private static final int[] ENERGY = {1_000, 4_000, 16_000, 64_000, 250_000, 1_000_000};
    /** Gas per tick in mB, copper to fluxium. */
    private static final int[] GAS = {256, 800, 2_400, 7_000, 21_000, 64_000};
    /** Items moved in one operation, copper to fluxium. */
    private static final int[] ITEMS_PER_OPERATION = {1, 4, 8, 16, 32, 64};
    /** Ticks between two item operations, copper to fluxium. */
    private static final int[] TICKS_PER_OPERATION = {20, 16, 12, 8, 4, 2};
    /** Redstone channels a pipe carries, copper to fluxium. */
    private static final int[] REDSTONE_CHANNELS = {1, 2, 4, 8, 12, 16};

    private PipeTiers() {
    }

    /** Fluid a fluid pipe moves per tick, in mB. */
    public static int fluidPerTick(Tier tier) {
        return FLUID[tier.ordinal()];
    }

    /** Energy an energy pipe moves per tick, in FE. */
    public static int energyPerTick(Tier tier) {
        return ENERGY[tier.ordinal()];
    }

    /** Gas a gas pipe moves per tick, in mB. */
    public static int gasPerTick(Tier tier) {
        return GAS[tier.ordinal()];
    }

    /** Items an item pipe moves in one operation. */
    public static int itemsPerOperation(Tier tier) {
        return ITEMS_PER_OPERATION[tier.ordinal()];
    }

    /** Ticks an item pipe waits between operations. */
    public static int ticksPerOperation(Tier tier) {
        return TICKS_PER_OPERATION[tier.ordinal()];
    }

    /** Redstone channels a redstone pipe carries. */
    public static int redstoneChannels(Tier tier) {
        return REDSTONE_CHANNELS[tier.ordinal()];
    }

    /** Whether pipes of this type come in tiers; ME pipes do not. */
    public static boolean isTiered(PipeType type) {
        return type != PipeType.ME && type != PipeType.DENSE_ME;
    }

    /**
     * The amount a network of this type may move per tick, for the types that move an amount
     * (fluid, energy and gas). A network moves at the speed of its slowest pipe.
     *
     * @return the limit per tick, or 0 for types that do not move a plain amount
     */
    public static long amountPerTick(PipeType type, Tier tier) {
        return switch (type) {
            case FLUID -> fluidPerTick(tier);
            case ENERGY -> energyPerTick(tier);
            case GAS -> gasPerTick(tier);
            default -> 0L;
        };
    }
}
