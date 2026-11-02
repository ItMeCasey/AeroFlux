package com.itmecasey.aeroflux.core.conduit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itmecasey.aeroflux.core.Tier;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TransferTest {

    // --- PipeTiers --------------------------------------------------------------------------

    @Test
    void everyTierMovesMoreThanTheOneBelow() {
        Tier[] tiers = Tier.values();
        for (int i = 1; i < tiers.length; i++) {
            Tier low = tiers[i - 1];
            Tier high = tiers[i];
            assertTrue(PipeTiers.fluidPerTick(high) > PipeTiers.fluidPerTick(low), "fluid " + high);
            assertTrue(PipeTiers.energyPerTick(high) > PipeTiers.energyPerTick(low), "energy " + high);
            assertTrue(PipeTiers.gasPerTick(high) > PipeTiers.gasPerTick(low), "gas " + high);
            assertTrue(PipeTiers.itemsPerOperation(high) > PipeTiers.itemsPerOperation(low), "item batch " + high);
            assertTrue(PipeTiers.ticksPerOperation(high) < PipeTiers.ticksPerOperation(low), "item speed " + high);
            assertTrue(PipeTiers.redstoneChannels(high) > PipeTiers.redstoneChannels(low), "channels " + high);
        }
    }

    @Test
    void theEndsOfTheTablesMatchThePlan() {
        assertEquals(100, PipeTiers.fluidPerTick(Tier.COPPER));
        assertEquals(12_000, PipeTiers.fluidPerTick(Tier.FLUXIUM));
        assertEquals(1_000, PipeTiers.energyPerTick(Tier.COPPER));
        assertEquals(1_000_000, PipeTiers.energyPerTick(Tier.FLUXIUM));
        assertEquals(256, PipeTiers.gasPerTick(Tier.COPPER));
        assertEquals(64_000, PipeTiers.gasPerTick(Tier.FLUXIUM));
        assertEquals(1, PipeTiers.itemsPerOperation(Tier.COPPER));
        assertEquals(64, PipeTiers.itemsPerOperation(Tier.FLUXIUM));
        assertEquals(20, PipeTiers.ticksPerOperation(Tier.COPPER));
        assertEquals(2, PipeTiers.ticksPerOperation(Tier.FLUXIUM));
        assertEquals(1, PipeTiers.redstoneChannels(Tier.COPPER));
        assertEquals(16, PipeTiers.redstoneChannels(Tier.FLUXIUM));
    }

    @Test
    void onlyMePipesHaveNoTiers() {
        for (PipeType type : PipeType.values()) {
            assertEquals(type != PipeType.ME && type != PipeType.DENSE_ME, PipeTiers.isTiered(type), type.id());
        }
    }

    // --- FlowPlanner ------------------------------------------------------------------------

    @Test
    void theLimitCapsWhatMoves() {
        FlowPlanner.Plan plan = FlowPlanner.plan(new long[] {1_000}, new long[] {1_000}, 100, 0);
        assertEquals(100, plan.total());
        assertEquals(100, plan.insert()[0]);
    }

    @Test
    void thePoorestPartyCapsWhatMoves() {
        assertEquals(30, FlowPlanner.plan(new long[] {30}, new long[] {1_000}, 500, 0).total());
        assertEquals(40, FlowPlanner.plan(new long[] {1_000}, new long[] {40}, 500, 0).total());
    }

    @Test
    void whatLeavesSourcesEqualsWhatEntersSinks() {
        FlowPlanner.Plan plan = FlowPlanner.plan(new long[] {70, 5, 200}, new long[] {10, 90}, 120, 3);
        long in = 0;
        for (long amount : plan.insert()) {
            in += amount;
        }
        assertEquals(plan.total(), in);
        assertEquals(100, plan.total(), "sinks can take only 100");
    }

    @Test
    void sourcesShareTheLoadEvenly() {
        FlowPlanner.Plan plan = FlowPlanner.plan(new long[] {100, 100, 100}, new long[] {1_000}, 90, 0);
        assertArrayEquals(new long[] {30, 30, 30}, plan.extract());
    }

    @Test
    void aSmallSourceGivesEverythingAndOthersCoverTheRest() {
        FlowPlanner.Plan plan = FlowPlanner.plan(new long[] {10, 100, 100}, new long[] {1_000}, 90, 0);
        assertEquals(10, plan.extract()[0]);
        assertEquals(40, plan.extract()[1]);
        assertEquals(40, plan.extract()[2]);
    }

    @Test
    void noSharePassesWhatAPartyCanDo() {
        long[] available = {7, 13, 5};
        long[] accepting = {4, 50, 1};
        FlowPlanner.Plan plan = FlowPlanner.plan(available, accepting, 20, 1);
        for (int i = 0; i < available.length; i++) {
            assertTrue(plan.extract()[i] <= available[i], "source " + i);
        }
        for (int i = 0; i < accepting.length; i++) {
            assertTrue(plan.insert()[i] <= accepting[i], "sink " + i);
        }
    }

    @Test
    void theRemainderRotatesBetweenSteps() {
        long[] sources = {10, 10, 10};
        long[] sink = {1_000};
        // 10 units over 3 sources: 3 each plus one extra, which should move around.
        long[] firstStep = FlowPlanner.plan(sources, sink, 10, 0).extract();
        long[] secondStep = FlowPlanner.plan(sources, sink, 10, 1).extract();
        assertEquals(10, firstStep[0] + firstStep[1] + firstStep[2]);
        assertFalse(java.util.Arrays.equals(firstStep, secondStep), "the extra unit should go elsewhere");
    }

    @Test
    void emptyAndNegativeInputsMoveNothing() {
        assertEquals(0, FlowPlanner.plan(new long[0], new long[] {10}, 100, 0).total());
        assertEquals(0, FlowPlanner.plan(new long[] {10}, new long[0], 100, 0).total());
        assertEquals(0, FlowPlanner.plan(new long[] {-5}, new long[] {10}, 100, 0).total());
        assertEquals(0, FlowPlanner.plan(new long[] {10}, new long[] {10}, 0, 0).total());
    }

    // --- NetworkFinder ----------------------------------------------------------------------

    private static final Map<Integer, List<Integer>> GRAPH = Map.of(
            1, List.of(2, 3),
            2, List.of(1, 4),
            3, List.of(1),
            4, List.of(2),
            5, List.of(6),
            6, List.of(5));

    @Test
    void theFinderCollectsOnlyTheConnectedPart() {
        NetworkFinder.Result<Integer> result = NetworkFinder.find(1, GRAPH::get, 100);
        assertEquals(java.util.Set.of(1, 2, 3, 4), result.nodes());
        assertFalse(result.truncated());
    }

    @Test
    void aLoneNodeIsItsOwnNetwork() {
        NetworkFinder.Result<Integer> result = NetworkFinder.find(9, node -> List.of(), 100);
        assertEquals(java.util.Set.of(9), result.nodes());
    }

    @Test
    void theFinderStopsAtTheSizeLimit() {
        // An endless line of nodes: n is joined to n+1.
        NetworkFinder.Result<Integer> result = NetworkFinder.find(0, n -> List.of(n + 1), 50);
        assertEquals(50, result.nodes().size());
        assertTrue(result.truncated());
    }

    // --- Tiers and modes in the bundle ------------------------------------------------------

    @Test
    void modesSayWhichWayThingsMove() {
        assertTrue(ConnectionMode.OPEN.pullsFromNeighbor() && ConnectionMode.OPEN.pushesToNeighbor());
        assertTrue(ConnectionMode.IMPORT.pullsFromNeighbor() && !ConnectionMode.IMPORT.pushesToNeighbor());
        assertTrue(!ConnectionMode.EXPORT.pullsFromNeighbor() && ConnectionMode.EXPORT.pushesToNeighbor());
        for (ConnectionMode passive : List.of(ConnectionMode.OFF, ConnectionMode.BLOCKED)) {
            assertTrue(!passive.pullsFromNeighbor() && !passive.pushesToNeighbor(), passive.name());
        }
    }

    @Test
    void eachPipeKeepsItsOwnTier() {
        BundleState state = new BundleState();
        state.addPipe(PipeType.FLUID, Tier.GOLD);
        state.addPipe(PipeType.ENERGY, Tier.FLUXIUM);
        state.addPipe(PipeType.ITEM);
        assertEquals(Tier.GOLD, state.tier(PipeType.FLUID.lane()));
        assertEquals(Tier.FLUXIUM, state.tier(PipeType.ENERGY.lane()));
        assertEquals(Tier.COPPER, state.tier(PipeType.ITEM.lane()), "the default is the lowest tier");
    }

    @Test
    void tiersSurviveSaving() {
        BundleState state = new BundleState();
        state.addPipe(PipeType.FLUID, Tier.DIAMOND);
        state.addPipe(PipeType.GAS, Tier.NETHERITE);
        BundleState copy = BundleState.deserialize(state.serialize());
        assertEquals(state, copy);
        assertEquals(Tier.DIAMOND, copy.tier(PipeType.FLUID.lane()));
        assertEquals(Tier.NETHERITE, copy.tier(PipeType.GAS.lane()));
    }

    @Test
    void dataSavedBeforeTiersExistedStillLoads() {
        BundleState state = new BundleState();
        state.addPipe(PipeType.ITEM);
        state.setConnection(PipeType.ITEM.lane(), Face.UP, true, false);
        int[] current = state.serialize();
        // The oldest format had no tier word and no upgrade words: three words per lane plus the set.
        int[] legacy = java.util.Arrays.copyOf(current, 1 + 3 * PipeType.LANES);

        BundleState loaded = BundleState.deserialize(legacy);
        assertTrue(loaded.set().contains(PipeType.ITEM));
        assertTrue(loaded.isConnected(PipeType.ITEM.lane(), Face.UP));
        assertEquals(Tier.COPPER, loaded.tier(PipeType.ITEM.lane()));
    }

    @Test
    void removingAPipeResetsItsTier() {
        BundleState state = new BundleState();
        state.addPipe(PipeType.FLUID, Tier.GOLD);
        state.removeLane(PipeType.FLUID.lane());
        state.addPipe(PipeType.FLUID);
        assertEquals(Tier.COPPER, state.tier(PipeType.FLUID.lane()));
    }
}
