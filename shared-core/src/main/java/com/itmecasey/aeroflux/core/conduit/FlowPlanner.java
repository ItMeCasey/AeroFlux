package com.itmecasey.aeroflux.core.conduit;

/**
 * Decides how much to take from each source and give to each sink in one step of a network.
 *
 * <p>The network moves as much as the sources hold, the sinks can take and the pipes allow,
 * whichever is least. That amount is shared out evenly: every source gives about the same and
 * every sink gets about the same, with anyone who cannot supply or take their share handing the
 * difference to the others. A rotation number lets the leftover one or two units go to a
 * different party each time, so nobody is favored.
 */
public final class FlowPlanner {
    private FlowPlanner() {
    }

    /**
     * The amount to take from each source and give to each sink. The two arrays always add up to
     * the same total.
     *
     * @param extract amount to extract from each source, same order as the input
     * @param insert  amount to insert into each sink, same order as the input
     */
    public record Plan(long[] extract, long[] insert) {
        /** The total amount that moves. */
        public long total() {
            long sum = 0;
            for (long amount : extract) {
                sum += amount;
            }
            return sum;
        }
    }

    /**
     * Plans one step.
     *
     * @param available how much each source can give right now; negative values count as 0
     * @param accepting how much each sink can take right now; negative values count as 0
     * @param limit     the most that may move in this step
     * @param rotation  changes who gets the remainder; use a counter that grows every step
     */
    public static Plan plan(long[] available, long[] accepting, long limit, int rotation) {
        long totalAvailable = sum(available);
        long totalAccepting = sum(accepting);
        long total = Math.max(0, Math.min(limit, Math.min(totalAvailable, totalAccepting)));
        return new Plan(share(available, total, rotation), share(accepting, total, rotation));
    }

    /**
     * Plans one step when each source has its own distribution mode.
     *
     * <p>The total that moves is the same as in {@link #plan}, shared evenly among the sources.
     * Each source then hands its part to the sinks the way its own mode says: evenly, front to
     * back, back to front, or in a random order. Sinks that fill up pass the rest on, so a source
     * can end up moving a little less than its part if the sinks run out of room.
     *
     * @param available how much each source can give; negative values count as 0
     * @param accepting how much each sink can take; negative values count as 0
     * @param limit     the most that may move in this step
     * @param modes     the mode of each source, same order as {@code available}
     * @param rotation  a counter that grows every step
     * @param random    randomness for {@link DistributionMode#RANDOM}
     * @return {@code moves[source][sink]}, the amount going from each source to each sink
     */
    public static long[][] planBySource(long[] available, long[] accepting, long limit,
                                        DistributionMode[] modes, int rotation, java.util.Random random) {
        int sources = available.length;
        int sinks = accepting.length;
        long[][] moves = new long[sources][sinks];
        long[] room = new long[sinks];
        for (int j = 0; j < sinks; j++) {
            room[j] = Math.max(0, accepting[j]);
        }

        long total = Math.max(0, Math.min(limit, Math.min(sum(available), sum(accepting))));
        long[] part = share(available, total, rotation);

        // Serve the sources in rotating order so the first one does not always get first pick of room.
        for (int step = 0; step < sources; step++) {
            int i = Math.floorMod(rotation + step, sources);
            long left = part[i];
            DistributionMode mode = modes[i];
            if (mode == DistributionMode.ROUND_ROBIN) {
                long[] given = share(room, Math.min(left, sum(room)), rotation + i);
                for (int j = 0; j < sinks; j++) {
                    moves[i][j] += given[j];
                    room[j] -= given[j];
                }
            } else {
                for (int j : mode.order(sinks, rotation, random)) {
                    long give = Math.min(left, room[j]);
                    moves[i][j] += give;
                    room[j] -= give;
                    left -= give;
                    if (left == 0) {
                        break;
                    }
                }
            }
        }
        return moves;
    }

    /**
     * Splits {@code amount} across parties as evenly as their capacities allow.
     *
     * @param capacity the most each party can take part in
     * @param amount   the total to split; must not exceed the sum of the capacities
     * @param rotation which party gets the remainder first
     */
    static long[] share(long[] capacity, long amount, int rotation) {
        int count = capacity.length;
        long[] shares = new long[count];
        long remaining = amount;
        while (remaining > 0) {
            int open = 0;
            for (int i = 0; i < count; i++) {
                if (capacity[i] > shares[i]) {
                    open++;
                }
            }
            if (open == 0) {
                break;
            }
            long each = remaining / open;
            if (each > 0) {
                for (int i = 0; i < count; i++) {
                    if (capacity[i] > shares[i]) {
                        long give = Math.min(each, capacity[i] - shares[i]);
                        shares[i] += give;
                        remaining -= give;
                    }
                }
            } else {
                // Fewer units left than parties: hand them out one at a time, starting at the rotation.
                for (int step = 0; step < count && remaining > 0; step++) {
                    int i = Math.floorMod(rotation + step, count);
                    if (capacity[i] > shares[i]) {
                        shares[i]++;
                        remaining--;
                    }
                }
            }
        }
        return shares;
    }

    private static long sum(long[] values) {
        long total = 0;
        for (long value : values) {
            total += Math.max(0, value);
        }
        return total;
    }
}
