package com.itmecasey.aeroflux.core.chunkloader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itmecasey.aeroflux.core.Tier;
import org.junit.jupiter.api.Test;

class ChunkLoaderMathTest {
    @Test
    void areaGrowsByTwoChunksPerTier() {
        int[] sides = {1, 3, 5, 7, 9, 11};
        for (Tier tier : Tier.values()) {
            assertEquals(sides[tier.ordinal()], ChunkLoaderMath.side(tier));
            assertEquals(sides[tier.ordinal()] * sides[tier.ordinal()], ChunkLoaderMath.chunkCount(tier));
        }
    }

    @Test
    void costPerChunkDropsEveryTier() {
        Tier[] tiers = Tier.values();
        for (int i = 1; i < tiers.length; i++) {
            assertTrue(ChunkLoaderMath.costPerChunkMilli(tiers[i]) < ChunkLoaderMath.costPerChunkMilli(tiers[i - 1]),
                    tiers[i] + " should cost less per chunk than " + tiers[i - 1]);
        }
        assertEquals(2_000, ChunkLoaderMath.costPerTickMilli(Tier.COPPER));
        assertEquals(72_600, ChunkLoaderMath.costPerTickMilli(Tier.FLUXIUM));
    }

    @Test
    void areaIsCenteredOnTheLoadersChunk() {
        assertTrue(ChunkLoaderMath.covers(Tier.COPPER, 4, -2, 4, -2));
        assertFalse(ChunkLoaderMath.covers(Tier.COPPER, 4, -2, 5, -2));
        assertTrue(ChunkLoaderMath.covers(Tier.GOLD, 0, 0, -2, 2));
        assertFalse(ChunkLoaderMath.covers(Tier.GOLD, 0, 0, 3, 0));
    }

    @Test
    void wholeMillibucketsAreBoughtAndTheChangeIsKept() {
        // Fluxium costs 72.6 mB a tick: buying 73 leaves 0.4 over, which the next tick uses.
        long cost = ChunkLoaderMath.costPerTickMilli(Tier.FLUXIUM);
        long credit = 0;
        long bought = 0;
        for (int tick = 0; tick < 10; tick++) {
            int buy = ChunkLoaderMath.mbToBuy(credit, cost);
            bought += buy;
            credit += buy * (long) ChunkLoaderMath.MILLI - cost;
            assertTrue(credit >= 0 && credit < ChunkLoaderMath.MILLI);
        }
        assertEquals(726, bought);
        assertEquals(0, ChunkLoaderMath.mbToBuy(5_000, 2_000));
    }

    @Test
    void runtimeAndClockText() {
        // 16 buckets at 2 mB/t is 8,000 ticks, 6 minutes 40 seconds.
        long ticks = ChunkLoaderMath.runtimeTicks(Tier.COPPER, ChunkLoaderMath.capacity(Tier.COPPER), 0);
        assertEquals(8_000, ticks);
        assertEquals("6:40", ChunkLoaderMath.formatTicks(ticks));
        assertEquals("1:00:05", ChunkLoaderMath.formatTicks(72_100));
        assertEquals("0:00", ChunkLoaderMath.formatTicks(-5));
        assertEquals("72.6", ChunkLoaderMath.formatMilli(72_600));
        assertEquals("2", ChunkLoaderMath.formatMilli(2_000));
    }

    @Test
    void statusFollowsTheRulesInOrder() {
        assertEquals(LoaderStatus.DISABLED, LoaderStatus.decide(false, true, true, true, true, 10));
        assertEquals(LoaderStatus.DIMENSION, LoaderStatus.decide(true, false, true, true, true, 10));
        assertEquals(LoaderStatus.CAP_REACHED, LoaderStatus.decide(true, true, false, false, true, 10));
        // A loader already running never trips its own cap.
        assertEquals(LoaderStatus.ACTIVE, LoaderStatus.decide(true, true, true, false, true, 10));
        assertEquals(LoaderStatus.GRACE, LoaderStatus.decide(true, true, true, true, false, 10));
        assertEquals(LoaderStatus.NO_FLUX, LoaderStatus.decide(true, true, true, true, false, 0));
        assertEquals(LoaderStatus.NO_FLUX, LoaderStatus.decide(true, true, false, true, false, 10));
        assertTrue(LoaderStatus.GRACE.holdsTickets());
        assertFalse(LoaderStatus.CAP_REACHED.holdsTickets());
    }
}
