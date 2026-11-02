package com.itmecasey.aeroflux.core;

/**
 * Measures how fast fluid goes into and out of a tank, for the "in" and "out" rates on the tank and
 * collector screens. Changes are added up over windows of {@value #WINDOW} game ticks, and the
 * rate shown is the last finished window's, so it holds still enough to read and stays exact for
 * the steady flows pipes make.
 *
 * <p>Nothing has to tick it: each call passes the game time, and a window that ended long ago (no
 * fluid moved for a while) simply reads as zero.
 */
public final class RateMeter {
    /** The length of one window, in game ticks: one second. */
    public static final int WINDOW = 20;

    /** The window the running totals belong to, as game time divided by {@link #WINDOW}. */
    private long window = Long.MIN_VALUE;
    private long in;
    private long out;
    private long lastIn;
    private long lastOut;

    /**
     * Counts a change in the amount held: positive went in, negative came out.
     *
     * @param delta    the change, in mB
     * @param gameTime the level's game time, in ticks
     */
    public void record(long delta, long gameTime) {
        roll(gameTime);
        if (delta > 0) {
            in += delta;
        } else {
            out -= delta;
        }
    }

    /** mB that went in during the last finished window. */
    public long lastIn(long gameTime) {
        roll(gameTime);
        return lastIn;
    }

    /** mB that came out during the last finished window. */
    public long lastOut(long gameTime) {
        roll(gameTime);
        return lastOut;
    }

    /** The rate in over the last finished window, in mB per tick. */
    public double inPerTick(long gameTime) {
        return lastIn(gameTime) / (double) WINDOW;
    }

    /** The rate out over the last finished window, in mB per tick. */
    public double outPerTick(long gameTime) {
        return lastOut(gameTime) / (double) WINDOW;
    }

    /** Moves on to the window {@code gameTime} falls in, keeping the one just finished. */
    private void roll(long gameTime) {
        long now = Math.floorDiv(gameTime, WINDOW);
        if (now == window) {
            return;
        }
        // Only the window right before this one counts; anything older means nothing moved since.
        boolean previous = window != Long.MIN_VALUE && now == window + 1;
        lastIn = previous ? in : 0;
        lastOut = previous ? out : 0;
        in = 0;
        out = 0;
        window = now;
    }
}
