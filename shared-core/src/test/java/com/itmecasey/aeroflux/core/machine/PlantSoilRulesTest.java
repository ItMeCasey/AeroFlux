package com.itmecasey.aeroflux.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itmecasey.aeroflux.core.machine.PlantSoilRules.Rule;
import com.itmecasey.aeroflux.core.machine.PlantSoilRules.Verdict;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlantSoilRulesTest {
    private static final Rule ESSENCE = new Rule(List.of("#mod:seeds"), List.of("mod:rich_farmland"), false, 1.0, 0.1);
    private static final Rule NETHER_WART = new Rule(List.of("minecraft:nether_wart"), List.of("minecraft:soul_sand"), true, 1.0, 0.0);

    @Test
    void theDefaultSoilWorksWithNoRules() {
        assertEquals(Verdict.PLAIN, PlantSoilRules.judge(List.of(), true, p -> true, s -> false));
    }

    @Test
    void aWrongSoilDoesNotGrow() {
        assertFalse(PlantSoilRules.judge(List.of(), false, p -> true, s -> false).grows());
    }

    @Test
    void aBonusSoilGivesItsBonusToTheNamedPlantsOnly() {
        Verdict essence = PlantSoilRules.judge(List.of(ESSENCE), true, "#mod:seeds"::equals, "mod:rich_farmland"::equals);
        assertTrue(essence.grows());
        assertEquals(0.1, essence.extraSeedChance(), 1e-9);
        Verdict wheat = PlantSoilRules.judge(List.of(ESSENCE), true, "minecraft:wheat_seeds"::equals, "mod:rich_farmland"::equals);
        assertEquals(0.0, wheat.extraSeedChance(), 1e-9);
    }

    @Test
    void aReplacingRuleTakesAwayTheDefaultSoil() {
        assertFalse(PlantSoilRules.judge(List.of(NETHER_WART), true, "minecraft:nether_wart"::equals, "minecraft:dirt"::equals).grows());
        assertTrue(PlantSoilRules.judge(List.of(NETHER_WART), false, "minecraft:nether_wart"::equals, "minecraft:soul_sand"::equals).grows());
    }

    @Test
    void aTierMatchedRuleOnlyPaysOutWhereTheTierMatches() {
        Rule matched = new Rule(List.of(), List.of("mod:rich_farmland"), false, 1.0, 0.1, true);
        Verdict off = PlantSoilRules.judge(List.of(ESSENCE, matched), true, p -> true, "mod:rich_farmland"::equals, () -> false);
        Verdict on = PlantSoilRules.judge(List.of(ESSENCE, matched), true, p -> true, "mod:rich_farmland"::equals, () -> true);
        assertEquals(0.1, off.extraSeedChance(), 1e-9);
        assertEquals(0.2, on.extraSeedChance(), 1e-9);
    }

    @Test
    void bonusesAddUpAndStopAtOne() {
        Rule big = new Rule(List.of(), List.of("a:b"), false, 2.0, 0.8);
        Verdict verdict = PlantSoilRules.judge(List.of(big, big), false, p -> true, "a:b"::equals);
        assertEquals(1.0, verdict.extraSeedChance(), 1e-9);
        assertEquals(2.0, verdict.speed(), 1e-9);
    }
}
