package com.itmecasey.aeroflux.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FluxCostTest {
    private static final double EPSILON = 1e-9;

    @Test
    void aJobCostsItsTicksTimesTheRunningCostPlusTheExtra() {
        assertEquals(200, FluxCost.perOperation(1, 200, 0));
        assertEquals(20 * 100 + 500, FluxCost.perOperation(20, 100, 500));
    }

    @Test
    void noJobCostsNothing() {
        assertEquals(0, FluxCost.perOperation(20, 0, 500));
        assertEquals(0, FluxCost.nextTick(20, 0, 0, 500));
        assertEquals(0, FluxCost.coverFraction(1_000, 0), EPSILON);
        assertEquals(0, FluxCost.operationsCovered(1_000, 0));
    }

    @Test
    void aHugeJobDoesNotOverflow() {
        assertEquals(Integer.MAX_VALUE, FluxCost.perOperation(1_000_000, 1_000_000, 0));
    }

    @Test
    void onlyTheLastTickPaysTheExtra() {
        assertEquals(20, FluxCost.nextTick(20, 0, 100, 500));
        assertEquals(20, FluxCost.nextTick(20, 97, 100, 500));
        assertEquals(520, FluxCost.nextTick(20, 99, 100, 500));
        // A one-tick job pays everything on its first and only tick.
        assertEquals(520, FluxCost.nextTick(20, 0, 1, 500));
    }

    @Test
    void theTankCoversATickOnlyWithTheWholeCost() {
        assertTrue(FluxCost.coversNextTick(20, 20));
        assertFalse(FluxCost.coversNextTick(19, 20));
        assertTrue(FluxCost.coversNextTick(0, 0));
    }

    @Test
    void theBarFillsUpToOneWholeJob() {
        assertEquals(0.5, FluxCost.coverFraction(100, 200), EPSILON);
        assertEquals(1.0, FluxCost.coverFraction(1_000, 200), EPSILON);
        assertEquals(0.0, FluxCost.coverFraction(-5, 200), EPSILON);
        assertEquals(5, FluxCost.operationsCovered(1_000, 200));
        assertEquals(0, FluxCost.operationsCovered(199, 200));
    }
}
