package com.itmecasey.aeroflux.core.machine;

/**
 * The rules and prices of the Flux Enchanter and Disenchanter, as plan section 7 lays them out
 * and with its stated defaults: the Enchanter refuses curses, everything is priced per enchantment
 * level, and the Disenchanter keeps curses on the item. These numbers are meant to be moved into
 * the config; nothing else should hard-code them.
 */
public final class EnchantRules {
    /** Moon Flux the Enchanter takes per enchantment level it applies, in mB, on top of its running cost. */
    public static final int FLUX_PER_LEVEL = 250;
    /** Moon Flux the Disenchanter takes per level it strips, in mB. */
    public static final int DISENCHANT_FLUX_PER_LEVEL = 100;
    /** Whether the Enchanter applies curses from a book. */
    public static final boolean ALLOW_CURSES = false;
    /** Whether the Enchanter applies treasure enchantments (Mending and the like) from a book. */
    public static final boolean ALLOW_TREASURE = true;

    private static final int BASE_TICKS = 100;
    private static final int TICKS_PER_LEVEL = 20;

    private EnchantRules() {
    }

    /** Whether the Enchanter may apply an enchantment of this kind. */
    public static boolean enchanterAllows(boolean curse, boolean treasure) {
        return (ALLOW_CURSES || !curse) && (ALLOW_TREASURE || !treasure);
    }

    /** Lapis the Enchanter uses for a job applying this many levels in total (one per level, at least one). */
    public static int lapisCost(int totalLevels) {
        return Math.max(1, totalLevels);
    }

    /** Moon Flux the Enchanter uses on top of its running cost for this many levels, in mB. */
    public static int enchantFlux(int totalLevels) {
        return Math.max(0, totalLevels) * FLUX_PER_LEVEL;
    }

    /** Moon Flux the Disenchanter uses on top of its running cost for this many levels, in mB. */
    public static int disenchantFlux(int totalLevels) {
        return Math.max(0, totalLevels) * DISENCHANT_FLUX_PER_LEVEL;
    }

    /** How long a job takes before tier speed: more levels, more time. */
    public static int baseTicks(int totalLevels) {
        return BASE_TICKS + Math.max(0, totalLevels) * TICKS_PER_LEVEL;
    }

    /**
     * The level an enchantment is applied at: the book's level, never above what the enchantment
     * allows, and never below 1.
     */
    public static int appliedLevel(int bookLevel, int enchantmentMax) {
        return Math.max(1, Math.min(bookLevel, Math.max(1, enchantmentMax)));
    }

    /**
     * Whether an enchantment already on the item should be replaced by the book's: only if the
     * book's level is higher.
     */
    public static boolean upgrades(int currentLevel, int newLevel) {
        return newLevel > currentLevel;
    }
}
