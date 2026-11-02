package com.itmecasey.aeroflux.core.conduit;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * Finds every member of a pipe network: all the nodes reachable from a starting node through
 * connections. It knows nothing about blocks; the game supplies what "next to" means, so the
 * search can be tested on plain graphs.
 */
public final class NetworkFinder {
    private NetworkFinder() {
    }

    /**
     * The result of a search.
     *
     * @param nodes     every node found, in the order they were reached
     * @param truncated true if the search stopped at the size limit, so {@code nodes} is only part
     *                  of the network
     * @param <N>       the node type
     */
    public record Result<N>(Set<N> nodes, boolean truncated) {
    }

    /**
     * Searches outward from {@code start}.
     *
     * @param start     where to begin; always part of the result
     * @param neighbors the nodes directly joined to a node
     * @param limit     the most nodes to collect, so a huge network cannot stall the server
     */
    public static <N> Result<N> find(N start, Function<N, Iterable<N>> neighbors, int limit) {
        Set<N> found = new LinkedHashSet<>();
        ArrayDeque<N> queue = new ArrayDeque<>();
        found.add(start);
        queue.add(start);
        boolean truncated = false;
        while (!queue.isEmpty() && !truncated) {
            N node = queue.poll();
            for (N next : neighbors.apply(node)) {
                if (found.contains(next)) {
                    continue;
                }
                if (found.size() >= limit) {
                    truncated = true;
                    break;
                }
                found.add(next);
                queue.add(next);
            }
        }
        return new Result<>(found, truncated);
    }
}
