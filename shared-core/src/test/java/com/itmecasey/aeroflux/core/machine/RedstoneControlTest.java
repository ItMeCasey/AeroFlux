package com.itmecasey.aeroflux.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RedstoneControlTest {
    @Test
    void alwaysIgnoresTheSignal() {
        RedstoneControl control = new RedstoneControl();
        assertTrue(control.mayRun());
        control.signal(true);
        assertTrue(control.mayRun());
    }

    @Test
    void highAndLowFollowTheSignal() {
        RedstoneControl control = new RedstoneControl();
        control.setMode(RedstoneMode.HIGH);
        assertFalse(control.mayRun());
        control.signal(true);
        assertTrue(control.mayRun());

        control.setMode(RedstoneMode.LOW);
        assertFalse(control.mayRun());
        control.signal(false);
        assertTrue(control.mayRun());
    }

    @Test
    void aPulseAllowsOneJob() {
        RedstoneControl control = new RedstoneControl();
        control.setMode(RedstoneMode.PULSE);
        assertFalse(control.mayRun());
        control.signal(true);
        control.signal(false);
        assertTrue(control.mayRun(), "the pulse stays armed after the signal drops");
        control.jobFinished();
        assertFalse(control.mayRun());
    }

    @Test
    void aSteadySignalIsOnePulse() {
        RedstoneControl control = new RedstoneControl();
        control.setMode(RedstoneMode.PULSE);
        control.signal(true);
        control.jobFinished();
        assertFalse(control.signal(true), "the same signal again is no change");
        assertFalse(control.mayRun());
    }

    @Test
    void switchingModesDropsAWaitingPulse() {
        RedstoneControl control = new RedstoneControl();
        control.setMode(RedstoneMode.PULSE);
        control.signal(true);
        assertTrue(control.pulseArmed());
        control.setMode(RedstoneMode.HIGH);
        control.setMode(RedstoneMode.PULSE);
        assertFalse(control.mayRun());
    }

    @Test
    void aSignalBeforePulseModeIsNoPulse() {
        RedstoneControl control = new RedstoneControl();
        control.signal(true);
        control.setMode(RedstoneMode.PULSE);
        assertFalse(control.mayRun());
    }

    @Test
    void restoreKeepsAPulseOnlyInPulseMode() {
        RedstoneControl control = new RedstoneControl();
        control.restore(RedstoneMode.PULSE, true, true);
        assertTrue(control.mayRun() && control.powered());
        control.restore(RedstoneMode.LOW, false, true);
        assertFalse(control.pulseArmed());
        control.restore(null, false, false);
        assertEquals(RedstoneMode.ALWAYS, control.mode());
    }

    @Test
    void modesCycleAndEncodeSafely() {
        assertEquals(RedstoneMode.HIGH, RedstoneMode.ALWAYS.cycle(false));
        assertEquals(RedstoneMode.ALWAYS, RedstoneMode.PULSE.cycle(false));
        assertEquals(RedstoneMode.PULSE, RedstoneMode.ALWAYS.cycle(true));
        for (RedstoneMode mode : RedstoneMode.values()) {
            assertEquals(mode, RedstoneMode.fromButton(mode.buttonId()));
            assertEquals(mode, RedstoneMode.byId(mode.id()));
            assertEquals(mode, RedstoneMode.byIndex(mode.ordinal()));
            // The buttons must never be mistaken for a side-config command.
            assertNull(SideCommand.decode(mode.buttonId()));
        }
        assertNull(RedstoneMode.fromButton(RedstoneMode.BUTTON_BASE - 1));
        assertNull(RedstoneMode.fromButton(RedstoneMode.BUTTON_BASE + RedstoneMode.values().length));
        assertEquals(RedstoneMode.ALWAYS, RedstoneMode.byIndex(-1));
        assertEquals(RedstoneMode.ALWAYS, RedstoneMode.byId("sideways"));
    }
}
