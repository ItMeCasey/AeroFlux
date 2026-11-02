package com.itmecasey.aeroflux.core.conduit;

import java.util.Arrays;
import java.util.Random;
import java.util.TreeSet;

/**
 * The Priority upgrade's rule: destinations with a higher priority fill first, and destinations
 * that share a priority are served the way the pipe's distribution mode says, exactly as if the
 * others were not there.
 *
 * <p>A face without a Priority upgrade counts as priority {@value #DEFAULT}. When every
 * destination has the same priority the answers here are the same as {@link FlowPlanner}'s and
 * {@link DistributionMode#order}'s, so networks without the upgrade behave as they always did.
 */
public final class PriorityPlanner {
    /** The lowest priority a face can be set to. */
    public static final int MIN = -5;
    /** The highest priority a face can be set to. */
    public static final int MAX = 5;
    /** The priority of a face with no Priority upgrade, and of a freshly installed one. */
    public static final int DEFAULT = 0;

    private PriorityPlanner() {
    }

    /** A priority pulled into the {@value #MIN}..{@value #MAX} range. */
    public static int clamp(int priority) {
        return Math.max(MIN, Math.min(MAX, priority));
    }

    /**
     * Reorders destinations so higher priorities come first. The sort is stable, so destinations
     * that share a priority keep the order they had, which is the distribution mode's order.
     *
     * @param order      destination indexes, usually from {@link DistributionMode#order}
     * @param priorities the priority of each destination, indexed by destination
     * @return a new array; {@code order} is left as it was
     */
    public static int[] byPriority(int[] order, int[] priorities) {
        Integer[] boxed = new Integer[order.length];
        for (int i = 0; i < order.length; i++) {
            boxed[i] = order[i];
        }
        // Arrays.sort on objects is a stable merge sort.
        Arrays.sort(boxed, (a, b) -> Integer.compare(priorities[b], priorities[a]));
        int[] sorted = new int[order.length];
        for (int i = 0; i < order.length; i++) {
            sorted[i] = boxed[i];
        }
        return sorted;
    }

    /**
     * Plans one step like {@link FlowPlanner#planBySource}, but fills the sinks one priority level
     * at a time, highest first. Each level gets whatever the sources still hold and the step still
     * allows once the levels above it have taken their fill.
     *
     * @param available  how much each source can give; negative values count as 0
     * @param accepting  how much each sink can take; negative values count as 0
     * @param priorities the priority of each sink, same order as {@code accepting}
     * @param limit      the most that may move in this step
     * @param modes      the distribution mode of each source, same order as {@code available}
     * @param rotation   a counter that grows every step
     * @param random     randomness for {@link DistributionMode#RANDOM}
     * @return {@code moves[source][sink]}, the amount going from each source to each sink
     */
    public static long[][] planBySource(long[] available, long[] accepting, int[] priorities, long limit,
                                        DistributionMode[] modes, int rotation, Random random) {
        TreeSet<Integer> levels = new TreeSet<>();
        for (int priority : priorities) {
            levels.add(priority);
        }
        if (levels.size() <= 1) {
            return FlowPlanner.planBySource(available, accepting, limit, modes, rotation, random);
        }

        int sources = available.length;
        int sinks = accepting.length;
        long[][] moves = new long[sources][sinks];
        long[] left = new long[sources];
        for (int i = 0; i < sources; i++) {
            left[i] = Math.max(0, available[i]);
        }
        long budget = Math.max(0, limit);

        for (int level : levels.descendingSet()) {
            if (budget <= 0) {
                break;
            }
            long[] room = new long[sinks];
            for (int j = 0; j < sinks; j++) {
                room[j] = priorities[j] == level ? Math.max(0, accepting[j]) : 0;
            }
            long[][] step = FlowPlanner.planBySource(left, room, budget, modes, rotation, random);
            for (int i = 0; i < sources; i++) {
                for (int j = 0; j < sinks; j++) {
                    moves[i][j] += step[i][j];
                    left[i] -= step[i][j];
                    budget -= step[i][j];
                }
            }
        }
        return moves;
    }
}
