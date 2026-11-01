package com.itmecasey.aeroflux.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CollectionMathTest {
    private static final DimensionRules OVERWORLD = DimensionRules.OVERWORLD;
    private static final double EPSILON = 1e-9;

    private static boolean active(FluxType type, DimensionRules rules, long time) {
        return CollectionMath.isActive(type, rules, time, time);
    }

    @Test
    void aeroFluxIsMadeOnlyDuringTheDay() {
        assertTrue(active(FluxType.AERO, OVERWORLD, 0));
        assertTrue(active(FluxType.AERO, OVERWORLD, 6_000));
        assertTrue(active(FluxType.AERO, OVERWORLD, 11_999));
        assertFalse(active(FluxType.AERO, OVERWORLD, 12_000));
        assertFalse(active(FluxType.AERO, OVERWORLD, 18_000));
    }

    @Test
    void moonFluxIsMadeOnlyAtNight() {
        assertFalse(active(FluxType.MOON, OVERWORLD, 6_000));
        assertTrue(active(FluxType.MOON, OVERWORLD, 13_000));
        assertTrue(active(FluxType.MOON, OVERWORLD, 22_999));
        assertFalse(active(FluxType.MOON, OVERWORLD, 23_000));
    }

    @Test
    void duskAndDawnProduceNothing() {
        for (long time : new long[] {12_000, 12_500, 12_999, 23_000, 23_500, 23_999}) {
            assertFalse(active(FluxType.AERO, OVERWORLD, time), "aero at " + time);
            assertFalse(active(FluxType.MOON, OVERWORLD, time), "moon at " + time);
        }
    }

    @Test
    void timeWrapsAroundAcrossDays() {
        assertTrue(active(FluxType.AERO, OVERWORLD, 24_000L * 100 + 3_000));
        assertTrue(active(FluxType.MOON, OVERWORLD, 24_000L * 100 + 15_000));
    }

    @Test
    void disabledDimensionsProduceNothing() {
        assertFalse(active(FluxType.AERO, DimensionRules.DISABLED, 6_000));
        assertFalse(active(FluxType.MOON, DimensionRules.DISABLED, 15_000));
    }

    @Test
    void overworldTimeSourceIgnoresTheLocalClock() {
        DimensionRules rules = new DimensionRules(true, true, 1.0, 1.0,
                DimensionRules.TimeSource.OVERWORLD_TIME, true, false, false);
        // Local clock says night, Overworld says day: day wins.
        assertTrue(CollectionMath.isActive(FluxType.AERO, rules, 15_000, 6_000));
        assertFalse(CollectionMath.isActive(FluxType.MOON, rules, 15_000, 6_000));
    }

    @Test
    void fixedTimeSourcesAreIndependentOfTheClock() {
        DimensionRules day = withTimeSource(DimensionRules.TimeSource.ALWAYS_DAY);
        DimensionRules night = withTimeSource(DimensionRules.TimeSource.ALWAYS_NIGHT);
        DimensionRules both = withTimeSource(DimensionRules.TimeSource.ALWAYS_BOTH);

        assertTrue(active(FluxType.AERO, day, 15_000));
        assertFalse(active(FluxType.MOON, day, 15_000));
        assertTrue(active(FluxType.MOON, night, 6_000));
        assertFalse(active(FluxType.AERO, night, 6_000));
        assertTrue(active(FluxType.AERO, both, 15_000));
        assertTrue(active(FluxType.MOON, both, 6_000));
    }

    @Test
    void weatherReducesAeroFluxButNotMoonFlux() {
        assertEquals(1.0, CollectionMath.weatherFactor(FluxType.AERO, false, false), EPSILON);
        assertEquals(0.5, CollectionMath.weatherFactor(FluxType.AERO, true, false), EPSILON);
        assertEquals(0.25, CollectionMath.weatherFactor(FluxType.AERO, true, true), EPSILON);
        assertEquals(1.0, CollectionMath.weatherFactor(FluxType.MOON, true, true), EPSILON);
    }

    @Test
    void moonPhaseScalesFromFullToNew() {
        assertEquals(1.0, CollectionMath.moonPhaseFactor(0), EPSILON);
        assertEquals(0.4, CollectionMath.moonPhaseFactor(4), EPSILON);
        // The phases are symmetric around the new moon.
        assertEquals(CollectionMath.moonPhaseFactor(2), CollectionMath.moonPhaseFactor(6), EPSILON);
        assertEquals(CollectionMath.moonPhaseFactor(1), CollectionMath.moonPhaseFactor(7), EPSILON);
        assertEquals(1.0, CollectionMath.moonPhaseFactor(8), EPSILON);
    }

    @Test
    void outputCombinesTierWeatherAndPhase() {
        // Iron collector, clear day: the base rate.
        assertEquals(20.0, CollectionMath.outputPerTick(Tier.IRON, FluxType.AERO, OVERWORLD,
                6_000, 6_000, false, false, 0), EPSILON);
        // Rainy day halves it.
        assertEquals(10.0, CollectionMath.outputPerTick(Tier.IRON, FluxType.AERO, OVERWORLD,
                6_000, 6_000, true, false, 0), EPSILON);
        // New moon at night, even in a storm: 40% of the base rate.
        assertEquals(8.0, CollectionMath.outputPerTick(Tier.IRON, FluxType.MOON, OVERWORLD,
                15_000, 15_000, true, true, 4), EPSILON);
        // Wrong time of day for the flux.
        assertEquals(0.0, CollectionMath.outputPerTick(Tier.IRON, FluxType.MOON, OVERWORLD,
                6_000, 6_000, false, false, 0), EPSILON);
    }

    @Test
    void dimensionMultiplierAndIgnoreWeatherApply() {
        DimensionRules rules = new DimensionRules(true, true, 0.5, 1.0,
                DimensionRules.TimeSource.DIMENSION_TIME, true, true, false);
        // Half multiplier, and rain is ignored in this dimension.
        assertEquals(10.0, CollectionMath.outputPerTick(Tier.IRON, FluxType.AERO, rules,
                6_000, 6_000, true, true, 0), EPSILON);
    }

    @Test
    void aConfiguredBaseRateReplacesTheTierDefault() {
        assertEquals(35.0, CollectionMath.outputPerTick(35, FluxType.AERO, OVERWORLD,
                6_000, 6_000, false, false, 0), EPSILON);
    }

    private static DimensionRules withTimeSource(DimensionRules.TimeSource source) {
        return new DimensionRules(true, true, 1.0, 1.0, source, true, false, false);
    }
}
