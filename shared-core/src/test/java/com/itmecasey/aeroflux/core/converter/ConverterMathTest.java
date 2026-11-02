package com.itmecasey.aeroflux.core.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.itmecasey.aeroflux.core.Tier;
import org.junit.jupiter.api.Test;

class ConverterMathTest {

    @Test
    void eachTierBurnsTwiceAsFast() {
        assertEquals(1, ConverterMath.mbPerTick(Tier.COPPER));
        assertEquals(32, ConverterMath.mbPerTick(Tier.FLUXIUM));
        assertEquals(100, ConverterMath.fePerTick(Tier.COPPER));
        assertEquals(3_200, ConverterMath.fePerTick(Tier.FLUXIUM));
    }

    @Test
    void aeIsHalfOfFeAsInAe2() {
        assertEquals(ConverterMath.fePerTick(Tier.GOLD) / 2, ConverterMath.aePerTick(Tier.GOLD));
    }

    @Test
    void theBufferHoldsTenSecondsOfOutput() {
        assertEquals(20_000, ConverterMath.bufferCapacity(Tier.COPPER, ConverterType.ENERGY));
        assertEquals(10_000, ConverterMath.bufferCapacity(Tier.COPPER, ConverterType.AE));
    }

    @Test
    void burningStopsAtTheTierTheTankOrTheBuffer() {
        assertEquals(4, ConverterMath.mbToBurn(Tier.GOLD, 1_000, 1_000_000, ConverterMath.FE_PER_MB));
        assertEquals(2, ConverterMath.mbToBurn(Tier.GOLD, 2, 1_000_000, ConverterMath.FE_PER_MB));
        assertEquals(1, ConverterMath.mbToBurn(Tier.GOLD, 1_000, 150, ConverterMath.FE_PER_MB));
        assertEquals(0, ConverterMath.mbToBurn(Tier.GOLD, 1_000, 50, ConverterMath.FE_PER_MB));
        assertEquals(0, ConverterMath.mbToBurn(Tier.GOLD, 0, 1_000_000, ConverterMath.FE_PER_MB));
    }

    @Test
    void tanksDoubleEachTier() {
        assertEquals(8_000, ConverterMath.tankCapacity(Tier.COPPER));
        assertEquals(256_000, ConverterMath.tankCapacity(Tier.FLUXIUM));
    }
}
