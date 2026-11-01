package com.itmecasey.aeroflux.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FillLevelTest {
    private static final long CAPACITY = 32_000;

    @Test
    void emptyIsStepZeroAndFullIsTheTopStep() {
        assertEquals(0, FillLevel.step(0, CAPACITY));
        assertEquals(FillLevel.STEPS, FillLevel.step(CAPACITY, CAPACITY));
    }

    @Test
    void aFewMillibucketsStillShowASliver() {
        assertEquals(1, FillLevel.step(1, CAPACITY));
    }

    @Test
    void almostFullIsNotYetTheTopStep() {
        assertEquals(FillLevel.STEPS - 1, FillLevel.step(CAPACITY - 1, CAPACITY));
    }

    @Test
    void stepsNeverDecreaseAsTheTankFills() {
        int previous = 0;
        for (long amount = 0; amount <= CAPACITY; amount += 97) {
            int step = FillLevel.step(amount, CAPACITY);
            assertTrue(step >= previous, "step went down at " + amount);
            previous = step;
        }
    }

    @Test
    void zeroCapacityIsHandled() {
        assertEquals(0, FillLevel.step(100, 0));
        assertEquals(0.0, FillLevel.fraction(100, 0), 1e-9);
    }

    @Test
    void theFractionIsClampedToOne() {
        assertEquals(0.5, FillLevel.fraction(16_000, CAPACITY), 1e-9);
        assertEquals(1.0, FillLevel.fraction(40_000, CAPACITY), 1e-9);
    }

    @Test
    void theSyncKeyChangesWithTheStepAndTheFlux() {
        // Each step is 2,000 mB wide here, so 6,100 to 8,000 mB all round up to step 4.
        int aero = FillLevel.syncKey(7_000, CAPACITY, FluxType.AERO);
        assertEquals(aero, FillLevel.syncKey(7_900, CAPACITY, FluxType.AERO), "same step, same key");
        assertNotEquals(aero, FillLevel.syncKey(9_000, CAPACITY, FluxType.AERO), "next step");
        assertNotEquals(aero, FillLevel.syncKey(7_000, CAPACITY, FluxType.MOON), "other flux");
    }
}
