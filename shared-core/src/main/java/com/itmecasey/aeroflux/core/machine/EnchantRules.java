package com.itmecasey.aeroflux.core.machine;

import java.util.List;
import java.util.function.Predicate;

/**
 * The rules and prices of the Flux Enchanter and Disenchanter, as plan section 7 lays them out:
 * the Enchanter refuses curses, everything is priced per enchantment level, and the Disenchanter
 * keeps curses on the item. Every number and switch comes from the config through
 * {@link #use(Settings)}; until the config is read, the defaults below apply.
 *
 * <p>Enchantments are named the way a datapack names them, so modded ones work too: an id such as
 * {@code minecraft:mending}, or a tag such as {@code #minecraft:treasure}.
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
    /** Experience the Disenchanter gives back per level it strips, when that is switched on. */
    public static final int XP_PER_LEVEL = 3;

    private static final int BASE_TICKS = 100;
    private static final int TICKS_PER_LEVEL = 20;

    /**
     * Everything a server can change about the two machines.
     *
     * @param allowCurses     whether the Enchanter applies curses from a book
     * @param allowTreasure   whether the Enchanter applies treasure enchantments from a book
     * @param maxLevel        the highest level the Enchanter applies; 0 means each enchantment's own maximum
     * @param fluxPerLevel    Moon Flux per level for the Enchanter, in mB
     * @param lapisPerLevel   lapis per level for the Enchanter (a job always takes at least one)
     * @param disenchantFluxPerLevel Moon Flux per level for the Disenchanter, in mB
     * @param returnXp        whether the Disenchanter gives back experience for what it strips
     * @param xpPerLevel      how much experience per stripped level, when {@code returnXp} is on
     * @param blocked         enchantments neither machine moves: the Enchanter leaves them off and
     *                        the Disenchanter leaves them on the item
     * @param allowed         if not empty, the only enchantments the machines move
     */
    public record Settings(boolean allowCurses, boolean allowTreasure, int maxLevel, int fluxPerLevel,
                           int lapisPerLevel, int disenchantFluxPerLevel, boolean returnXp, int xpPerLevel,
                           List<String> blocked, List<String> allowed) {
        public Settings {
            maxLevel = Math.max(0, maxLevel);
            fluxPerLevel = Math.max(0, fluxPerLevel);
            lapisPerLevel = Math.max(0, lapisPerLevel);
            disenchantFluxPerLevel = Math.max(0, disenchantFluxPerLevel);
            xpPerLevel = Math.max(0, xpPerLevel);
            blocked = List.copyOf(blocked);
            allowed = List.copyOf(allowed);
        }
    }

    /** The settings the plan starts from. */
    public static final Settings DEFAULTS = new Settings(ALLOW_CURSES, ALLOW_TREASURE, 0, FLUX_PER_LEVEL, 1,
            DISENCHANT_FLUX_PER_LEVEL, false, XP_PER_LEVEL, List.of(), List.of());

    private static volatile Settings settings = DEFAULTS;

    private EnchantRules() {
    }

    /** Switches to new settings, read from the config when it loads or reloads. */
    public static void use(Settings newSettings) {
        settings = newSettings;
    }

    /** The settings in use. */
    public static Settings settings() {
        return settings;
    }

    /** Whether the Enchanter may apply an enchantment of this kind. */
    public static boolean enchanterAllows(boolean curse, boolean treasure) {
        Settings now = settings;
        return (now.allowCurses() || !curse) && (now.allowTreasure() || !treasure);
    }

    /**
     * Whether the block and allow lists let the machines move an enchantment.
     *
     * @param id     the enchantment's id, such as {@code minecraft:sharpness}
     * @param hasTag answers whether the enchantment is in a tag, given the tag's id without the {@code #}
     */
    public static boolean listsAllow(String id, Predicate<String> hasTag) {
        Settings now = settings;
        if (matchesAny(now.blocked(), id, hasTag)) {
            return false;
        }
        return now.allowed().isEmpty() || matchesAny(now.allowed(), id, hasTag);
    }

    private static boolean matchesAny(List<String> entries, String id, Predicate<String> hasTag) {
        for (String entry : entries) {
            String trimmed = entry.trim();
            if (trimmed.startsWith("#") ? hasTag.test(trimmed.substring(1)) : trimmed.equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** Lapis the Enchanter uses for a job applying this many levels in total (at least one). */
    public static int lapisCost(int totalLevels) {
        return Math.max(1, Math.max(0, totalLevels) * settings.lapisPerLevel());
    }

    /** Moon Flux the Enchanter uses on top of its running cost for this many levels, in mB. */
    public static int enchantFlux(int totalLevels) {
        return Math.max(0, totalLevels) * settings.fluxPerLevel();
    }

    /** Moon Flux the Disenchanter uses on top of its running cost for this many levels, in mB. */
    public static int disenchantFlux(int totalLevels) {
        return Math.max(0, totalLevels) * settings.disenchantFluxPerLevel();
    }

    /** Experience the Disenchanter gives back for this many stripped levels: 0 unless switched on. */
    public static int disenchantXp(int totalLevels) {
        Settings now = settings;
        return now.returnXp() ? Math.max(0, totalLevels) * now.xpPerLevel() : 0;
    }

    /** How long a job takes before tier speed: more levels, more time. */
    public static int baseTicks(int totalLevels) {
        return BASE_TICKS + Math.max(0, totalLevels) * TICKS_PER_LEVEL;
    }

    /**
     * The level an enchantment is applied at: the book's level, never above what the enchantment
     * allows or the configured cap, and never below 1.
     */
    public static int appliedLevel(int bookLevel, int enchantmentMax) {
        int cap = Math.max(1, enchantmentMax);
        int configured = settings.maxLevel();
        if (configured > 0) {
            cap = Math.min(cap, configured);
        }
        return Math.max(1, Math.min(bookLevel, cap));
    }

    /**
     * Whether an enchantment already on the item should be replaced by the book's: only if the
     * book's level is higher.
     */
    public static boolean upgrades(int currentLevel, int newLevel) {
        return newLevel > currentLevel;
    }
}
