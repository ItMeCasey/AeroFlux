package com.itmecasey.aeroflux.core.conduit;

import java.util.Random;

/**
 * How a pipe chooses among the places it can deliver to.
 *
 * <p>Destinations are numbered in a fixed order (by pipe position, then face), so "first" and
 * "last" mean the same thing every time. Each pipe has its own mode, used for what it takes in
 * from the blocks that feed it.
 */
public enum DistributionMode {
    /** Share evenly, and rotate who gets the leftover so nobody is favored. */
    ROUND_ROBIN,
    /** Fill the first destination before moving on to the second, and so on. */
    FIRST_TO_LAST,
    /** Fill the last destination first, then work backward. */
    LAST_TO_FIRST,
    /** Fill destinations in a random order, different each time. */
    RANDOM;

    /** The next mode in the order the wrench cycles through. */
    public DistributionMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /**
     * The order in which to try {@code count} destinations. For {@link #ROUND_ROBIN} this is a
     * rotation of 0..count-1 starting at {@code rotation}; for the others the fixed or random
     * order. Callers that fill sequentially use it directly; round-robin callers share evenly and
     * only use it to decide who gets any leftover.
     *
     * @param count    how many destinations there are
     * @param rotation a counter that grows each step, used by round-robin
     * @param random   source of randomness, used by {@link #RANDOM}
     */
    public int[] order(int count, int rotation, Random random) {
        int[] order = new int[count];
        switch (this) {
            case FIRST_TO_LAST -> {
                for (int i = 0; i < count; i++) {
                    order[i] = i;
                }
            }
            case LAST_TO_FIRST -> {
                for (int i = 0; i < count; i++) {
                    order[i] = count - 1 - i;
                }
            }
            case RANDOM -> {
                for (int i = 0; i < count; i++) {
                    order[i] = i;
                }
                for (int i = count - 1; i > 0; i--) {
                    int j = random.nextInt(i + 1);
                    int swap = order[i];
                    order[i] = order[j];
                    order[j] = swap;
                }
            }
            default -> {
                for (int i = 0; i < count; i++) {
                    order[i] = Math.floorMod(rotation + i, Math.max(count, 1));
                }
            }
        }
        return order;
    }
}
