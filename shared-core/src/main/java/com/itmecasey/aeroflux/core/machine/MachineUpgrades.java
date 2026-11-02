package com.itmecasey.aeroflux.core.machine;

import com.itmecasey.aeroflux.core.Tier;

/**
 * What upgrades do to a machine, and how many it can hold. Machines take the same Speed and
 * Capacity upgrade items as pipes, but the numbers are their own.
 *
 * <ul>
 *   <li><b>Speed</b> makes every job shorter and burns more Flux each tick while working. The
 *       Flux per tick grows faster than the speed does, so a sped-up machine costs a little more
 *       per item, the usual Mekanism-style trade of Flux for time.</li>
 *   <li><b>Capacity</b> adds the tier's whole tank size again for each upgrade.</li>
 * </ul>
 *
 * <p>With no upgrades every number here is exactly the one {@link MachineMath} gives, so an
 * unupgraded machine works as it always did. Counts above {@link #MAX_PER_KIND} count as that
 * many, and negative counts as none.
 */
public final class MachineUpgrades {
    /** The most upgrades of one kind that make a difference; no machine has more slots than this anyway. */
    public static final int MAX_PER_KIND = 4;

    /** Work speed in percent, by number of Speed upgrades from 0 to 4. */
    private static final int[] SPEED_PERCENT = {100, 150, 200, 250, 300};
    /** Flux burned per tick in percent, by number of Speed upgrades from 0 to 4. */
    private static final int[] FLUX_PERCENT = {100, 160, 230, 310, 400};
    private static final int PERCENT = 100;

    private MachineUpgrades() {
    }

    /** How many upgrade slots a machine of this tier has: three, or four from netherite up, the same as pipes. */
    public static int slots(Tier tier) {
        return tier.ordinal() >= Tier.NETHERITE.ordinal() ? 4 : 3;
    }

    /** The work speed Speed upgrades give, in percent of the tier's own speed. */
    public static int speedPercent(int speedUpgrades) {
        return SPEED_PERCENT[clamp(speedUpgrades)];
    }

    /** The Flux per tick Speed upgrades cost, in percent of the tier's own cost. */
    public static int fluxPercent(int speedUpgrades) {
        return FLUX_PERCENT[clamp(speedUpgrades)];
    }

    /**
     * How many ticks one job takes. The recipe's time is divided by the tier's speed and by the
     * upgrade speed, then rounded up, and a job always takes at least a tick. With no upgrades this
     * is {@link MachineMath#ticksFor}.
     */
    public static int ticksFor(int baseTicks, Tier tier, int speedUpgrades) {
        long work = (long) Math.max(baseTicks, 0) * PERCENT;
        long rate = (long) MachineMath.speed(tier) * speedPercent(speedUpgrades);
        return (int) Math.max(1L, (work + rate - 1) / rate);
    }

    /** Flux burned each working tick, in mB, rounded up. With no upgrades this is {@link MachineMath#fluxPerTick}. */
    public static int fluxPerTick(Tier tier, int speedUpgrades) {
        return (MachineMath.fluxPerTick(tier) * fluxPercent(speedUpgrades) + PERCENT - 1) / PERCENT;
    }

    /** All the Flux one uninterrupted job costs, in mB. */
    public static int fluxForJob(int baseTicks, Tier tier, int speedUpgrades) {
        return ticksFor(baseTicks, tier, speedUpgrades) * fluxPerTick(tier, speedUpgrades);
    }

    /** The size of the Flux tank, in mB: the tier's size once, plus once more for every Capacity upgrade. */
    public static int inputCapacity(Tier tier, int capacityUpgrades) {
        return MachineMath.inputCapacity(tier) * (1 + clamp(capacityUpgrades));
    }

    private static int clamp(int upgrades) {
        return Math.max(0, Math.min(upgrades, MAX_PER_KIND));
    }
}
