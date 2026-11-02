package com.itmecasey.aeroflux.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itmecasey.aeroflux.core.FluxType;
import org.junit.jupiter.api.Test;

class MachineTypeTest {
    @Test
    void everyMachineFitsItsPanel() {
        for (MachineType type : MachineType.values()) {
            MachineLayout layout = MachineLayout.of(type.inputSlots(), type.outputSlots());
            assertEquals(type.inputSlots(), layout.inputCount());
            assertEquals(type.outputSlots(), layout.outputCount());
            // Only the Sky Controller works with no slots, and only the Breeder has nothing to put out.
            if (type != MachineType.SKY_CONTROLLER) {
                assertTrue(type.inputSlots() >= 1, type.id());
            }
            if (type != MachineType.SKY_CONTROLLER && type != MachineType.BREEDER) {
                assertTrue(type.outputSlots() >= 1, type.id());
            }
            assertTrue(type.extraTanks().size() <= 2, type.id());
        }
    }

    @Test
    void idsAreUniqueAndLookUpBothWays() {
        for (MachineType type : MachineType.values()) {
            assertEquals(type, MachineType.byId(type.id()));
        }
        assertNull(MachineType.byId("toaster"));
        assertEquals("alloy_smelter", MachineType.ALLOY_SMELTER.id());
    }

    @Test
    void fluxMatchesThePlan() {
        for (MachineType type : new MachineType[] {MachineType.FURNACE, MachineType.GRINDER, MachineType.SAWMILL, MachineType.ALLOY_SMELTER}) {
            assertEquals(java.util.Set.of(FluxType.AERO), type.acceptedFlux(), type.id());
        }
        for (MachineType type : new MachineType[] {MachineType.INFUSER, MachineType.ENCHANTER, MachineType.DISENCHANTER}) {
            assertEquals(java.util.Set.of(FluxType.MOON), type.acceptedFlux(), type.id());
        }
        assertEquals(java.util.Set.of(FluxType.AERO, FluxType.MOON), MachineType.PLANT_GROWER.acceptedFlux());
    }

    @Test
    void onlyTheRecipeMachinesHaveRecipesOfTheirOwn() {
        assertFalse(MachineType.FURNACE.hasOwnRecipes());
        assertFalse(MachineType.ENCHANTER.hasOwnRecipes());
        assertFalse(MachineType.DISENCHANTER.hasOwnRecipes());
        assertTrue(MachineType.GRINDER.hasOwnRecipes());
        assertTrue(MachineType.PLANT_GROWER.hasOwnRecipes());
    }

    @Test
    void enchanterRefusesCursesButTakesTreasureByDefault() {
        assertFalse(EnchantRules.enchanterAllows(true, false));
        assertTrue(EnchantRules.enchanterAllows(false, true));
        assertTrue(EnchantRules.enchanterAllows(false, false));
    }

    @Test
    void pricesGrowWithLevels() {
        assertEquals(1, EnchantRules.lapisCost(0));
        assertEquals(5, EnchantRules.lapisCost(5));
        assertEquals(1_250, EnchantRules.enchantFlux(5));
        assertEquals(0, EnchantRules.enchantFlux(-3));
        assertEquals(300, EnchantRules.disenchantFlux(3));
        assertTrue(EnchantRules.baseTicks(5) > EnchantRules.baseTicks(1));
    }

    @Test
    void levelsAreCappedAndUpgradesNeedAHigherLevel() {
        assertEquals(3, EnchantRules.appliedLevel(5, 3));
        assertEquals(2, EnchantRules.appliedLevel(2, 3));
        assertEquals(1, EnchantRules.appliedLevel(0, 3));
        assertEquals(1, EnchantRules.appliedLevel(4, 0));
        assertTrue(EnchantRules.upgrades(1, 2));
        assertFalse(EnchantRules.upgrades(2, 2));
        assertFalse(EnchantRules.upgrades(3, 2));
    }

    @Test
    void chancesBehave() {
        assertTrue(OutputChance.drops(1.0F, 0.999_999));
        assertTrue(OutputChance.drops(1.5F, 0.99));
        assertFalse(OutputChance.drops(0.0F, 0.0));
        assertFalse(OutputChance.drops(-1.0F, 0.0));
        assertTrue(OutputChance.drops(0.25F, 0.24));
        assertFalse(OutputChance.drops(0.25F, 0.25));
    }
}
