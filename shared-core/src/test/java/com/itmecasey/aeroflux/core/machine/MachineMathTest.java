package com.itmecasey.aeroflux.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itmecasey.aeroflux.core.Tier;
import org.junit.jupiter.api.Test;

class MachineMathTest {
    /** The time of a vanilla smelting recipe. */
    private static final int VANILLA_SMELT = 200;

    @Test
    void copperIsTwiceAsFastAsAVanillaFurnace() {
        assertEquals(100, MachineMath.ticksFor(VANILLA_SMELT, Tier.COPPER));
    }

    @Test
    void everyTierIsFasterThanTheOneBelow() {
        int previous = Integer.MAX_VALUE;
        for (Tier tier : Tier.values()) {
            int ticks = MachineMath.ticksFor(VANILLA_SMELT, tier);
            assertTrue(ticks < previous, tier + " should be faster than the tier below");
            previous = ticks;
        }
    }

    @Test
    void ticksRoundUpSoAJobNeverFinishesEarly() {
        // 200 / 3 is 66.67, so an iron machine needs 67 whole ticks.
        assertEquals(67, MachineMath.ticksFor(VANILLA_SMELT, Tier.IRON));
        assertEquals(17, MachineMath.ticksFor(VANILLA_SMELT, Tier.FLUXIUM));
    }

    @Test
    void aJobTakesAtLeastOneTick() {
        assertEquals(1, MachineMath.ticksFor(0, Tier.FLUXIUM));
        assertEquals(1, MachineMath.ticksFor(-5, Tier.COPPER));
        assertEquals(1, MachineMath.ticksFor(1, Tier.COPPER));
    }

    @Test
    void higherTiersBurnMoreFluxPerTickButLessPerItem() {
        int perItemBefore = Integer.MAX_VALUE / 2;
        int perTickBefore = 0;
        for (Tier tier : Tier.values()) {
            assertTrue(MachineMath.fluxPerTick(tier) > perTickBefore);
            int perItem = MachineMath.fluxForJob(VANILLA_SMELT, tier);
            // Rounding a job up to whole ticks can add one tick of Flux (iron: 67 ticks at 3 mB is 201).
            assertTrue(perItem <= perItemBefore + MachineMath.fluxPerTick(tier),
                    tier + " should not cost more per item than the tier below");
            perTickBefore = MachineMath.fluxPerTick(tier);
            perItemBefore = perItem;
        }
        assertEquals(200, MachineMath.fluxForJob(VANILLA_SMELT, Tier.COPPER));
        assertEquals(136, MachineMath.fluxForJob(VANILLA_SMELT, Tier.FLUXIUM));
    }

    @Test
    void inputCapacityGrowsWithTier() {
        assertEquals(4_000, MachineMath.inputCapacity(Tier.COPPER));
        assertEquals(128_000, MachineMath.inputCapacity(Tier.FLUXIUM));
        for (int i = 1; i < Tier.values().length; i++) {
            assertTrue(MachineMath.inputCapacity(Tier.values()[i]) > MachineMath.inputCapacity(Tier.values()[i - 1]));
        }
    }

    @Test
    void aFullTankAlwaysPaysForAWholeJob() {
        for (Tier tier : Tier.values()) {
            assertTrue(MachineMath.inputCapacity(tier) >= MachineMath.fluxForJob(VANILLA_SMELT * 8, tier));
        }
    }

    @Test
    void ticksOfFluxDividesTheTank() {
        assertEquals(50, MachineMath.ticksOfFlux(100, Tier.COPPER));
        assertEquals(0, MachineMath.ticksOfFlux(1, Tier.COPPER));
        assertEquals(0, MachineMath.ticksOfFlux(-10, Tier.COPPER));
    }

    @Test
    void statusIsDecidedInOrderOfImportance() {
        assertEquals(MachineStatus.IDLE, MachineStatus.decide(false, true, true));
        assertEquals(MachineStatus.IDLE, MachineStatus.decide(false, false, false));
        assertEquals(MachineStatus.OUTPUT_FULL, MachineStatus.decide(true, false, true));
        assertEquals(MachineStatus.OUTPUT_FULL, MachineStatus.decide(true, false, false));
        assertEquals(MachineStatus.NO_FLUX, MachineStatus.decide(true, true, false));
        assertEquals(MachineStatus.RUNNING, MachineStatus.decide(true, true, true));
    }

    @Test
    void statusIndexesAreSafe() {
        assertEquals(MachineStatus.NO_FLUX, MachineStatus.byIndex(MachineStatus.NO_FLUX.ordinal()));
        assertEquals(MachineStatus.IDLE, MachineStatus.byIndex(-1));
        assertEquals(MachineStatus.IDLE, MachineStatus.byIndex(99));
    }

    @Test
    void splitIntSurvivesSixteenBitSlotsWithSignExtension() {
        int[] samples = {0, 1, 32_767, 32_768, 65_535, 65_536, 128_000, 1_000_000, Integer.MAX_VALUE};
        for (int value : samples) {
            // The network sends shorts, and the client reads them back sign-extended.
            int low = (short) SplitInt.low(value);
            int high = (short) SplitInt.high(value);
            assertEquals(value, SplitInt.join(low, high), "value " + value);
        }
    }

    @Test
    void splitIntHalvesAreUnsignedSixteenBitNumbers() {
        assertEquals(0xFFFF, SplitInt.low(-1));
        assertEquals(0xFFFF, SplitInt.high(-1));
        assertFalse(SplitInt.low(Integer.MAX_VALUE) < 0);
    }
}
