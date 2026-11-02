package com.itmecasey.aeroflux.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MachineSoundSetTest {
    @Test
    void everyMachinePlaysAKnownLoopOrNone() {
        for (MachineType machine : MachineType.values()) {
            String loop = MachineSoundSet.loopOf(machine);
            assertTrue(loop == null || MachineSoundSet.LOOPS.contains(loop), machine + " -> " + loop);
        }
    }

    @Test
    void theFirstMachinesPlayTheirOwnLoops() {
        assertEquals("furnace", MachineSoundSet.loopOf(MachineType.FURNACE));
        assertEquals("smelter", MachineSoundSet.loopOf(MachineType.ALLOY_SMELTER));
        assertEquals("grower", MachineSoundSet.loopOf(MachineType.PLANT_GROWER));
        assertNull(MachineSoundSet.loopOf(MachineType.MAGNET));
    }

    @Test
    void everyLoopIsUsed() {
        Set<String> used = new HashSet<>();
        for (MachineType machine : MachineType.values()) {
            used.add(MachineSoundSet.loopOf(machine));
        }
        assertTrue(used.containsAll(MachineSoundSet.LOOPS));
    }

    @Test
    void loopsAreToldFromTheShortSounds() {
        assertTrue(MachineSoundSet.isLoop("machine.grinder.loop"));
        assertTrue(!MachineSoundSet.isLoop("machine.start"));
        assertTrue(!MachineSoundSet.isLoop("machine.done"));
    }
}
