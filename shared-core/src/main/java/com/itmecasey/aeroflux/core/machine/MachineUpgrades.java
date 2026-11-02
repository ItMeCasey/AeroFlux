package com.itmecasey.aeroflux.core.machine;

import com.itmecasey.aeroflux.core.Tier;
import java.util.function.DoubleSupplier;

/**
 * What upgrades do to a machine, and how many it can hold. Machines take the same Speed and
 * Capacity upgrade items as pipes, but the numbers are their own, and four more upgrades that only
 * machines take (see {@link Special}).
 *
 * <ul>
 *   <li><b>Speed</b> makes every job shorter and burns more Flux each tick while working. The
 *       Flux per tick grows faster than the speed does, so a sped-up machine costs a little more
 *       per item, the usual Mekanism-style trade of Flux for time.</li>
 *   <li><b>Capacity</b> adds the tier's whole tank size again for each upgrade.</li>
 *   <li><b>Efficiency</b> cuts the Flux burned per tick, with less gained from each one after the first.</li>
 *   <li><b>Fortune</b> (Grinder, Sifter) and <b>Purity</b> (Purifier) give a chance of more product.</li>
 *   <li><b>Silk Touch</b> (Plant Grower) harvests whole blocks where a recipe says how.</li>
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
    /** What is left of the Flux per tick in percent, by number of Efficiency upgrades from 0 to 4. */
    private static final int[] EFFICIENCY_PERCENT = {100, 80, 65, 55, 50};
    /** The chance, in percent, that each Fortune upgrade adds one more of the main product. */
    public static final int FORTUNE_CHANCE = 30;
    /** The chance, in percent, that each Purity upgrade adds one more of the main product. */
    public static final int PURITY_CHANCE = 50;
    /** How much each Fortune upgrade raises a Sifter product's chance, in percentage points. */
    public static final int SIFTER_FORTUNE_POINTS = 10;

    /** The upgrades only machines take, each fitting only the machines it does something in. */
    public enum Special {
        EFFICIENCY("efficiency"),
        FORTUNE("fortune"),
        SILK_TOUCH("silk_touch"),
        PURITY("purity");

        private final String id;

        Special(String id) {
            this.id = id;
        }

        /** The name used in item ids and translation keys, such as {@code silk_touch}. */
        public String id() {
            return id;
        }

        /** Whether this upgrade does anything in that machine, and so whether its slots take it. */
        public boolean fits(MachineType machine) {
            return switch (this) {
                case EFFICIENCY -> true;
                case FORTUNE -> machine == MachineType.GRINDER || machine == MachineType.SIFTER;
                case SILK_TOUCH -> machine == MachineType.PLANT_GROWER;
                case PURITY -> machine == MachineType.PURIFIER;
            };
        }
    }

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
        return fluxPerTick(tier, speedUpgrades, 0);
    }

    /** What is left of the Flux per tick with Efficiency upgrades, in percent. */
    public static int efficiencyPercent(int efficiencyUpgrades) {
        return EFFICIENCY_PERCENT[clamp(efficiencyUpgrades)];
    }

    /**
     * Flux burned each working tick with Speed and Efficiency upgrades, in mB, rounded up. A
     * working machine always burns at least 1 mB a tick.
     */
    public static int fluxPerTick(Tier tier, int speedUpgrades, int efficiencyUpgrades) {
        long scaled = (long) MachineMath.fluxPerTick(tier) * fluxPercent(speedUpgrades) * efficiencyPercent(efficiencyUpgrades);
        long whole = PERCENT * PERCENT;
        return (int) Math.max(1L, (scaled + whole - 1) / whole);
    }

    /**
     * How many more of the main product Fortune or Purity upgrades add to one finished job: each
     * upgrade adds one with its own chance.
     *
     * @param upgrades      how many of the upgrade are installed
     * @param chancePercent the chance each one has, such as {@link #FORTUNE_CHANCE}
     * @param rolls         random numbers from 0 (inclusive) to 1 (exclusive), one taken per upgrade
     */
    public static int bonusItems(int upgrades, int chancePercent, DoubleSupplier rolls) {
        int bonus = 0;
        for (int i = 0; i < clamp(upgrades); i++) {
            if (rolls.getAsDouble() * PERCENT < chancePercent) {
                bonus++;
            }
        }
        return bonus;
    }

    /** A Sifter product's chance with Fortune upgrades: raised by {@link #SIFTER_FORTUNE_POINTS} points each, up to certain. */
    public static double sifterChance(double chance, int fortuneUpgrades) {
        return Math.min(1.0, chance + clamp(fortuneUpgrades) * SIFTER_FORTUNE_POINTS / (double) PERCENT);
    }

    /** All the Flux one uninterrupted job costs, in mB. */
    public static int fluxForJob(int baseTicks, Tier tier, int speedUpgrades) {
        return ticksFor(baseTicks, tier, speedUpgrades) * fluxPerTick(tier, speedUpgrades);
    }

    /** The size of the Flux tank, in mB: the tier's size once, plus once more for every Capacity upgrade. */
    public static int inputCapacity(Tier tier, int capacityUpgrades) {
        return inputCapacity(MachineMath.inputCapacity(tier), capacityUpgrades);
    }

    /**
     * The size of a tank whose own size is {@code baseCapacity} mB (a server can set that in its
     * config): the base once, plus once more for every Capacity upgrade. A total too big for an
     * int stops at {@link Integer#MAX_VALUE}.
     */
    public static int inputCapacity(int baseCapacity, int capacityUpgrades) {
        long total = (long) Math.max(baseCapacity, 0) * (1 + clamp(capacityUpgrades));
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static int clamp(int upgrades) {
        return Math.max(0, Math.min(upgrades, MAX_PER_KIND));
    }
}
