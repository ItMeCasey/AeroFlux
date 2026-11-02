package com.itmecasey.aeroflux.core.conduit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** The Filter and Priority upgrades: the filter rule, the priority planner and how both are saved. */
class EndpointUpgradeTest {
    private static final String STONE = "minecraft:stone";
    private static final String DIRT = "minecraft:dirt";

    // --- Filter ------------------------------------------------------------------------------

    @Test
    void newFilterIsAnEmptyWhitelistThatStopsEverything() {
        EndpointFilter filter = new EndpointFilter();
        assertTrue(filter.isWhitelist());
        assertTrue(filter.isEmpty());
        assertFalse(filter.accepts(STONE));
    }

    @Test
    void whitelistPassesOnlyWhatItLists() {
        EndpointFilter filter = new EndpointFilter();
        filter.setEntry(4, STONE);
        assertTrue(filter.accepts(STONE));
        assertFalse(filter.accepts(DIRT));
        assertFalse(filter.accepts(null));
    }

    @Test
    void blacklistPassesEverythingElse() {
        EndpointFilter filter = new EndpointFilter();
        filter.setWhitelist(false);
        assertTrue(filter.accepts(STONE), "an empty blacklist stops nothing");
        filter.setEntry(0, STONE);
        assertFalse(filter.accepts(STONE));
        assertTrue(filter.accepts(DIRT));
    }

    @Test
    void entriesCanBeClearedAndReportChanges() {
        EndpointFilter filter = new EndpointFilter();
        assertTrue(filter.setEntry(2, STONE));
        assertFalse(filter.setEntry(2, STONE), "the same id again is no change");
        assertTrue(filter.setEntry(2, null));
        assertNull(filter.entry(2));
        assertFalse(filter.setEntry(EndpointFilter.SLOTS, STONE), "out of range");
        assertTrue(filter.isEmpty());
    }

    // --- Priority ordering -------------------------------------------------------------------

    @Test
    void higherPrioritiesComeFirstAndTiesKeepTheirOrder() {
        int[] priorities = {0, 3, 0, -2, 3};
        int[] order = {2, 3, 4, 0, 1};
        assertArrayEquals(new int[] {4, 1, 2, 0, 3}, PriorityPlanner.byPriority(order, priorities));
    }

    @Test
    void equalPrioritiesLeaveTheOrderAlone() {
        int[] order = {3, 1, 0, 2};
        assertArrayEquals(order, PriorityPlanner.byPriority(order, new int[4]));
    }

    @Test
    void clampKeepsPrioritiesInRange() {
        assertEquals(PriorityPlanner.MAX, PriorityPlanner.clamp(99));
        assertEquals(PriorityPlanner.MIN, PriorityPlanner.clamp(-99));
        assertEquals(2, PriorityPlanner.clamp(2));
    }

    // --- Priority planning -------------------------------------------------------------------

    private static long column(long[][] moves, int sink) {
        long sum = 0;
        for (long[] row : moves) {
            sum += row[sink];
        }
        return sum;
    }

    @Test
    void sameAsFlowPlannerWhenEveryPriorityIsEqual() {
        long[] available = {100, 40};
        long[] accepting = {30, 80, 50};
        DistributionMode[] modes = {DistributionMode.ROUND_ROBIN, DistributionMode.FIRST_TO_LAST};
        long[][] plain = FlowPlanner.planBySource(available, accepting, 90, modes, 3, new Random(5));
        long[][] ranked = PriorityPlanner.planBySource(available, accepting, new int[3], 90, modes, 3, new Random(5));
        for (int i = 0; i < plain.length; i++) {
            assertArrayEquals(plain[i], ranked[i]);
        }
    }

    @Test
    void highPrioritySinkFillsBeforeTheOthers() {
        long[] available = {100};
        long[] accepting = {50, 50, 50};
        int[] priorities = {0, 2, 0};
        long[][] moves = PriorityPlanner.planBySource(available, accepting, priorities, 70,
                new DistributionMode[] {DistributionMode.ROUND_ROBIN}, 0, new Random(1));
        assertEquals(50, column(moves, 1), "the priority 2 sink is filled first");
        assertEquals(20, column(moves, 0) + column(moves, 2), "the rest is shared by the others");
        assertEquals(10, column(moves, 0));
        assertEquals(10, column(moves, 2));
    }

    @Test
    void lowPrioritySinkOnlyGetsTheOverflow() {
        long[] available = {60};
        long[] accepting = {100, 100};
        int[] priorities = {-1, 0};
        long[][] moves = PriorityPlanner.planBySource(available, accepting, priorities, 1_000,
                new DistributionMode[] {DistributionMode.FIRST_TO_LAST}, 0, new Random(1));
        assertEquals(0, column(moves, 0));
        assertEquals(60, column(moves, 1));
    }

    @Test
    void theStepLimitHoldsAcrossLevels() {
        long[] available = {500, 500};
        long[] accepting = {100, 100, 100};
        int[] priorities = {5, 0, -5};
        long[][] moves = PriorityPlanner.planBySource(available, accepting, priorities, 150,
                new DistributionMode[] {DistributionMode.ROUND_ROBIN, DistributionMode.ROUND_ROBIN}, 0, new Random(1));
        assertEquals(100, column(moves, 0));
        assertEquals(50, column(moves, 1));
        assertEquals(0, column(moves, 2));
    }

    // --- Settings ----------------------------------------------------------------------------

    @Test
    void installingAndRemovingUpgrades() {
        EndpointSettings settings = new EndpointSettings();
        int lane = PipeType.ITEM.lane();
        assertTrue(settings.install(lane, Face.NORTH, EndpointSettings.Upgrade.FILTER));
        assertFalse(settings.install(lane, Face.NORTH, EndpointSettings.Upgrade.FILTER), "one per face");
        assertTrue(settings.install(lane, Face.EAST, EndpointSettings.Upgrade.PRIORITY));
        assertEquals(1, settings.count(lane, EndpointSettings.Upgrade.FILTER));
        assertEquals(PriorityPlanner.DEFAULT, settings.priority(lane, Face.EAST));
        assertNull(settings.filter(lane, Face.EAST));

        assertTrue(settings.setPriority(lane, Face.EAST, 9));
        assertEquals(PriorityPlanner.MAX, settings.priority(lane, Face.EAST));
        assertFalse(settings.setPriority(lane, Face.NORTH, 1), "no Priority upgrade there");

        assertTrue(settings.remove(lane, Face.EAST, EndpointSettings.Upgrade.PRIORITY));
        assertEquals(PriorityPlanner.DEFAULT, settings.priority(lane, Face.EAST));
        assertTrue(settings.remove(lane, Face.NORTH, EndpointSettings.Upgrade.FILTER));
        assertTrue(settings.isEmpty());
    }

    @Test
    void onlyItemAndFluidPipesTakeFilters() {
        assertTrue(EndpointSettings.supportsFilter(PipeType.ITEM));
        assertTrue(EndpointSettings.supportsFilter(PipeType.FLUID));
        assertFalse(EndpointSettings.supportsFilter(PipeType.ENERGY));
        assertTrue(EndpointSettings.supportsPriority(PipeType.ENERGY));
        assertFalse(EndpointSettings.supportsPriority(PipeType.REDSTONE));
    }

    @Test
    void encodeAndDecodeRoundTrip() {
        EndpointSettings settings = new EndpointSettings();
        int item = PipeType.ITEM.lane();
        int fluid = PipeType.FLUID.lane();
        settings.install(item, Face.UP, EndpointSettings.Upgrade.FILTER);
        settings.filter(item, Face.UP).setEntry(0, STONE);
        settings.filter(item, Face.UP).setEntry(8, DIRT);
        settings.filter(item, Face.UP).setWhitelist(false);
        settings.install(item, Face.UP, EndpointSettings.Upgrade.PRIORITY);
        settings.setPriority(item, Face.UP, -3);
        settings.install(fluid, Face.SOUTH, EndpointSettings.Upgrade.FILTER);
        settings.filter(fluid, Face.SOUTH).setEntry(4, "minecraft:water");

        EndpointSettings restored = EndpointSettings.decode(settings.encode());
        assertEquals(settings, restored);
        assertEquals(-3, restored.priority(item, Face.UP));
        assertFalse(restored.filter(item, Face.UP).isWhitelist());
        assertEquals(DIRT, restored.filter(item, Face.UP).entry(8));
        assertTrue(restored.filter(fluid, Face.SOUTH).isWhitelist());
    }

    @Test
    void missingOrDamagedDataLoadsEmpty() {
        assertTrue(EndpointSettings.decode(null).isEmpty());
        assertTrue(EndpointSettings.decode("").isEmpty());
        assertTrue(EndpointSettings.decode("9;1,0,1,0,").isEmpty(), "unknown version");
        EndpointSettings partly = EndpointSettings.decode("1;x,0,1,0,;1,2,2,4,");
        assertEquals(4, partly.priority(1, Face.byIndex(2)), "the good record survives the bad one");
    }

    @Test
    void retainValidDropsUpgradesWithoutAPipe() {
        EndpointSettings settings = new EndpointSettings();
        settings.install(PipeType.ITEM.lane(), Face.DOWN, EndpointSettings.Upgrade.FILTER);
        settings.install(PipeType.FLUID.lane(), Face.DOWN, EndpointSettings.Upgrade.PRIORITY);
        settings.retainValid(PipeSet.of(PipeType.FLUID));
        assertFalse(settings.has(PipeType.ITEM.lane(), Face.DOWN, EndpointSettings.Upgrade.FILTER));
        assertTrue(settings.has(PipeType.FLUID.lane(), Face.DOWN, EndpointSettings.Upgrade.PRIORITY));
    }
}
