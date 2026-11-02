package com.itmecasey.aeroflux.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The config side of the Enchanter and Disenchanter: every switch changes what the rules answer. */
class EnchantRulesTest {
    /** Mending is in the treasure tag; Sharpness is in none here. */
    private static final Predicate<String> MENDING_TAGS = Set.of("minecraft:treasure")::contains;
    private static final Predicate<String> NO_TAGS = tag -> false;

    @AfterEach
    void backToDefaults() {
        EnchantRules.use(EnchantRules.DEFAULTS);
    }

    private static EnchantRules.Settings settings(boolean curses, boolean treasure, int maxLevel, int lapis,
                                                  boolean xp, List<String> blocked, List<String> allowed) {
        return new EnchantRules.Settings(curses, treasure, maxLevel, 400, lapis, 50, xp, 4, blocked, allowed);
    }

    @Test
    void theDefaultsAreThePlansNumbers() {
        assertEquals(1_250, EnchantRules.enchantFlux(5));
        assertEquals(5, EnchantRules.lapisCost(5));
        assertEquals(300, EnchantRules.disenchantFlux(3));
        assertEquals(0, EnchantRules.disenchantXp(3), "no XP back unless switched on");
        assertTrue(EnchantRules.listsAllow("minecraft:mending", MENDING_TAGS));
    }

    @Test
    void pricesFollowTheConfig() {
        EnchantRules.use(settings(false, true, 0, 2, true, List.of(), List.of()));
        assertEquals(2_000, EnchantRules.enchantFlux(5));
        assertEquals(10, EnchantRules.lapisCost(5));
        assertEquals(150, EnchantRules.disenchantFlux(3));
        assertEquals(12, EnchantRules.disenchantXp(3));
    }

    @Test
    void freeLapisStillTakesOne() {
        EnchantRules.use(settings(false, true, 0, 0, false, List.of(), List.of()));
        assertEquals(1, EnchantRules.lapisCost(5));
    }

    @Test
    void cursesAndTreasureCanBeSwitched() {
        EnchantRules.use(settings(true, false, 0, 1, false, List.of(), List.of()));
        assertTrue(EnchantRules.enchanterAllows(true, false));
        assertFalse(EnchantRules.enchanterAllows(false, true));
    }

    @Test
    void theLevelCapNeverRaisesAnEnchantmentsOwnMaximum() {
        EnchantRules.use(settings(false, true, 3, 1, false, List.of(), List.of()));
        assertEquals(3, EnchantRules.appliedLevel(5, 5));
        assertEquals(2, EnchantRules.appliedLevel(5, 2));
        assertEquals(1, EnchantRules.appliedLevel(0, 5));
    }

    @Test
    void blockedEntriesMatchIdsAndTags() {
        EnchantRules.use(settings(false, true, 0, 1, false, List.of("minecraft:sharpness", " #minecraft:treasure"), List.of()));
        assertFalse(EnchantRules.listsAllow("minecraft:sharpness", NO_TAGS));
        assertFalse(EnchantRules.listsAllow("minecraft:mending", MENDING_TAGS));
        assertTrue(EnchantRules.listsAllow("minecraft:efficiency", NO_TAGS));
    }

    @Test
    void anAllowListLetsOnlyItsEntriesThrough() {
        EnchantRules.use(settings(false, true, 0, 1, false, List.of("minecraft:mending"), List.of("#minecraft:treasure", "minecraft:unbreaking")));
        assertTrue(EnchantRules.listsAllow("minecraft:unbreaking", NO_TAGS));
        assertFalse(EnchantRules.listsAllow("minecraft:sharpness", NO_TAGS));
        // Blocked wins over allowed.
        assertFalse(EnchantRules.listsAllow("minecraft:mending", MENDING_TAGS));
    }
}
