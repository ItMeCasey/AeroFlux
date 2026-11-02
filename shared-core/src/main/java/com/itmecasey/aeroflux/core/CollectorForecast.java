package com.itmecasey.aeroflux.core;

import java.util.Locale;

/**
 * The numbers on a collector's sky panel that are worked out rather than read from the world:
 * the part of the day, how long the current part lasts, and when the buffer will be full.
 * {@link CollectionMath} decides what is made; this class only describes it.
 */
public final class CollectorForecast {
    /** Ticks per second. */
    private static final int TPS = 20;

    /** The four parts of a day, as the collector sees them. */
    public enum SkyPhase {
        /** Aero Flux is made. */
        DAY,
        /** The gap after day: nothing is made. */
        DUSK,
        /** Moon Flux is made. */
        NIGHT,
        /** The gap before the next day: nothing is made. */
        DAWN
    }

    private CollectorForecast() {
    }

    /** The part of the day at a time of day; any tick count works, it wraps every day. */
    public static SkyPhase phase(long time) {
        long timeOfDay = Math.floorMod(time, CollectionMath.DAY_LENGTH);
        if (timeOfDay < CollectionMath.DAY_END) {
            return SkyPhase.DAY;
        }
        if (timeOfDay < CollectionMath.NIGHT_START) {
            return SkyPhase.DUSK;
        }
        return timeOfDay < CollectionMath.NIGHT_END ? SkyPhase.NIGHT : SkyPhase.DAWN;
    }

    /** Ticks until the current part of the day ends and the next begins. */
    public static long ticksLeftInPhase(long time) {
        long timeOfDay = Math.floorMod(time, CollectionMath.DAY_LENGTH);
        long end = switch (phase(timeOfDay)) {
            case DAY -> CollectionMath.DAY_END;
            case DUSK -> CollectionMath.NIGHT_START;
            case NIGHT -> CollectionMath.NIGHT_END;
            case DAWN -> CollectionMath.DAY_LENGTH;
        };
        return end - timeOfDay;
    }

    /**
     * How long until the buffer is full at a steady net rate.
     *
     * @param amount     what the buffer holds, in mB
     * @param capacity   the buffer's size, in mB
     * @param netPerTick what goes in minus what goes out, in mB per tick
     * @return the ticks, 0 if it is already full, or -1 if it never fills at this rate
     */
    public static long ticksToFull(long amount, long capacity, double netPerTick) {
        long missing = capacity - amount;
        if (missing <= 0) {
            return 0;
        }
        if (netPerTick <= 0.0) {
            return -1;
        }
        return (long) Math.ceil(missing / netPerTick);
    }

    /**
     * A tick count as a short time, the way the screens print it: {@code 45s}, {@code 3m 20s},
     * {@code 2h 05m}. Seconds are rounded up, so a few ticks never read as "0s".
     */
    public static String shortDuration(long ticks) {
        long seconds = (Math.max(0, ticks) + TPS - 1) / TPS;
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m " + (seconds % 60) + "s";
        }
        return (minutes / 60) + "h " + String.format(Locale.ROOT, "%02d", minutes % 60) + "m";
    }
}
