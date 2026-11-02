package com.itmecasey.aeroflux.core.conduit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itmecasey.aeroflux.core.Tier;
import org.junit.jupiter.api.Test;

class UpgradeTest {

    @Test
    void slotsGrowAtTheTopTiers() {
        assertEquals(3, PipeUpgrades.slots(Tier.COPPER));
        assertEquals(3, PipeUpgrades.slots(Tier.DIAMOND));
        assertEquals(4, PipeUpgrades.slots(Tier.NETHERITE));
        assertEquals(4, PipeUpgrades.slots(Tier.FLUXIUM));
    }

    @Test
    void everyUpgradeHelpsButLessThanTheLast() {
        for (int count = 1; count <= 4; count++) {
            double speedGain = PipeUpgrades.speedFactor(count) - PipeUpgrades.speedFactor(count - 1);
            double capacityGain = PipeUpgrades.capacityFactor(count) - PipeUpgrades.capacityFactor(count - 1);
            assertTrue(speedGain > 0 && capacityGain > 0, "upgrade " + count + " helps");
            if (count > 1) {
                assertTrue(speedGain < PipeUpgrades.speedFactor(count - 1) - PipeUpgrades.speedFactor(count - 2), "speed " + count);
                assertTrue(capacityGain < PipeUpgrades.capacityFactor(count - 1) - PipeUpgrades.capacityFactor(count - 2), "capacity " + count);
            }
        }
    }

    @Test
    void noUpgradesChangesNothing() {
        assertEquals(100, PipeUpgrades.amountPerTick(PipeType.FLUID, Tier.COPPER, 0, 0));
        assertEquals(1, PipeUpgrades.itemsPerOperation(Tier.COPPER, 0));
        assertEquals(20, PipeUpgrades.ticksPerOperation(Tier.COPPER, 0));
    }

    @Test
    void upgradesRaiseFluidSpeedAndShortenItemWaits() {
        assertEquals(Math.round(100 * 1.5 * 2.0), PipeUpgrades.amountPerTick(PipeType.FLUID, Tier.COPPER, 1, 1));
        assertTrue(PipeUpgrades.ticksPerOperation(Tier.COPPER, 3) < 20);
        assertEquals(1, PipeUpgrades.ticksPerOperation(Tier.FLUXIUM, 4) >= 1 ? 1 : 0, "never below one tick");
        assertTrue(PipeUpgrades.itemsPerOperation(Tier.COPPER, 2) > 1);
    }

    @Test
    void outOfRangeCountsAreClamped() {
        assertEquals(PipeUpgrades.speedFactor(4), PipeUpgrades.speedFactor(99), 1e-9);
        assertEquals(PipeUpgrades.capacityFactor(0), PipeUpgrades.capacityFactor(-3), 1e-9);
    }

    @Test
    void aPipeTakesUpgradesUntilItsSlotsAreFull() {
        BundleState state = new BundleState();
        state.addPipe(PipeType.FLUID, Tier.COPPER);
        int lane = PipeType.FLUID.lane();
        assertTrue(state.addUpgrade(lane, PipeUpgrades.Kind.SPEED));
        assertTrue(state.addUpgrade(lane, PipeUpgrades.Kind.CAPACITY));
        assertTrue(state.addUpgrade(lane, PipeUpgrades.Kind.SPEED));
        assertFalse(state.addUpgrade(lane, PipeUpgrades.Kind.CAPACITY), "a copper pipe has three slots");
        assertEquals(2, state.speedUpgrades(lane));
        assertEquals(1, state.capacityUpgrades(lane));
    }

    @Test
    void anEmptyLaneTakesNoUpgrades() {
        BundleState state = new BundleState();
        assertFalse(state.addUpgrade(PipeType.ITEM.lane(), PipeUpgrades.Kind.SPEED));
    }

    @Test
    void removingUpgradesGivesThemBack() {
        BundleState state = new BundleState();
        state.addPipe(PipeType.ENERGY, Tier.GOLD);
        int lane = PipeType.ENERGY.lane();
        state.addUpgrade(lane, PipeUpgrades.Kind.SPEED);
        state.addUpgrade(lane, PipeUpgrades.Kind.SPEED);
        assertEquals(2, state.removeUpgrades(lane, PipeUpgrades.Kind.SPEED));
        assertEquals(0, state.speedUpgrades(lane));
    }

    @Test
    void upgradesSurviveSavingAndOldDataLoadsWithNone() {
        BundleState state = new BundleState();
        state.addPipe(PipeType.ITEM, Tier.NETHERITE);
        int lane = PipeType.ITEM.lane();
        for (int i = 0; i < 4; i++) {
            state.addUpgrade(lane, i % 2 == 0 ? PipeUpgrades.Kind.SPEED : PipeUpgrades.Kind.CAPACITY);
        }
        BundleState copy = BundleState.deserialize(state.serialize());
        assertEquals(state, copy);
        assertEquals(2, copy.speedUpgrades(lane));
        assertEquals(2, copy.capacityUpgrades(lane));

        int[] current = state.serialize();
        int[] beforeUpgrades = java.util.Arrays.copyOf(current, current.length - 2);
        BundleState old = BundleState.deserialize(beforeUpgrades);
        assertEquals(0, old.upgradeCount(lane));
        assertEquals(Tier.NETHERITE, old.tier(lane));
    }

    @Test
    void removingAPipeDropsItsUpgrades() {
        BundleState state = new BundleState();
        state.addPipe(PipeType.GAS);
        state.addUpgrade(PipeType.GAS.lane(), PipeUpgrades.Kind.CAPACITY);
        state.removeLane(PipeType.GAS.lane());
        state.addPipe(PipeType.GAS);
        assertEquals(0, state.upgradeCount(PipeType.GAS.lane()));
    }

    // --- Pipes that do not distribute -------------------------------------------------------

    @Test
    void onlyFluidItemEnergyAndGasDistribute() {
        for (PipeType type : PipeType.values()) {
            boolean expected = type == PipeType.FLUID || type == PipeType.ITEM
                    || type == PipeType.ENERGY || type == PipeType.GAS;
            assertEquals(expected, type.usesDistribution(), type.id());
        }
    }

    @Test
    void redstoneAndMePipesIgnoreADistributionMode() {
        BundleState state = new BundleState();
        state.addPipe(PipeType.REDSTONE);
        state.addPipe(PipeType.ME);
        state.setDistribution(PipeType.REDSTONE.lane(), DistributionMode.RANDOM);
        state.setDistribution(PipeType.ME.lane(), DistributionMode.LAST_TO_FIRST);
        assertEquals(DistributionMode.ROUND_ROBIN, state.distribution(PipeType.REDSTONE.lane()));
        assertEquals(DistributionMode.ROUND_ROBIN, state.distribution(PipeType.ME.lane()));
    }

    @Test
    void anEmptyLaneIgnoresADistributionMode() {
        BundleState state = new BundleState();
        state.setDistribution(PipeType.FLUID.lane(), DistributionMode.RANDOM);
        state.addPipe(PipeType.FLUID);
        assertEquals(DistributionMode.ROUND_ROBIN, state.distribution(PipeType.FLUID.lane()));
    }
}
