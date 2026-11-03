package com.itmecasey.aeroflux.core.profile;

import java.util.Locale;

/**
 * Number formatting for the profiler report, kept apart from the game so the rounding is tested.
 * Always uses a point as the decimal mark, so a report reads the same whatever the server's locale.
 */
public final class ProfileFormat {
    private ProfileFormat() {
    }

    /** Milliseconds with three decimals: {@code 0.125}. */
    public static String ms(double milliseconds) {
        return String.format(Locale.ROOT, "%.3f", milliseconds);
    }

    /** A time in nanoseconds shown in microseconds with one decimal: {@code 12.5}. */
    public static String micros(long nanos) {
        return String.format(Locale.ROOT, "%.1f", nanos / 1_000.0);
    }

    /** A share from 0 to 1 as a whole percent: {@code 42}. */
    public static String percent(double fraction) {
        return String.format(Locale.ROOT, "%d", Math.round(fraction * 100.0));
    }

    /** A window of ticks as seconds with one decimal: {@code 5.0}. */
    public static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0);
    }
}
