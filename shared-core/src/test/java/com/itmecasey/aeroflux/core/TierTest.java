package com.itmecasey.aeroflux.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TierTest {

    @Test
    void statsIncreaseWithEveryTier() {
        Tier[] tiers = Tier.values();
        for (int i = 1; i < tiers.length; i++) {
            Tier lower = tiers[i - 1];
            Tier higher = tiers[i];
            assertTrue(higher.collectorRate() > lower.collectorRate(), higher + " rate");
            assertTrue(higher.collectorBuffer() > lower.collectorBuffer(), higher + " buffer");
            assertTrue(higher.tankCapacity() > lower.tankCapacity(), higher + " tank");
        }
    }

    @Test
    void capacitiesAreStoredInMillibuckets() {
        assertEquals(32_000, Tier.COPPER.tankCapacity());
        assertEquals(32_768_000, Tier.FLUXIUM.tankCapacity());
    }

    @Test
    void levelsAndIdsFollowTheEnumOrder() {
        assertEquals(1, Tier.COPPER.level());
        assertEquals(6, Tier.FLUXIUM.level());
        assertEquals("netherite", Tier.NETHERITE.id());
    }
}
