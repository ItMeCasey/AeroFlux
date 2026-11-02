package com.itmecasey.aeroflux.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itmecasey.aeroflux.core.Tier;
import org.junit.jupiter.api.Test;

class MachineReachTest {
    @Test
    void reachGrowsWithTier() {
        assertEquals(4, MachineReach.lampRadius(Tier.COPPER));
        assertEquals(16, MachineReach.lampRadius(Tier.FLUXIUM));
        assertEquals(3, MachineReach.breederRadius(Tier.COPPER));
        assertEquals(9, MachineReach.breederRadius(Tier.FLUXIUM));
        for (int i = 1; i < Tier.values().length; i++) {
            Tier tier = Tier.values()[i];
            Tier below = Tier.values()[i - 1];
            assertTrue(MachineReach.magnetRadius(tier) > MachineReach.magnetRadius(below));
            assertTrue(MachineReach.skyCooldown(tier) < MachineReach.skyCooldown(below));
        }
    }

    @Test
    void skyCooldownHalvesEachTier() {
        assertEquals(2400, MachineReach.skyCooldown(Tier.COPPER));
        assertEquals(75, MachineReach.skyCooldown(Tier.FLUXIUM));
    }
}
