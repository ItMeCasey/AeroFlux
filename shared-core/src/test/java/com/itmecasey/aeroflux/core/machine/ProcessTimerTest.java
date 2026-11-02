package com.itmecasey.aeroflux.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProcessTimerTest {
    @Test
    void finishesOnTheTickThatReachesTheTotal() {
        ProcessTimer timer = new ProcessTimer();
        timer.setTotal(3);
        assertFalse(timer.advance());
        assertFalse(timer.advance());
        assertTrue(timer.advance());
        assertEquals(3, timer.progress());
        assertEquals(0, timer.remaining());
    }

    @Test
    void doesNothingWithoutAJob() {
        ProcessTimer timer = new ProcessTimer();
        assertFalse(timer.advance());
        assertEquals(0, timer.progress());
        assertEquals(0.0, timer.fraction());
    }

    @Test
    void aPausedMachineKeepsItsProgress() {
        ProcessTimer timer = new ProcessTimer();
        timer.setTotal(10);
        timer.advance();
        timer.advance();
        // The machine simply does not call advance while it has no Flux.
        assertEquals(2, timer.progress());
        assertEquals(0.2, timer.fraction(), 1e-9);
        assertEquals(8, timer.remaining());
    }

    @Test
    void shorteningTheJobClampsProgress() {
        ProcessTimer timer = new ProcessTimer();
        timer.setTotal(10);
        for (int i = 0; i < 8; i++) {
            timer.advance();
        }
        timer.setTotal(5);
        assertEquals(5, timer.progress());
        assertEquals(1.0, timer.fraction());
    }

    @Test
    void resetForgetsTheJob() {
        ProcessTimer timer = new ProcessTimer();
        timer.setTotal(4);
        timer.advance();
        timer.reset();
        assertEquals(0, timer.progress());
        assertEquals(0, timer.total());
    }

    @Test
    void restoreClampsDamagedValues() {
        ProcessTimer timer = new ProcessTimer();
        timer.restore(500, 100);
        assertEquals(100, timer.progress());
        timer.restore(-4, 100);
        assertEquals(0, timer.progress());
        timer.restore(5, -1);
        assertEquals(0, timer.total());
        assertEquals(0, timer.progress());
    }

    @Test
    void staticFractionHandlesOddInput() {
        assertEquals(0.5, ProcessTimer.fraction(50, 100), 1e-9);
        assertEquals(0.0, ProcessTimer.fraction(5, 0));
        assertEquals(1.0, ProcessTimer.fraction(200, 100));
        assertEquals(0.0, ProcessTimer.fraction(-1, 100));
    }
}
