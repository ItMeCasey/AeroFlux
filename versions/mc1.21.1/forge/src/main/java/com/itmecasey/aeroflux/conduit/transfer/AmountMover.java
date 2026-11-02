package com.itmecasey.aeroflux.conduit.transfer;

import com.itmecasey.aeroflux.core.conduit.DistributionMode;
import com.itmecasey.aeroflux.core.conduit.FlowPlanner;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * The part fluid and energy have in common: plan a step with {@link FlowPlanner}, take the
 * planned amounts out of the sources and put them into the sinks. A resource only has to describe
 * its machines as {@link Port}s.
 */
final class AmountMover {
    private AmountMover() {
    }

    /** One machine face as seen by the mover, for a single resource that is already chosen. */
    interface Port {
        /** The machine block behind this port. Ports on the same block are never paired. */
        BlockPos target();

        /** How much this port could give right now, at most {@code limit}. Must not change anything. */
        long available(long limit);

        /** How much this port could take right now, at most {@code limit}. Must not change anything. */
        long accepting(long limit);

        /** Takes up to {@code amount} out and returns how much really came out. */
        long extract(long amount);

        /** Puts up to {@code amount} in and returns how much was really taken. */
        long insert(long amount);
    }

    /**
     * Runs one step.
     *
     * @param sources the ports the network pulls from, in endpoint order
     * @param sinks   the ports the network pushes to, in endpoint order
     * @param modes   the distribution mode of each source, same order as {@code sources}
     * @param limit   the most that may move in this step
     * @return how much moved, for tests and logging
     */
    static long move(List<Port> sources, List<Port> sinks, DistributionMode[] modes, long limit, LaneState state) {
        if (sources.isEmpty() || sinks.isEmpty() || limit <= 0) {
            return 0;
        }
        long[] available = new long[sources.size()];
        long[] accepting = new long[sinks.size()];
        for (int i = 0; i < available.length; i++) {
            available[i] = guardedRead(sources.get(i), true, limit);
        }
        for (int j = 0; j < accepting.length; j++) {
            accepting[j] = guardedRead(sinks.get(j), false, limit);
        }

        long[][] plan = FlowPlanner.planBySource(available, accepting, limit, modes, state.nextRotation(), state.random());
        long moved = 0;
        for (int i = 0; i < sources.size(); i++) {
            moved += carryOut(sources.get(i), sinks, plan[i]);
        }
        return moved;
    }

    /** Takes one source's planned amount and hands it to the sinks; anything left goes back. */
    private static long carryOut(Port source, List<Port> sinks, long[] shares) {
        long want = 0;
        for (int j = 0; j < shares.length; j++) {
            // A machine that is both a source and a sink would only pass the amount to itself.
            if (!source.target().equals(sinks.get(j).target())) {
                want += shares[j];
            }
        }
        if (want <= 0) {
            return 0;
        }
        try {
            long taken = source.extract(want);
            long left = taken;
            for (int j = 0; j < shares.length && left > 0; j++) {
                Port sink = sinks.get(j);
                if (shares[j] <= 0 || source.target().equals(sink.target())) {
                    continue;
                }
                left -= sink.insert(Math.min(left, shares[j]));
            }
            if (left > 0) {
                // The sinks took less than was promised; put the rest back where it came from.
                left -= source.insert(left);
            }
            return taken - left;
        } catch (RuntimeException e) {
            TransferFaults.report(source.target(), e);
            return 0;
        }
    }

    private static long guardedRead(Port port, boolean asSource, long limit) {
        try {
            long value = asSource ? port.available(limit) : port.accepting(limit);
            return Math.max(0, value);
        } catch (RuntimeException e) {
            TransferFaults.report(port.target(), e);
            return 0;
        }
    }
}
