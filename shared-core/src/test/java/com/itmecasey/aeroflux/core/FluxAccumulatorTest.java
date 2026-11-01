package com.itmecasey.aeroflux.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FluxAccumulatorTest {
    private static final double EPSILON = 1e-9;

    @Test
    void wholeRatesPassStraightThrough() {
        FluxAccumulator accumulator = new FluxAccumulator();
        assertEquals(20, accumulator.add(20.0));
        assertEquals(0.0, accumulator.carry(), EPSILON);
    }

    @Test
    void fractionsAreCarriedUntilTheyAddUp() {
        FluxAccumulator accumulator = new FluxAccumulator();
        assertEquals(0, accumulator.add(0.4));
        assertEquals(0, accumulator.add(0.4));
        assertEquals(1, accumulator.add(0.4));
        assertEquals(0.2, accumulator.carry(), EPSILON);
    }

    @Test
    void longRunTotalMatchesTheRate() {
        FluxAccumulator accumulator = new FluxAccumulator();
        long total = 0;
        for (int tick = 0; tick < 1_000; tick++) {
            total += accumulator.add(2.4);
        }
        // 1,000 ticks at 2.4 mB/t is 2,400 mB, give or take the carried fraction.
        assertTrue(Math.abs(total - 2_400) <= 1, "total was " + total);
    }

    @Test
    void resetDropsTheFraction() {
        FluxAccumulator accumulator = new FluxAccumulator();
        accumulator.add(0.75);
        accumulator.reset();
        assertEquals(0.0, accumulator.carry(), EPSILON);
    }

    @Test
    void damagedSavedValuesAreClamped() {
        FluxAccumulator accumulator = new FluxAccumulator();
        accumulator.setCarry(5.0);
        assertTrue(accumulator.carry() < 1.0);
        accumulator.setCarry(-3.0);
        assertEquals(0.0, accumulator.carry(), EPSILON);
    }
}
