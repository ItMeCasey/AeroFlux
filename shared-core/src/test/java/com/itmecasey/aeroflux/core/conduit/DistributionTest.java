package com.itmecasey.aeroflux.core.conduit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DistributionTest {

    private static long rowSum(long[] row) {
        return Arrays.stream(row).sum();
    }

    private static long columnSum(long[][] moves, int column) {
        long sum = 0;
        for (long[] row : moves) {
            sum += row[column];
        }
        return sum;
    }

    // --- The orderings ----------------------------------------------------------------------

    @Test
    void firstToLastAndLastToFirstAreFixedOrders() {
        Random random = new Random(1);
        assertArrayEquals(new int[] {0, 1, 2, 3}, DistributionMode.FIRST_TO_LAST.order(4, 7, random));
        assertArrayEquals(new int[] {3, 2, 1, 0}, DistributionMode.LAST_TO_FIRST.order(4, 7, random));
    }

    @Test
    void roundRobinRotatesTheStartingPoint() {
        Random random = new Random(1);
        assertArrayEquals(new int[] {0, 1, 2}, DistributionMode.ROUND_ROBIN.order(3, 0, random));
        assertArrayEquals(new int[] {1, 2, 0}, DistributionMode.ROUND_ROBIN.order(3, 1, random));
        assertArrayEquals(new int[] {2, 0, 1}, DistributionMode.ROUND_ROBIN.order(3, 5, random));
    }

    @Test
    void randomVisitsEveryDestinationOnceAndVaries() {
        Random random = new Random(42);
        boolean sawDifferentOrders = false;
        int[] previous = null;
        for (int i = 0; i < 20; i++) {
            int[] order = DistributionMode.RANDOM.order(6, 0, random);
            int[] sorted = order.clone();
            Arrays.sort(sorted);
            assertArrayEquals(new int[] {0, 1, 2, 3, 4, 5}, sorted, "a permutation");
            if (previous != null && !Arrays.equals(previous, order)) {
                sawDifferentOrders = true;
            }
            previous = order;
        }
        assertTrue(sawDifferentOrders, "twenty random orders should not all match");
    }

    @Test
    void anEmptyListOfDestinationsGivesAnEmptyOrder() {
        for (DistributionMode mode : DistributionMode.values()) {
            assertEquals(0, mode.order(0, 3, new Random(1)).length, mode.name());
        }
    }

    @Test
    void theWrenchCyclesThroughEveryDistributionMode() {
        DistributionMode mode = DistributionMode.ROUND_ROBIN;
        for (int i = 0; i < DistributionMode.values().length; i++) {
            mode = mode.next();
        }
        assertEquals(DistributionMode.ROUND_ROBIN, mode);
    }

    // --- Planning by source -----------------------------------------------------------------

    @Test
    void firstToLastFillsTheFirstSinkBeforeTheSecond() {
        long[][] moves = FlowPlanner.planBySource(new long[] {100}, new long[] {30, 30, 30}, 100,
                new DistributionMode[] {DistributionMode.FIRST_TO_LAST}, 0, new Random(1));
        assertArrayEquals(new long[] {30, 30, 30}, moves[0], "capacity 90 fills in order, all of it");

        long[][] partial = FlowPlanner.planBySource(new long[] {40}, new long[] {30, 30, 30}, 100,
                new DistributionMode[] {DistributionMode.FIRST_TO_LAST}, 0, new Random(1));
        assertArrayEquals(new long[] {30, 10, 0}, partial[0]);
    }

    @Test
    void lastToFirstFillsTheLastSinkFirst() {
        long[][] moves = FlowPlanner.planBySource(new long[] {40}, new long[] {30, 30, 30}, 100,
                new DistributionMode[] {DistributionMode.LAST_TO_FIRST}, 0, new Random(1));
        assertArrayEquals(new long[] {0, 10, 30}, moves[0]);
    }

    @Test
    void roundRobinSharesEvenlyAcrossSinks() {
        long[][] moves = FlowPlanner.planBySource(new long[] {90}, new long[] {100, 100, 100}, 90,
                new DistributionMode[] {DistributionMode.ROUND_ROBIN}, 0, new Random(1));
        assertArrayEquals(new long[] {30, 30, 30}, moves[0]);
    }

    @Test
    void randomStillRespectsEverySinksRoom() {
        long[][] moves = FlowPlanner.planBySource(new long[] {500}, new long[] {10, 20, 30}, 500,
                new DistributionMode[] {DistributionMode.RANDOM}, 0, new Random(7));
        assertEquals(60, rowSum(moves[0]));
        assertTrue(moves[0][0] <= 10 && moves[0][1] <= 20 && moves[0][2] <= 30);
    }

    @Test
    void theLimitAndTheRoomAreNeverExceededWhateverTheModes() {
        DistributionMode[] modes = {DistributionMode.FIRST_TO_LAST, DistributionMode.RANDOM, DistributionMode.ROUND_ROBIN};
        long[] available = {50, 80, 20};
        long[] accepting = {25, 25, 25, 25};
        for (int rotation = 0; rotation < 6; rotation++) {
            long[][] moves = FlowPlanner.planBySource(available, accepting, 70, modes, rotation, new Random(rotation));
            long total = 0;
            for (int i = 0; i < moves.length; i++) {
                assertTrue(rowSum(moves[i]) <= available[i], "source " + i + " gives at most it has");
                total += rowSum(moves[i]);
            }
            for (int j = 0; j < accepting.length; j++) {
                assertTrue(columnSum(moves, j) <= accepting[j], "sink " + j + " takes at most its room");
            }
            assertTrue(total <= 70, "the limit holds");
            assertEquals(70, total, "there is enough supply and room to move the full limit");
        }
    }

    @Test
    void eachSourceUsesItsOwnMode() {
        // Source 0 fills front to back, source 1 back to front; each has 20 to give.
        long[][] moves = FlowPlanner.planBySource(new long[] {20, 20}, new long[] {20, 20}, 40,
                new DistributionMode[] {DistributionMode.FIRST_TO_LAST, DistributionMode.LAST_TO_FIRST}, 0, new Random(1));
        assertArrayEquals(new long[] {20, 0}, moves[0]);
        assertArrayEquals(new long[] {0, 20}, moves[1]);
    }

    @Test
    void noSourcesOrNoSinksMoveNothing() {
        assertEquals(0, FlowPlanner.planBySource(new long[0], new long[] {10}, 100,
                new DistributionMode[0], 0, new Random(1)).length);
        long[][] noSinks = FlowPlanner.planBySource(new long[] {10}, new long[0], 100,
                new DistributionMode[] {DistributionMode.RANDOM}, 0, new Random(1));
        assertEquals(0, noSinks[0].length);
    }

    // --- In the bundle ----------------------------------------------------------------------

    @Test
    void aPipeStartsOnRoundRobinAndKeepsAChangedMode() {
        BundleState state = new BundleState();
        state.addPipe(PipeType.ITEM);
        assertEquals(DistributionMode.ROUND_ROBIN, state.distribution(PipeType.ITEM.lane()));

        state.setDistribution(PipeType.ITEM.lane(), DistributionMode.LAST_TO_FIRST);
        BundleState copy = BundleState.deserialize(state.serialize());
        assertEquals(state, copy);
        assertEquals(DistributionMode.LAST_TO_FIRST, copy.distribution(PipeType.ITEM.lane()));
    }

    @Test
    void eachLaneKeepsItsOwnDistributionMode() {
        BundleState state = new BundleState();
        state.addPipe(PipeType.FLUID);
        state.addPipe(PipeType.ENERGY);
        state.setDistribution(PipeType.FLUID.lane(), DistributionMode.RANDOM);
        state.setDistribution(PipeType.ENERGY.lane(), DistributionMode.FIRST_TO_LAST);
        BundleState copy = BundleState.deserialize(state.serialize());
        assertEquals(DistributionMode.RANDOM, copy.distribution(PipeType.FLUID.lane()));
        assertEquals(DistributionMode.FIRST_TO_LAST, copy.distribution(PipeType.ENERGY.lane()));
    }

    @Test
    void removingAPipeResetsItsDistributionMode() {
        BundleState state = new BundleState();
        state.addPipe(PipeType.GAS);
        state.setDistribution(PipeType.GAS.lane(), DistributionMode.RANDOM);
        state.removeLane(PipeType.GAS.lane());
        state.addPipe(PipeType.GAS);
        assertFalse(state.distribution(PipeType.GAS.lane()) == DistributionMode.RANDOM);
    }
}
