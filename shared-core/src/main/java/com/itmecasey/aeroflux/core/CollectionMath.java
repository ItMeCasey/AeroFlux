package com.itmecasey.aeroflux.core;

/**
 * The rules that turn the sky into fluid: which flux a collector makes at a given time of
 * day, and how weather and the moon's phase scale the amount.
 *
 * <p>Everything here is a pure function of its arguments, so it can be unit tested without
 * starting the game. The loader modules read the world state and pass it in.
 */
public final class CollectionMath {
    /** Ticks in one Minecraft day. */
    public static final long DAY_LENGTH = 24_000L;

    /** First tick of the daytime window (inclusive). Aero Flux is made from here. */
    static final long DAY_START = 0L;
    /** First tick after the daytime window. A short gap follows before night begins. */
    static final long DAY_END = 12_000L;
    /** First tick of the nighttime window (inclusive). Moon Flux is made from here. */
    static final long NIGHT_START = 13_000L;
    /** First tick after the nighttime window. A short gap follows before the next dawn. */
    static final long NIGHT_END = 23_000L;

    private static final double RAIN_FACTOR = 0.5;
    private static final double THUNDER_FACTOR = 0.25;
    private static final double NEW_MOON_FACTOR = 0.4;

    private CollectionMath() {
    }

    /**
     * Tells whether the given flux is being made at this time.
     *
     * <p>Dusk and dawn are gaps where neither flux is made, which is what makes storing a
     * full day's worth of both worth the trouble.
     *
     * @param type          the fluid being asked about
     * @param rules         the rules of the dimension the collector is in
     * @param dimensionTime the dimension's own time of day, in ticks
     * @param overworldTime the Overworld's time of day, in ticks
     */
    public static boolean isActive(FluxType type, DimensionRules rules, long dimensionTime, long overworldTime) {
        if (!isEnabled(type, rules)) {
            return false;
        }
        return switch (rules.timeSource()) {
            case ALWAYS_BOTH -> true;
            case ALWAYS_DAY -> type == FluxType.AERO;
            case ALWAYS_NIGHT -> type == FluxType.MOON;
            case DIMENSION_TIME -> matchesClock(type, dimensionTime);
            case OVERWORLD_TIME -> matchesClock(type, overworldTime);
        };
    }

    /**
     * Fluid produced per tick, in mB, as a fractional value. Machines keep the remainder so
     * that low rates still produce whole millibuckets over time.
     *
     * @param tier          the collector's tier
     * @param type          the fluid being produced
     * @param rules         the rules of the dimension the collector is in
     * @param dimensionTime the dimension's own time of day, in ticks
     * @param overworldTime the Overworld's time of day, in ticks
     * @param raining       whether it is raining at the collector
     * @param thundering    whether there is a thunderstorm at the collector
     * @param moonPhase     the moon phase, 0 (full) to 7, as Minecraft numbers it
     * @return the output per tick, or 0 if the flux is not made right now
     */
    public static double outputPerTick(Tier tier, FluxType type, DimensionRules rules,
                                       long dimensionTime, long overworldTime,
                                       boolean raining, boolean thundering, int moonPhase) {
        return outputPerTick(tier.collectorRate(), type, rules, dimensionTime, overworldTime,
                raining, thundering, moonPhase);
    }

    /**
     * Same as the tier-based overload, but for a base rate that may have been changed in the
     * config instead of using the built-in value.
     *
     * @param baseRate the output in full, clear light, in mB/t
     */
    public static double outputPerTick(int baseRate, FluxType type, DimensionRules rules,
                                       long dimensionTime, long overworldTime,
                                       boolean raining, boolean thundering, int moonPhase) {
        if (!isActive(type, rules, dimensionTime, overworldTime)) {
            return 0.0;
        }
        double multiplier = type == FluxType.AERO ? rules.aeroMultiplier() : rules.moonMultiplier();
        double output = baseRate * multiplier;
        if (!rules.ignoreWeather()) {
            output *= weatherFactor(type, raining, thundering);
        }
        if (type == FluxType.MOON) {
            output *= moonPhaseFactor(moonPhase);
        }
        return output;
    }

    /**
     * How much weather reduces output. Rain halves Aero Flux and thunder cuts it to a
     * quarter; moonlight is unaffected, since the moon is still "up" behind the clouds.
     */
    public static double weatherFactor(FluxType type, boolean raining, boolean thundering) {
        if (type == FluxType.MOON) {
            return 1.0;
        }
        if (thundering) {
            return THUNDER_FACTOR;
        }
        return raining ? RAIN_FACTOR : 1.0;
    }

    /**
     * How the moon's phase scales Moon Flux: 1.0 at a full moon, falling in even steps to
     * {@value #NEW_MOON_FACTOR} at a new moon.
     *
     * @param moonPhase the phase, 0 (full) to 7; values outside the range wrap around
     */
    public static double moonPhaseFactor(int moonPhase) {
        int phase = Math.floorMod(moonPhase, 8);
        int distanceFromFull = Math.min(phase, 8 - phase);
        return NEW_MOON_FACTOR + (1.0 - NEW_MOON_FACTOR) * (1.0 - distanceFromFull / 4.0);
    }

    private static boolean isEnabled(FluxType type, DimensionRules rules) {
        return type == FluxType.AERO ? rules.aeroEnabled() : rules.moonEnabled();
    }

    /** Checks the flux against the day or night window for a time of day. */
    private static boolean matchesClock(FluxType type, long time) {
        long timeOfDay = Math.floorMod(time, DAY_LENGTH);
        return switch (type) {
            case AERO -> timeOfDay >= DAY_START && timeOfDay < DAY_END;
            case MOON -> timeOfDay >= NIGHT_START && timeOfDay < NIGHT_END;
        };
    }
}
