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
    void layoutsMatchThePlan() {
        assertEquals(new PipeGeometry.Slot(0, 0), PipeGeometry.layout(1).get(0));

        List<PipeGeometry.Slot> two = PipeGeometry.layout(2);
        assertEquals(-3, two.get(0).u(), EPSILON);
        assertEquals(3, two.get(1).u(), EPSILON);

        // Three pipes: one on top, two below.
        List<PipeGeometry.Slot> three = PipeGeometry.layout(3);
        assertEquals(0, three.get(0).u(), EPSILON);
        assertEquals(3, three.get(0).v(), EPSILON);
        assertEquals(-3, three.get(1).v(), EPSILON);

        assertEquals(4, PipeGeometry.layout(4).size());
        assertEquals(6, PipeGeometry.layout(6).size());
    }

    @Test
    void theFirstRingPipeIsAtTheTopAndTheRestGoClockwise() {
        List<PipeGeometry.Slot> ring = PipeGeometry.layout(6);
        assertEquals(0, ring.get(0).u(), 1e-6);
        assertTrue(ring.get(0).v() > 0);
        assertTrue(ring.get(1).u() > 0, "the second pipe is to the right of the top");
    }

    @Test
    void pipesInAnyLayoutNeverTouchAndStayInsideTheBlock() {
        for (int count = 1; count <= 6; count++) {
            List<Box> cores = PipeGeometry.layout(count).stream().map(PipeGeometry::core).toList();
            for (int i = 0; i < cores.size(); i++) {
                Box core = cores.get(i);
                assertTrue(core.minX() >= 0 && core.maxX() <= 16, count + " pipes: x inside block");
                assertTrue(core.minY() >= 0 && core.maxY() <= 16, count + " pipes: y inside block");
                for (int j = i + 1; j < cores.size(); j++) {
                    Box other = cores.get(j);
                    // At least one pixel of clear space between two pipes.
                    double gapX = Math.max(core.minX() - other.maxX(), other.minX() - core.maxX());
                    double gapY = Math.max(core.minY() - other.maxY(), other.minY() - core.maxY());
                    assertTrue(Math.max(gapX, gapY) >= 1.0 - 1e-6,
                            count + " pipes: " + i + " and " + j + " are too close");
                }
            }
        }
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

        // Two pipes side by side: 10 by 4, as in the plan.
        Box two = PipeGeometry.connector(2, Face.NORTH);
        assertEquals(10, two.maxX() - two.minX(), EPSILON);
        assertEquals(4, two.maxY() - two.minY(), EPSILON);

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
