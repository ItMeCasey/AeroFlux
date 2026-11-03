package com.itmecasey.aeroflux.core.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itmecasey.aeroflux.core.Tier;
import org.junit.jupiter.api.Test;

class StressMathTest {

    @Test
    void capacityDoublesEachTier() {
        assertEquals(256, StressMath.capacity(Tier.COPPER));
        assertEquals(512, StressMath.capacity(Tier.IRON));
        assertEquals(8_192, StressMath.capacity(Tier.FLUXIUM));
    }

    @Test
    void speedRisesWithTheTier() {
        int last = 0;
        for (Tier tier : Tier.values()) {
            assertTrue(StressMath.rpm(tier) > last);
            last = StressMath.rpm(tier);
        }
        assertEquals(256, StressMath.rpm(Tier.FLUXIUM));
    }

    @Test
    void burnFollowsTheLoad() {
        assertEquals(0.0, StressMath.burn(Tier.GOLD, 0, 1_024), 1e-9);
        assertEquals(2.0, StressMath.burn(Tier.GOLD, 512, 1_024), 1e-9);
        assertEquals(4.0, StressMath.burn(Tier.GOLD, 1_024, 1_024), 1e-9);
        // An overstressed network burns no more than full load.
        assertEquals(4.0, StressMath.burn(Tier.GOLD, 4_096, 1_024), 1e-9);
        assertEquals(0.0, StressMath.burn(Tier.GOLD, 100, 0), 1e-9);
    }
}
