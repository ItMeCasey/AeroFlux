package com.itmecasey.aeroflux.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProfileTest {
    private static final double EPSILON = 1e-9;
    private static final String OVERWORLD = "minecraft:overworld";

    /** Two furnaces and a grinder (machines), one collector and two bundles, over 10 ticks. */
    private static TickSampler sampleBase() {
        TickSampler sampler = new TickSampler();
        for (int tick = 0; tick < 10; tick++) {
            sampler.record(ProfileTypes.machine("flux_furnace"), OVERWORLD, 0, 64, 0, 40_000);
            sampler.record(ProfileTypes.machine("flux_furnace"), OVERWORLD, 2, 64, 0, 10_000);
            sampler.record(ProfileTypes.machine("flux_grinder"), OVERWORLD, 4, 64, 0, 20_000);
            sampler.record(ProfileTypes.COLLECTOR, OVERWORLD, 6, 64, 0, 5_000);
            sampler.record(ProfileTypes.CONDUIT_BUNDLE, OVERWORLD, 8, 64, 0, 1_000);
            sampler.record(ProfileTypes.CONDUIT_BUNDLE, OVERWORLD, 9, 64, 0, 1_000);
        }
        return sampler;
    }

    @Test
    void anEmptySamplerReportsZeroes() {
        ProfileReport report = ProfileReport.of(new TickSampler(), 100, 5);
        assertEquals(0, report.total().nanos());
        assertEquals(0, report.total().blocks());
        assertTrue(report.types().isEmpty());
        assertTrue(report.heaviest().isEmpty());
        assertEquals(0.0, report.total().msPerTick(report.windowTicks()), EPSILON);
        assertEquals(0.0, report.total().shareOf(0), EPSILON);
    }

    @Test
    void theTotalIsTheSumOfTheTypes() {
        ProfileReport report = ProfileReport.of(sampleBase(), 10, 5);
        long sum = report.types().stream().mapToLong(ProfileReport.Line::nanos).sum();
        assertEquals(report.total().nanos(), sum);
        assertEquals(10 * (40_000 + 10_000 + 20_000 + 5_000 + 2_000), report.total().nanos());
        assertEquals(6, report.total().blocks());
    }

    @Test
    void timeIsAveragedOverTheWindow() {
        ProfileReport report = ProfileReport.of(sampleBase(), 10, 5);
        // 770 microseconds over 10 ticks is 77 microseconds a tick.
        assertEquals(0.077, report.total().msPerTick(10), EPSILON);
        ProfileReport.Line furnaces = line(report.types(), "machine/flux_furnace");
        assertEquals(2, furnaces.blocks());
        assertEquals(20, furnaces.calls());
        assertEquals(40_000, furnaces.worstNanos());
        assertEquals(0.05, furnaces.msPerTick(10), EPSILON);
    }

    @Test
    void machinesAreAlsoGroupedTogether() {
        ProfileReport report = ProfileReport.of(sampleBase(), 10, 5);
        ProfileReport.Line machines = line(report.groups(), "machine");
        assertEquals(3, machines.blocks());
        assertEquals(10 * 70_000, machines.nanos());
        assertEquals(3, report.groups().size());
        assertEquals("machine", report.groups().get(0).type());
    }

    @Test
    void linesAreOrderedHeaviestFirst() {
        ProfileReport report = ProfileReport.of(sampleBase(), 10, 5);
        List<ProfileReport.Line> types = report.types();
        for (int i = 1; i < types.size(); i++) {
            assertTrue(types.get(i - 1).nanos() >= types.get(i).nanos());
        }
        assertEquals("machine/flux_furnace", types.get(0).type());
    }

    @Test
    void theHeaviestBlocksAreListedWithTheirPositions() {
        ProfileReport report = ProfileReport.of(sampleBase(), 10, 3);
        assertEquals(3, report.heaviest().size());
        ProfileReport.SiteLine first = report.heaviest().get(0);
        assertEquals("machine/flux_furnace", first.type());
        assertEquals(0, first.x());
        assertEquals(64, first.y());
        assertEquals(400_000, first.nanos());
        assertEquals(0.04, first.msPerTick(10), EPSILON);
        assertEquals(4, report.heaviest().get(1).x());
    }

    @Test
    void askingForMoreBlocksThanThereAreListsThemAll() {
        assertEquals(6, ProfileReport.of(sampleBase(), 10, 50).heaviest().size());
        assertEquals(0, ProfileReport.of(sampleBase(), 10, 0).heaviest().size());
        assertEquals(0, ProfileReport.of(sampleBase(), 10, -3).heaviest().size());
    }

    @Test
    void sharesAddUpToOne() {
        ProfileReport report = ProfileReport.of(sampleBase(), 10, 5);
        double sum = report.types().stream().mapToDouble(line -> line.shareOf(report.total().nanos())).sum();
        assertEquals(1.0, sum, EPSILON);
    }

    @Test
    void aNegativeTimeCountsAsZero() {
        TickSampler sampler = new TickSampler();
        sampler.record(ProfileTypes.LAMP, OVERWORLD, 0, 0, 0, -50);
        assertEquals(0, ProfileReport.of(sampler, 1, 1).total().nanos());
        assertEquals(1, sampler.samples());
    }

    @Test
    void sameKindInTwoDimensionsIsTwoBlocks() {
        TickSampler sampler = new TickSampler();
        sampler.record(ProfileTypes.COLLECTOR, OVERWORLD, 1, 2, 3, 10);
        sampler.record(ProfileTypes.COLLECTOR, "minecraft:the_nether", 1, 2, 3, 10);
        assertEquals(2, sampler.blocks());
    }

    @Test
    void theWindowIsNeverBelowOneTick() {
        assertEquals(1, ProfileReport.of(sampleBase(), 0, 1).windowTicks());
    }

    @Test
    void loadedCountsSplitBundlesFromTheRest() {
        ProfileReport report = ProfileReport.of(sampleBase(), 10, 5);
        assertEquals(2, report.loadedBundles());
        assertEquals(4, report.loadedMachineLike());
    }

    @Test
    void theReferenceBaseGetsTheDesignBudget() {
        assertEquals(0.5, ProfileBudget.allowedMs(50, 1_000), EPSILON);
        assertEquals(0.25, ProfileBudget.allowedMs(50, 0), EPSILON);
        assertEquals(1.0, ProfileBudget.allowedMs(100, 2_000), EPSILON);
    }

    @Test
    void aSmallWorldKeepsAFloor() {
        assertEquals(ProfileBudget.FLOOR_MS, ProfileBudget.allowedMs(0, 0), EPSILON);
        assertEquals(ProfileBudget.FLOOR_MS, ProfileBudget.allowedMs(3, 10), EPSILON);
        assertEquals(ProfileBudget.FLOOR_MS, ProfileBudget.allowedMs(-4, -9), EPSILON);
    }

    @Test
    void theBudgetComparisonIsInclusive() {
        assertTrue(ProfileBudget.within(0.5, 0.5));
        assertFalse(ProfileBudget.within(0.5001, 0.5));
        assertEquals(0.5, ProfileBudget.fractionUsed(0.25, 0.5), EPSILON);
        assertEquals(0.0, ProfileBudget.fractionUsed(0.25, 0.0), EPSILON);
    }

    @Test
    void theBudgetReadsTheReport() {
        ProfileReport report = ProfileReport.of(sampleBase(), 10, 5);
        // 4 machine-like blocks and 2 bundles: well under the floor.
        assertEquals(ProfileBudget.FLOOR_MS, ProfileBudget.allowedMs(report), EPSILON);
    }

    @Test
    void numbersUseAPointWhateverTheLocale() {
        assertEquals("0.125", ProfileFormat.ms(0.125));
        assertEquals("0.000", ProfileFormat.ms(0.0));
        assertEquals("12.5", ProfileFormat.micros(12_500));
        assertEquals("42", ProfileFormat.percent(0.42));
        assertEquals("100", ProfileFormat.percent(1.0));
        assertEquals("5.0", ProfileFormat.seconds(100));
        assertEquals("0.5", ProfileFormat.seconds(10));
    }

    @Test
    void groupsComeFromTheNamePrefix() {
        assertEquals("machine", ProfileTypes.groupOf("machine/flux_furnace"));
        assertEquals("collector", ProfileTypes.groupOf("collector"));
        assertEquals("machine/flux_furnace", ProfileTypes.machine("flux_furnace"));
        assertTrue(ProfileTypes.isBundle(ProfileTypes.CONDUIT_BUNDLE));
        assertFalse(ProfileTypes.isBundle(ProfileTypes.COLLECTOR));
    }

    private static ProfileReport.Line line(List<ProfileReport.Line> lines, String type) {
        return lines.stream().filter(line -> line.type().equals(type)).findFirst().orElseThrow();
    }
}
