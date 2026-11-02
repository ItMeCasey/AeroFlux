package com.itmecasey.aeroflux.core.conduit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConduitTest {
    private static final double EPSILON = 1e-9;

    // --- PipeSet -----------------------------------------------------------------------------

    @Test
    void aSetKeepsOnePipePerLane() {
        PipeSet set = PipeSet.of(PipeType.ME, PipeType.DENSE_ME);
        assertEquals(1, set.size());
        assertTrue(set.contains(PipeType.DENSE_ME));
        assertFalse(set.contains(PipeType.ME));
        assertTrue(set.hasLaneFor(PipeType.ME));
    }

    @Test
    void pipesAreOrderedByLaneNoMatterHowTheyWereAdded() {
        PipeSet a = PipeSet.of(PipeType.GAS, PipeType.FLUID, PipeType.ENERGY);
        PipeSet b = PipeSet.of(PipeType.ENERGY, PipeType.GAS, PipeType.FLUID);
        assertEquals(a, b);
        assertEquals(List.of(PipeType.FLUID, PipeType.ENERGY, PipeType.GAS), a.types());
        assertEquals(0, a.slotOf(PipeType.FLUID));
        assertEquals(1, a.slotOf(PipeType.ENERGY));
        assertEquals(2, a.slotOf(PipeType.GAS));
        assertEquals(-1, a.slotOf(PipeType.ITEM));
    }

    @Test
    void theKeyRoundTrips() {
        for (PipeSet set : List.of(PipeSet.EMPTY, PipeSet.of(PipeType.REDSTONE),
                PipeSet.of(PipeType.FLUID, PipeType.ITEM, PipeType.REDSTONE, PipeType.ENERGY,
                        PipeType.GAS, PipeType.DENSE_ME))) {
            assertEquals(set, PipeSet.fromKey(set.key()));
        }
    }

    // --- Layout and geometry ----------------------------------------------------------------

    @Test
    void everyBundleSizeHasALayout() {
        for (int count = 0; count <= 6; count++) {
            assertEquals(count, PipeGeometry.layout(count).size());
        }
    }

    @Test
    void aSinglePipeSitsAtTheCenterOfTheBlock() {
        assertEquals(new PipeGeometry.Slot(8, 8, 8), PipeGeometry.layout(1).get(0));
    }

    @Test
    void everyGroupIsCenteredInTheBlock() {
        for (int count = 1; count <= 6; count++) {
            List<Box> cores = PipeGeometry.layout(count).stream().map(PipeGeometry::core).toList();
            double minX = cores.stream().mapToDouble(Box::minX).min().orElseThrow();
            double maxX = cores.stream().mapToDouble(Box::maxX).max().orElseThrow();
            double minY = cores.stream().mapToDouble(Box::minY).min().orElseThrow();
            double maxY = cores.stream().mapToDouble(Box::maxY).max().orElseThrow();
            double minZ = cores.stream().mapToDouble(Box::minZ).min().orElseThrow();
            double maxZ = cores.stream().mapToDouble(Box::maxZ).max().orElseThrow();
            assertEquals(16.0, minX + maxX, EPSILON, count + " pipes: x");
            assertEquals(16.0, minY + maxY, EPSILON, count + " pipes: y");
            assertEquals(16.0, minZ + maxZ, EPSILON, count + " pipes: z");
        }
    }

    @Test
    void coresStayInsideTheBlockAndNeverTouch() {
        for (int count = 1; count <= 6; count++) {
            List<Box> cores = PipeGeometry.layout(count).stream().map(PipeGeometry::core).toList();
            for (int i = 0; i < cores.size(); i++) {
                Box core = cores.get(i);
                assertTrue(core.minX() >= 0 && core.maxX() <= 16, count + " pipes: x inside block");
                assertTrue(core.minY() >= 0 && core.maxY() <= 16, count + " pipes: y inside block");
                assertTrue(core.minZ() >= 0 && core.maxZ() <= 16, count + " pipes: z inside block");
                for (int j = i + 1; j < cores.size(); j++) {
                    assertTrue(gap(core, cores.get(j)) >= 1.0 - 1e-6,
                            count + " pipes: " + i + " and " + j + " are too close");
                }
            }
        }
    }

    @Test
    void straightRunsAreCleanAlongEveryAxis() {
        // Looking along an axis, any two pipes must be at least a pixel apart on one visible axis.
        for (int count = 2; count <= 6; count++) {
            List<PipeGeometry.Slot> slots = PipeGeometry.layout(count);
            for (Face face : Face.values()) {
                for (int i = 0; i < slots.size(); i++) {
                    for (int j = i + 1; j < slots.size(); j++) {
                        Box a = PipeGeometry.arm(slots.get(i), face, false);
                        Box b = PipeGeometry.arm(slots.get(j), face, false);
                        assertFalse(a.overlaps(b), count + " pipes: arms " + i + "," + j + " toward " + face);
                        assertTrue(viewGap(PipeGeometry.core(slots.get(i)), PipeGeometry.core(slots.get(j)), face) >= 1.0 - 1e-6,
                                count + " pipes: " + i + " and " + j + " seen along " + face);
                    }
                }
            }
        }
    }

    @Test
    void anArmNeverRunsThroughAnotherPipesCore() {
        for (int count = 2; count <= 6; count++) {
            List<PipeGeometry.Slot> slots = PipeGeometry.layout(count);
            for (int i = 0; i < slots.size(); i++) {
                for (int j = 0; j < slots.size(); j++) {
                    if (i == j) {
                        continue;
                    }
                    for (Face face : Face.values()) {
                        assertFalse(PipeGeometry.arm(slots.get(i), face, false).overlaps(PipeGeometry.core(slots.get(j))),
                                count + " pipes: arm of " + i + " toward " + face + " hits the core of " + j);
                    }
                }
            }
        }
    }

    @Test
    void bundlesOfUpToThreePipesCanTurnAnyCornerWithoutCrossing() {
        for (int count = 2; count <= 3; count++) {
            List<PipeGeometry.Slot> slots = PipeGeometry.layout(count);
            for (int i = 0; i < slots.size(); i++) {
                for (int j = 0; j < slots.size(); j++) {
                    for (Face first : Face.values()) {
                        for (Face second : Face.values()) {
                            if (i == j) {
                                continue;
                            }
                            assertFalse(PipeGeometry.arm(slots.get(i), first, false)
                                            .overlaps(PipeGeometry.arm(slots.get(j), second, false)),
                                    count + " pipes: " + i + " toward " + first + " crosses " + j + " toward " + second);
                        }
                    }
                }
            }
        }
    }

    /** The clearest gap between two boxes on any one axis; negative when they overlap on all. */
    private static double gap(Box a, Box b) {
        double gapX = Math.max(a.minX() - b.maxX(), b.minX() - a.maxX());
        double gapY = Math.max(a.minY() - b.maxY(), b.minY() - a.maxY());
        double gapZ = Math.max(a.minZ() - b.maxZ(), b.minZ() - a.maxZ());
        return Math.max(gapX, Math.max(gapY, gapZ));
    }

    /** The gap between two boxes as seen looking along a face's axis, so that axis is ignored. */
    private static double viewGap(Box a, Box b, Face face) {
        double gapX = Math.max(a.minX() - b.maxX(), b.minX() - a.maxX());
        double gapY = Math.max(a.minY() - b.maxY(), b.minY() - a.maxY());
        double gapZ = Math.max(a.minZ() - b.maxZ(), b.minZ() - a.maxZ());
        return switch (face) {
            case NORTH, SOUTH -> Math.max(gapX, gapY);
            case EAST, WEST -> Math.max(gapY, gapZ);
            case UP, DOWN -> Math.max(gapX, gapZ);
        };
    }
    @Test
    void aSinglePipeIsCenteredAndFourPixelsWide() {
        Box core = PipeGeometry.core(PipeGeometry.layout(1).get(0));
        assertEquals(6, core.minX(), EPSILON);
        assertEquals(10, core.maxX(), EPSILON);
        assertEquals(6, core.minY(), EPSILON);
        assertEquals(10, core.maxZ(), EPSILON);
    }

    @Test
    void armsRunFromTheCoreToTheFaceOrTheConnector() {
        PipeGeometry.Slot slot = PipeGeometry.layout(1).get(0);
        Box north = PipeGeometry.arm(slot, Face.NORTH, false);
        assertEquals(0, north.minZ(), EPSILON);
        assertEquals(6, north.maxZ(), EPSILON);

        Box eastToMachine = PipeGeometry.arm(slot, Face.EAST, true);
        assertEquals(10, eastToMachine.minX(), EPSILON);
        assertEquals(14, eastToMachine.maxX(), EPSILON);
    }

    @Test
    void theConnectorMatchesTheLayoutBoundingBox() {
        // One pipe: 4 by 4 pixels, 2 deep.
        Box one = PipeGeometry.connector(1, Face.NORTH);
        assertEquals(4, one.maxX() - one.minX(), EPSILON);
        assertEquals(4, one.maxY() - one.minY(), EPSILON);
        assertEquals(2, one.maxZ() - one.minZ(), EPSILON);

        // Two pipes sit diagonally, so the connector covers both: 9 by 9.
        Box two = PipeGeometry.connector(2, Face.NORTH);
        assertEquals(9, two.maxX() - two.minX(), EPSILON);
        assertEquals(9, two.maxY() - two.minY(), EPSILON);

        // For any bundle, the connector covers the cross-section of every pipe.
        for (int count = 1; count <= 6; count++) {
            for (Face face : Face.values()) {
                Box connector = PipeGeometry.connector(count, face);
                for (PipeGeometry.Slot slot : PipeGeometry.layout(count)) {
                    Box core = PipeGeometry.core(slot);
                    // Only the two axes across the face matter; the third is the connector's depth.
                    boolean coversX = connector.minX() <= core.minX() + EPSILON && connector.maxX() >= core.maxX() - EPSILON;
                    boolean coversY = connector.minY() <= core.minY() + EPSILON && connector.maxY() >= core.maxY() - EPSILON;
                    boolean coversZ = connector.minZ() <= core.minZ() + EPSILON && connector.maxZ() >= core.maxZ() - EPSILON;
                    boolean covers = switch (face) {
                        case NORTH, SOUTH -> coversX && coversY;
                        case EAST, WEST -> coversY && coversZ;
                        case UP, DOWN -> coversX && coversZ;
                    };
                    assertTrue(covers, count + " pipes: connector on " + face + " misses a pipe");
                }
            }
        }

        // A connector on the east face sits against that face.
        Box east = PipeGeometry.connector(1, Face.EAST);
        assertEquals(16, east.maxX(), EPSILON);
        assertEquals(14, east.minX(), EPSILON);
    }

    // --- BundleState ------------------------------------------------------------------------

    @Test
    void blockingAFaceRemovesItsConnection() {
        BundleState state = new BundleState();
        state.addPipe(PipeType.FLUID);
        state.setConnection(0, Face.NORTH, true, false);
        assertTrue(state.isConnected(0, Face.NORTH));

        state.setMode(0, Face.NORTH, ConnectionMode.BLOCKED);
        assertFalse(state.isConnected(0, Face.NORTH));

        // The world offering a connection again does not override the block.
        state.setConnection(0, Face.NORTH, true, false);
        assertFalse(state.isConnected(0, Face.NORTH));
    }

    @Test
    void theWrenchCyclesThroughEveryModeAndWraps() {
        ConnectionMode mode = ConnectionMode.OPEN;
        for (int i = 0; i < ConnectionMode.values().length; i++) {
            mode = mode.next();
        }
        assertEquals(ConnectionMode.OPEN, mode);
    }

    @Test
    void removingAPipeClearsItsLane() {
        BundleState state = new BundleState();
        state.addPipe(PipeType.ITEM);
        state.setConnection(1, Face.UP, true, true);
        state.removeLane(1);
        assertTrue(state.set().isEmpty());
        assertFalse(state.isConnected(1, Face.UP));
        assertFalse(state.isExternal(1, Face.UP));
    }

    @Test
    void anExternalConnectionAddsAConnectorBox() {
        BundleState state = new BundleState();
        state.addPipe(PipeType.ENERGY);
        state.setConnection(PipeType.ENERGY.lane(), Face.WEST, true, true);
        assertEquals(1, state.connectorBoxes().size());
        // core + one arm + connector
        assertEquals(3, state.allBoxes().size());

        state.setConnection(PipeType.ENERGY.lane(), Face.WEST, true, false);
        assertTrue(state.connectorBoxes().isEmpty());
    }

    @Test
    void theNearestPipeToAPointIsFoundEvenWhenTheAimIsOff() {
        BundleState state = new BundleState();
        state.addPipe(PipeType.FLUID);
        state.addPipe(PipeType.ITEM);
        // Two pipes: fluid on the left (x 1..5 after centering at -3), item on the right.
        assertEquals(PipeType.FLUID, state.nearestPipe(2, 8, 8));
        assertEquals(PipeType.ITEM, state.nearestPipe(14, 8, 8));
        // Aiming a little above both still picks the closer one.
        assertEquals(PipeType.ITEM, state.nearestPipe(12, 12, 8));
        assertNull(new BundleState().nearestPipe(8, 8, 8));
    }

    @Test
    void modelKeysChangeWithTheSetAndTheConnections() {
        BundleState a = new BundleState();
        a.addPipe(PipeType.FLUID);
        BundleState b = new BundleState();
        b.addPipe(PipeType.FLUID);
        assertEquals(a.modelKey(), b.modelKey());

        b.setConnection(0, Face.SOUTH, true, false);
        assertNotEquals(a.modelKey(), b.modelKey());

        // A mode that does not change what is drawn does not change the key.
        BundleState before = new BundleState();
        before.addPipe(PipeType.FLUID);
        a.setMode(0, Face.EAST, ConnectionMode.EXPORT);
        assertEquals(before.modelKey(), a.modelKey());
    }

    @Test
    void serializingRoundTrips() {
        BundleState state = new BundleState();
        state.addPipe(PipeType.FLUID);
        state.addPipe(PipeType.GAS);
        state.setConnection(0, Face.NORTH, true, false);
        state.setConnection(4, Face.EAST, true, true);
        state.setMode(4, Face.DOWN, ConnectionMode.EXPORT);

        BundleState copy = BundleState.deserialize(state.serialize());
        assertEquals(state, copy);
        assertEquals(ConnectionMode.EXPORT, copy.mode(4, Face.DOWN));
    }

    @Test
    void damagedSavedDataGivesAnEmptyBundle() {
        assertTrue(BundleState.deserialize(null).set().isEmpty());
        assertTrue(BundleState.deserialize(new int[3]).set().isEmpty());
    }
}
