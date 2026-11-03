package com.itmecasey.aeroflux.core.profile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a finished sampling window found: server time per kind of block, per group of kinds and in
 * total, and the heaviest single blocks.
 *
 * @param windowTicks how many server ticks the window lasted; time per tick divides by this
 * @param total       everything added up (its {@code type} is {@value #TOTAL})
 * @param groups      one line per group, such as all machines together, heaviest first
 * @param types       one line per kind of block, heaviest first
 * @param heaviest    the single blocks that took the most time, heaviest first
 */
public record ProfileReport(int windowTicks, Line total, List<Line> groups, List<Line> types, List<SiteLine> heaviest) {
    /** The name of the total line. */
    public static final String TOTAL = "total";

    /**
     * Time spent by one kind or group of blocks.
     *
     * @param type       the name
     * @param blocks     how many different blocks of it ticked in the window (the ones loaded)
     * @param calls      how many ticks that was in all
     * @param nanos      the time all of them took
     * @param worstNanos the longest single tick
     */
    public record Line(String type, int blocks, long calls, long nanos, long worstNanos) {
        /** Average server time per tick over a window, in milliseconds. */
        public double msPerTick(int windowTicks) {
            return windowTicks <= 0 ? 0.0 : nanos / 1_000_000.0 / windowTicks;
        }

        /** The share of {@code totalNanos} this line took, from 0 to 1; 0 when nothing was recorded. */
        public double shareOf(long totalNanos) {
            return totalNanos <= 0 ? 0.0 : (double) nanos / totalNanos;
        }
    }

    /** One block and the time it took. */
    public record SiteLine(String type, String dimension, int x, int y, int z, long calls, long nanos) {
        /** Average server time per tick over a window, in milliseconds. */
        public double msPerTick(int windowTicks) {
            return windowTicks <= 0 ? 0.0 : nanos / 1_000_000.0 / windowTicks;
        }
    }

    /**
     * Builds the report of a window.
     *
     * @param windowTicks how long the window ran, in server ticks (at least 1)
     * @param topBlocks   how many of the heaviest single blocks to list
     */
    public static ProfileReport of(TickSampler sampler, int windowTicks, int topBlocks) {
        int ticks = Math.max(1, windowTicks);
        Map<String, Accumulator> byType = new HashMap<>();
        Map<String, Accumulator> byGroup = new HashMap<>();
        Accumulator all = new Accumulator();
        List<SiteLine> sites = new ArrayList<>();
        for (Map.Entry<TickSampler.Site, TickSampler.Totals> entry : sampler.sites().entrySet()) {
            TickSampler.Site site = entry.getKey();
            TickSampler.Totals totals = entry.getValue();
            byType.computeIfAbsent(site.type(), key -> new Accumulator()).add(totals);
            byGroup.computeIfAbsent(ProfileTypes.groupOf(site.type()), key -> new Accumulator()).add(totals);
            all.add(totals);
            sites.add(new SiteLine(site.type(), site.dimension(), site.x(), site.y(), site.z(), totals.calls, totals.nanos));
        }
        // Ties break on name and position, so two runs over the same samples list the same order.
        Comparator<SiteLine> heaviestFirst = Comparator.comparingLong(SiteLine::nanos).reversed()
                .thenComparing(SiteLine::type).thenComparing(SiteLine::dimension)
                .thenComparingInt(SiteLine::x).thenComparingInt(SiteLine::y).thenComparingInt(SiteLine::z);
        sites.sort(heaviestFirst);
        List<SiteLine> top = new ArrayList<>(sites.subList(0, Math.min(Math.max(0, topBlocks), sites.size())));
        return new ProfileReport(ticks, all.line(TOTAL), lines(byGroup), lines(byType), top);
    }

    private static List<Line> lines(Map<String, Accumulator> byName) {
        List<Line> lines = new ArrayList<>();
        for (Map.Entry<String, Accumulator> entry : byName.entrySet()) {
            lines.add(entry.getValue().line(entry.getKey()));
        }
        lines.sort(Comparator.comparingLong(Line::nanos).reversed().thenComparing(Line::type));
        return lines;
    }

    /** Blocks loaded in the window that count toward the budget as machines and the like (everything but bundles). */
    public int loadedMachineLike() {
        int count = 0;
        for (Line line : types) {
            if (!ProfileTypes.isBundle(line.type())) {
                count += line.blocks();
            }
        }
        return count;
    }

    /** Conduit bundles loaded in the window. */
    public int loadedBundles() {
        for (Line line : types) {
            if (ProfileTypes.isBundle(line.type())) {
                return line.blocks();
            }
        }
        return 0;
    }

    /** Adds up the totals of several blocks. */
    private static final class Accumulator {
        private int blocks;
        private long calls;
        private long nanos;
        private long worst;

        void add(TickSampler.Totals totals) {
            blocks++;
            calls += totals.calls;
            nanos += totals.nanos;
            worst = Math.max(worst, totals.worstNanos);
        }

        Line line(String name) {
            return new Line(name, blocks, calls, nanos, worst);
        }
    }
}
