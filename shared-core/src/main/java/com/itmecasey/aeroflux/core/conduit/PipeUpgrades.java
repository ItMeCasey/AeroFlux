package com.itmecasey.aeroflux.core.conduit;

import com.itmecasey.aeroflux.core.Tier;

/**
 * What upgrades do to a pipe, and how many a pipe can hold.
 *
 * <p>Each upgrade adds less than the one before it, so stacking them stays useful without making
 * the top tier pointless. Filter and priority upgrades need a screen to configure and come later.
 */
public final class PipeUpgrades {
    /** The kinds of upgrade that exist so far. */
    public enum Kind {
        /** Moves things faster. */
        SPEED,
        /** Moves more at a time. */
        CAPACITY
    }

    /** Speed factor by number of Speed upgrades, 0 to 4. */
    private static final double[] SPEED_FACTOR = {1.0, 1.5, 1.9, 2.2, 2.4};
    /** Capacity factor by number of Capacity upgrades, 0 to 4. */
    private static final double[] CAPACITY_FACTOR = {1.0, 2.0, 2.8, 3.4, 3.8};

    private PipeUpgrades() {
    }

    /** How many upgrade slots a pipe of this tier has: three, or four from netherite up. */
    public static int slots(Tier tier) {
        return tier.ordinal() >= Tier.NETHERITE.ordinal() ? 4 : 3;
    }

    /** The factor Speed upgrades give: more fluid per tick, shorter waits between item batches. */
    public static double speedFactor(int upgrades) {
        return SPEED_FACTOR[clamp(upgrades)];
    }

    /** The factor Capacity upgrades give: a bigger share per tick, bigger item batches. */
    public static double capacityFactor(int upgrades) {
        return CAPACITY_FACTOR[clamp(upgrades)];
    }

    /**
     * What a fluid, energy or gas pipe moves per tick once upgraded. Both kinds of upgrade raise
     * the rate: Speed makes it quicker and Capacity makes each push bigger.
     */
    public static long amountPerTick(PipeType type, Tier tier, int speedUpgrades, int capacityUpgrades) {
        double base = PipeTiers.amountPerTick(type, tier);
        return Math.round(base * speedFactor(speedUpgrades) * capacityFactor(capacityUpgrades));
    }

    /** How many items an upgraded item pipe moves in one operation. */
    public static int itemsPerOperation(Tier tier, int capacityUpgrades) {
        return (int) Math.max(1, Math.round(PipeTiers.itemsPerOperation(tier) * capacityFactor(capacityUpgrades)));
    }

    /** How many ticks an upgraded item pipe waits between operations; never less than one. */
    public static int ticksPerOperation(Tier tier, int speedUpgrades) {
        return (int) Math.max(1, Math.round(PipeTiers.ticksPerOperation(tier) / speedFactor(speedUpgrades)));
    }

    private static int clamp(int upgrades) {
        return Math.max(0, Math.min(upgrades, SPEED_FACTOR.length - 1));
    }
}
