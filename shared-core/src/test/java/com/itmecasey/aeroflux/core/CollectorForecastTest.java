package com.itmecasey.aeroflux.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.itmecasey.aeroflux.core.CollectorForecast.SkyPhase;
import org.junit.jupiter.api.Test;

class CollectorForecastTest {
    @Test
    void thePhasesFollowTheCollectionWindows() {
        assertSame(SkyPhase.DAY, CollectorForecast.phase(0));
        assertSame(SkyPhase.DAY, CollectorForecast.phase(11_999));
        assertSame(SkyPhase.DUSK, CollectorForecast.phase(12_000));
        assertSame(SkyPhase.NIGHT, CollectorForecast.phase(13_000));
        assertSame(SkyPhase.NIGHT, CollectorForecast.phase(22_999));
        assertSame(SkyPhase.DAWN, CollectorForecast.phase(23_000));
        // Later days and negative clocks wrap around.
        assertSame(SkyPhase.NIGHT, CollectorForecast.phase(5 * 24_000L + 18_000));
        assertSame(SkyPhase.DAWN, CollectorForecast.phase(-500));
    }

    @Test
    void thePhasesAgreeWithWhatIsMade() {
        for (long time = 0; time < 24_000; time += 250) {
            boolean aero = CollectionMath.isActive(FluxType.AERO, DimensionRules.OVERWORLD, time, time);
            boolean moon = CollectionMath.isActive(FluxType.MOON, DimensionRules.OVERWORLD, time, time);
            SkyPhase phase = CollectorForecast.phase(time);
            assertEquals(aero, phase == SkyPhase.DAY, "at " + time);
            assertEquals(moon, phase == SkyPhase.NIGHT, "at " + time);
        }
    }

    @Test
    void ticksLeftCountToTheNextPhase() {
        assertEquals(12_000, CollectorForecast.ticksLeftInPhase(0));
        assertEquals(1, CollectorForecast.ticksLeftInPhase(11_999));
        assertEquals(1_000, CollectorForecast.ticksLeftInPhase(12_000));
        assertEquals(5_000, CollectorForecast.ticksLeftInPhase(18_000));
        assertEquals(1_000, CollectorForecast.ticksLeftInPhase(23_000));
    }

    @Test
    void fullInIsTheMissingAmountOverTheNetRate() {
        assertEquals(250, CollectorForecast.ticksToFull(0, 1_000, 4.0));
        // A fractional rate rounds up: the last few mB still need a whole tick.
        assertEquals(417, CollectorForecast.ticksToFull(0, 1_000, 2.4));
        assertEquals(0, CollectorForecast.ticksToFull(1_000, 1_000, 4.0));
        assertEquals(0, CollectorForecast.ticksToFull(1_200, 1_000, 0.0));
        assertEquals(-1, CollectorForecast.ticksToFull(10, 1_000, 0.0));
        assertEquals(-1, CollectorForecast.ticksToFull(10, 1_000, -2.0));
    }

    @Test
    void durationsReadShort() {
        assertEquals("0s", CollectorForecast.shortDuration(0));
        assertEquals("1s", CollectorForecast.shortDuration(3));
        assertEquals("45s", CollectorForecast.shortDuration(900));
        assertEquals("3m 20s", CollectorForecast.shortDuration(4_000));
        assertEquals("2h 05m", CollectorForecast.shortDuration(20L * 60 * 125));
    }
}
