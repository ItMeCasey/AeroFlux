package com.itmecasey.aeroflux.core.conduit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itmecasey.aeroflux.core.Tier;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolTest {

    @Test
    void theToolCyclesThroughEveryModeAndWraps() {
        ToolMode mode = ToolMode.EDIT;
        for (int i = 0; i < ToolMode.values().length; i++) {
            mode = mode.next();
        }
        assertEquals(ToolMode.EDIT, mode);
    }

    // --- RunPlanner -------------------------------------------------------------------------

    @Test
    void aStraightRunIncludesBothEndsInOrder() {
        List<RunPlanner.Pos> run = RunPlanner.line(new RunPlanner.Pos(0, 5, 0), new RunPlanner.Pos(3, 5, 0));
        assertEquals(List.of(new RunPlanner.Pos(0, 5, 0), new RunPlanner.Pos(1, 5, 0),
                new RunPlanner.Pos(2, 5, 0), new RunPlanner.Pos(3, 5, 0)), run);
    }

    @Test
    void runsWorkInEveryDirection() {
        assertEquals(new RunPlanner.Pos(0, 2, 0), RunPlanner.line(new RunPlanner.Pos(0, 4, 0), new RunPlanner.Pos(0, 2, 0)).get(2));
        assertEquals(3, RunPlanner.line(new RunPlanner.Pos(0, 0, 0), new RunPlanner.Pos(0, 0, -2)).size());
    }

    @Test
    void oneBlockIsARunOfOne() {
        assertEquals(1, RunPlanner.line(new RunPlanner.Pos(7, 7, 7), new RunPlanner.Pos(7, 7, 7)).size());
    }

    @Test
    void positionsThatAreNotOnALineGiveNothing() {
        assertTrue(RunPlanner.line(new RunPlanner.Pos(0, 0, 0), new RunPlanner.Pos(1, 0, 1)).isEmpty());
        assertTrue(RunPlanner.line(new RunPlanner.Pos(0, 0, 0), new RunPlanner.Pos(1, 1, 1)).isEmpty());
    }

    @Test
    void overlongRunsAreRefused() {
        assertEquals(RunPlanner.MAX_LENGTH,
                RunPlanner.line(new RunPlanner.Pos(0, 0, 0), new RunPlanner.Pos(RunPlanner.MAX_LENGTH - 1, 0, 0)).size());
        assertTrue(RunPlanner.line(new RunPlanner.Pos(0, 0, 0), new RunPlanner.Pos(RunPlanner.MAX_LENGTH, 0, 0)).isEmpty());
    }

    // --- BundleConfig -----------------------------------------------------------------------

    @Test
    void settingsCopyToABundleWithTheSamePipes() {
        BundleState source = new BundleState();
        source.addPipe(PipeType.FLUID, Tier.GOLD);
        source.setMode(PipeType.FLUID.lane(), Face.NORTH, ConnectionMode.EXPORT);
        source.setMode(PipeType.FLUID.lane(), Face.UP, ConnectionMode.BLOCKED);
        source.setDistribution(PipeType.FLUID.lane(), DistributionMode.RANDOM);

        BundleState target = new BundleState();
        target.addPipe(PipeType.FLUID, Tier.COPPER);

        assertEquals(1, BundleConfig.from(source).applyTo(target));
        assertEquals(ConnectionMode.EXPORT, target.mode(PipeType.FLUID.lane(), Face.NORTH));
        assertEquals(ConnectionMode.BLOCKED, target.mode(PipeType.FLUID.lane(), Face.UP));
        assertEquals(DistributionMode.RANDOM, target.distribution(PipeType.FLUID.lane()));
        assertEquals(Tier.COPPER, target.tier(PipeType.FLUID.lane()), "tiers are not copied");
    }

    @Test
    void onlyPipesInBothBundlesAreChanged() {
        BundleState source = new BundleState();
        source.addPipe(PipeType.FLUID);
        source.addPipe(PipeType.ITEM);
        source.setMode(PipeType.ITEM.lane(), Face.EAST, ConnectionMode.IMPORT);

        BundleState target = new BundleState();
        target.addPipe(PipeType.ITEM);
        target.addPipe(PipeType.ENERGY);

        assertEquals(1, BundleConfig.from(source).applyTo(target));
        assertEquals(ConnectionMode.IMPORT, target.mode(PipeType.ITEM.lane(), Face.EAST));
        assertEquals(ConnectionMode.OPEN, target.mode(PipeType.ENERGY.lane(), Face.EAST), "energy was not in the source");
    }

    @Test
    void theCopyDoesNotChangeWhenTheSourceDoes() {
        BundleState source = new BundleState();
        source.addPipe(PipeType.ITEM);
        source.setMode(PipeType.ITEM.lane(), Face.DOWN, ConnectionMode.EXPORT);
        BundleConfig copy = BundleConfig.from(source);

        source.setMode(PipeType.ITEM.lane(), Face.DOWN, ConnectionMode.OFF);

        BundleState target = new BundleState();
        target.addPipe(PipeType.ITEM);
        copy.applyTo(target);
        assertEquals(ConnectionMode.EXPORT, target.mode(PipeType.ITEM.lane(), Face.DOWN));
    }

    @Test
    void pastingNothingChangesNothing() {
        assertEquals(0, BundleConfig.from(new BundleState()).applyTo(new BundleState()));
        assertTrue(BundleConfig.from(new BundleState()).pipes().isEmpty());
    }
}
