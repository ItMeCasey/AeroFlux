package com.itmecasey.aeroflux.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itmecasey.aeroflux.core.Tier;
import org.junit.jupiter.api.Test;

class MachineUpgradesTest {
    private static final int VANILLA_SMELT = 200;

    @Test
    void noUpgradesChangeNothing() {
        for (Tier tier : Tier.values()) {
            for (int base : new int[] {0, 1, 7, VANILLA_SMELT, 1_000}) {
                assertEquals(MachineMath.ticksFor(base, tier), MachineUpgrades.ticksFor(base, tier, 0), tier + " " + base);
            }
            assertEquals(MachineMath.fluxPerTick(tier), MachineUpgrades.fluxPerTick(tier, 0));
            assertEquals(MachineMath.inputCapacity(tier), MachineUpgrades.inputCapacity(tier, 0));
        }
    }

    @Test
    void speedUpgradesShortenJobsAndRaiseTheRunningCost() {
        // Copper: 200 / (2 x 1.5) is 66.67, rounded up; 2 mB x 1.6 is 3.2, rounded up.
        assertEquals(67, MachineUpgrades.ticksFor(VANILLA_SMELT, Tier.COPPER, 1));
        assertEquals(4, MachineUpgrades.fluxPerTick(Tier.COPPER, 1));
        assertEquals(50, MachineUpgrades.ticksFor(VANILLA_SMELT, Tier.COPPER, 2));
        assertEquals(5, MachineUpgrades.fluxPerTick(Tier.COPPER, 2));
        assertEquals(250, MachineUpgrades.fluxForJob(VANILLA_SMELT, Tier.COPPER, 2));
        // Fluxium with four: 200 / (12 x 3) is 5.6, so 6 ticks at 32 mB.
        assertEquals(6, MachineUpgrades.ticksFor(VANILLA_SMELT, Tier.FLUXIUM, 4));
        assertEquals(32, MachineUpgrades.fluxPerTick(Tier.FLUXIUM, 4));
    }

    @Test
    void everySpeedUpgradeHelpsAndCostsMorePerTick() {
        for (Tier tier : Tier.values()) {
            for (int count = 1; count <= MachineUpgrades.MAX_PER_KIND; count++) {
                assertTrue(MachineUpgrades.ticksFor(VANILLA_SMELT, tier, count) <= MachineUpgrades.ticksFor(VANILLA_SMELT, tier, count - 1));
                assertTrue(MachineUpgrades.fluxPerTick(tier, count) > MachineUpgrades.fluxPerTick(tier, count - 1));
                // Speed is paid for: a sped-up job never costs less Flux than a plain one.
                assertTrue(MachineUpgrades.fluxForJob(VANILLA_SMELT, tier, count) >= MachineMath.fluxForJob(VANILLA_SMELT, tier));
            }
        }
    }

    @Test
    void capacityAddsAWholeTankEach() {
        assertEquals(2_000, MachineUpgrades.inputCapacity(Tier.COPPER, 1));
        assertEquals(5_000, MachineUpgrades.inputCapacity(Tier.COPPER, 4));
        assertEquals(160_000, MachineUpgrades.inputCapacity(Tier.FLUXIUM, 4));
    }

    @Test
    void countsOutsideTheRangeAreClamped() {
        assertEquals(MachineUpgrades.ticksFor(VANILLA_SMELT, Tier.IRON, 4), MachineUpgrades.ticksFor(VANILLA_SMELT, Tier.IRON, 9));
        assertEquals(MachineUpgrades.inputCapacity(Tier.IRON, 0), MachineUpgrades.inputCapacity(Tier.IRON, -3));
        assertEquals(100, MachineUpgrades.speedPercent(-1));
        assertEquals(400, MachineUpgrades.fluxPercent(12));
    }

    @Test
    void slotsFollowThePipeRule() {
        assertEquals(3, MachineUpgrades.slots(Tier.COPPER));
        assertEquals(3, MachineUpgrades.slots(Tier.DIAMOND));
        assertEquals(4, MachineUpgrades.slots(Tier.NETHERITE));
        assertEquals(4, MachineUpgrades.slots(Tier.FLUXIUM));
    }

    @Test
    void theStripHoldsEverySlotOutsideThePanel() {
        for (Tier tier : Tier.values()) {
            UpgradeStripLayout strip = new UpgradeStripLayout(MachineUpgrades.slots(tier));
            MachineLayout.Rect bounds = strip.bounds();
            assertTrue(bounds.x() >= MachineLayout.WIDTH, "the strip must not cover the panel");
            assertTrue(bounds.y() + bounds.height() <= MachineLayout.HEIGHT, "the strip must not hang below the panel");
            MachineLayout.Rect button = strip.redstoneButton();
            assertTrue(inside(button, bounds));
            for (int i = 0; i < strip.slotCount(); i++) {
                MachineLayout.Rect frame = MachineLayout.frameOf(strip.slot(i));
                assertTrue(inside(frame, bounds), "slot " + i + " sticks out of the strip");
                assertTrue(!frame.overlaps(button), "slot " + i + " covers the redstone button");
                for (int j = 0; j < i; j++) {
                    assertTrue(!frame.overlaps(MachineLayout.frameOf(strip.slot(j))), "slots " + j + " and " + i + " overlap");
                }
            }
        }
        assertEquals(UpgradeStripLayout.X + UpgradeStripLayout.WIDTH, UpgradeStripLayout.right());
    }

    private static boolean inside(MachineLayout.Rect inner, MachineLayout.Rect outer) {
        return inner.x() >= outer.x() && inner.y() >= outer.y()
                && inner.x() + inner.width() <= outer.x() + outer.width()
                && inner.y() + inner.height() <= outer.y() + outer.height();
    }
}
