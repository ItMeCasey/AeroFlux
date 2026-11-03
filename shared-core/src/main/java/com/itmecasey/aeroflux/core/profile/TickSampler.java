package com.itmecasey.aeroflux.core.profile;

import java.util.HashMap;
import java.util.Map;

/**
 * Collects the time AeroFlux block entities spend in their ticks during one sampling window.
 *
 * <p>The profiler creates one when a window starts and throws it away when the window ends, so
 * nothing is held (and nothing is recorded) while nobody is sampling. Every sample is one tick of one
 * block: its kind (see {@link ProfileTypes}), where it stands, and how long the tick took. This class
 * only adds them up; {@link ProfileReport#of} turns the totals into something to read.
 *
 * <p>Not thread safe. Block entities tick on the server thread, and so does the profiler.
 */
public final class TickSampler {
    /** Where a block stands: enough to tell two blocks of one kind apart. */
    record Site(String type, String dimension, int x, int y, int z) {
    }

    /** Running totals of one block, or of one kind of block. */
    static final class Totals {
        long calls;
        long nanos;
        long worstNanos;

        void add(long elapsed) {
            calls++;
            nanos += elapsed;
            worstNanos = Math.max(worstNanos, elapsed);
        }
    }

    private final Map<Site, Totals> sites = new HashMap<>();
    private long samples;

    /**
     * Adds one tick of one block.
     *
     * @param type      the kind of block, such as {@code "collector"}
     * @param dimension the dimension it is in, as text
     * @param nanos     how long the tick took; a negative value (a clock that went backwards) counts as zero
     */
    public void record(String type, String dimension, int x, int y, int z, long nanos) {
        sites.computeIfAbsent(new Site(type, dimension, x, y, z), site -> new Totals()).add(Math.max(0L, nanos));
        samples++;
    }

    /** How many block ticks have been recorded. */
    public long samples() {
        return samples;
    }

    /** How many different blocks have been seen. */
    public int blocks() {
        return sites.size();
    }

    Map<Site, Totals> sites() {
        return sites;
    }
}
