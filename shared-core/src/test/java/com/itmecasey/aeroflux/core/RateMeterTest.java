package com.itmecasey.aeroflux.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RateMeterTest {
    private static final double EPSILON = 1e-9;

    @Test
    void aNewMeterReadsZero() {
        RateMeter meter = new RateMeter();
        assertEquals(0, meter.lastIn(1_000));
        assertEquals(0, meter.lastOut(1_000));
    }

    @Test
    void theRateIsTheLastFinishedWindow() {
        RateMeter meter = new RateMeter();
        // 25 mB a tick in for a whole window, 10 mB a tick out.
        for (long tick = 100; tick < 120; tick++) {
            meter.record(25, tick);
            meter.record(-10, tick);
        }
        // Still inside that window: the window before it was empty.
        assertEquals(0, meter.lastIn(119));
        assertEquals(500, meter.lastIn(120));
        assertEquals(200, meter.lastOut(139));
        assertEquals(25.0, meter.inPerTick(130), EPSILON);
        assertEquals(10.0, meter.outPerTick(130), EPSILON);
    }

    @Test
    void anOldWindowReadsAsZero() {
        RateMeter meter = new RateMeter();
        meter.record(500, 100);
        assertEquals(500, meter.lastIn(125));
        // Two windows later nothing has moved for a whole window.
        assertEquals(0, meter.lastIn(140));
    }

    @Test
    void aSkippedWindowIsNotCarriedOver() {
        RateMeter meter = new RateMeter();
        meter.record(500, 100);
        // The next change comes three windows later; the old one is long gone.
        meter.record(40, 165);
        assertEquals(0, meter.lastIn(170));
        assertEquals(40, meter.lastIn(180));
    }

    @Test
    void negativeGameTimesWork() {
        RateMeter meter = new RateMeter();
        meter.record(-60, -5);
        assertEquals(60, meter.lastOut(0));
    }
}
