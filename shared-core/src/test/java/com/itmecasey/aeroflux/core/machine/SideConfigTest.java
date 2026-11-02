package com.itmecasey.aeroflux.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SideConfigTest {
    private static SideConfigs furnaceSides() {
        return new SideConfigs(EnumSet.of(SideResource.ITEMS, SideResource.FLUID));
    }

    // --- Face rules --------------------------------------------------------------------------

    @Test
    void inputsOnlyTakeThingsInAndOutputsOnlyGiveThemOut() {
        for (ConnectionMode mode : ConnectionMode.values()) {
            boolean in = mode == ConnectionMode.IMPORT || mode == ConnectionMode.OPEN;
            boolean out = mode == ConnectionMode.EXPORT || mode == ConnectionMode.OPEN;
            assertEquals(in, FaceRules.canInsert(PortRole.INPUT, mode), "insert into input, " + mode);
            assertEquals(out, FaceRules.canExtract(PortRole.OUTPUT, mode), "extract from output, " + mode);
            // The role beats the mode: automation never empties an input or fills an output.
            assertFalse(FaceRules.canExtract(PortRole.INPUT, mode), "extract from input, " + mode);
            assertFalse(FaceRules.canInsert(PortRole.OUTPUT, mode), "insert into output, " + mode);
        }
    }

    @Test
    void aBufferFollowsTheFaceAlone() {
        for (ConnectionMode mode : ConnectionMode.values()) {
            assertEquals(mode == ConnectionMode.IMPORT || mode == ConnectionMode.OPEN, FaceRules.canInsert(PortRole.BUFFER, mode));
            assertEquals(mode == ConnectionMode.EXPORT || mode == ConnectionMode.OPEN, FaceRules.canExtract(PortRole.BUFFER, mode));
        }
    }

    @Test
    void clickingCyclesThroughAllFiveModesAndBackAgain() {
        ConnectionMode mode = ConnectionMode.IMPORT;
        Set<ConnectionMode> seen = EnumSet.noneOf(ConnectionMode.class);
        for (int i = 0; i < 5; i++) {
            seen.add(mode);
            mode = SideConfig.cycle(mode, false);
        }
        assertEquals(ConnectionMode.IMPORT, mode);
        assertEquals(5, seen.size());
        for (ConnectionMode each : ConnectionMode.values()) {
            assertEquals(each, SideConfig.cycle(SideConfig.cycle(each, false), true));
        }
        assertEquals(ConnectionMode.EXPORT, SideConfig.cycle(ConnectionMode.IMPORT, false));
        assertEquals(ConnectionMode.BLOCKED, SideConfig.cycle(ConnectionMode.IMPORT, true));
    }

    @Test
    void onlyBlockedHidesAFace() {
        for (ConnectionMode mode : ConnectionMode.values()) {
            assertEquals(mode != ConnectionMode.BLOCKED, FaceRules.exposed(mode));
        }
        assertFalse(FaceRules.canInsert(PortRole.INPUT, ConnectionMode.OFF));
        assertFalse(FaceRules.canExtract(PortRole.OUTPUT, ConnectionMode.OFF));
    }

    // --- Defaults and packing ----------------------------------------------------------------

    @Test
    void itemsGoInAtTheTopAndOutAtTheBottom() {
        SideConfig items = SideConfig.defaultFor(SideResource.ITEMS);
        assertEquals(ConnectionMode.IMPORT, items.mode(Face.UP));
        assertEquals(ConnectionMode.EXPORT, items.mode(Face.DOWN));
        for (Face side : new Face[] {Face.NORTH, Face.SOUTH, Face.EAST, Face.WEST}) {
            assertEquals(ConnectionMode.OPEN, items.mode(side));
        }
    }

    @Test
    void fluidOnlyGoesInByDefault() {
        SideConfig fluid = SideConfig.defaultFor(SideResource.FLUID);
        for (Face face : Face.values()) {
            assertEquals(ConnectionMode.IMPORT, fluid.mode(face));
        }
    }

    @Test
    void packingRoundTripsEveryConfiguration() {
        // 5^6 = 15,625 configurations; check each one comes back and none shares a number.
        Set<Integer> seen = new HashSet<>();
        for (int packed = 0; packed < 15_625; packed++) {
            SideConfig config = SideConfig.unpack(packed);
            assertEquals(packed, config.pack());
            assertTrue(seen.add(packed));
        }
    }

    @Test
    void packedValuesFitASignedSixteenBitSlot() {
        SideConfig all = new SideConfig(ConnectionMode.BLOCKED);
        assertTrue(all.pack() <= Short.MAX_VALUE);
    }

    @Test
    void unpackAcceptsDamagedNumbers() {
        // Negative and oversized numbers still give some valid configuration instead of crashing.
        for (int value : new int[] {-1, -99_999, 15_625, 1_000_000, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            SideConfig config = SideConfig.unpack(value);
            for (Face face : Face.values()) {
                assertTrue(config.mode(face) != null);
            }
        }
    }

    @Test
    void copiesAreIndependent() {
        SideConfig original = SideConfig.defaultFor(SideResource.ITEMS);
        SideConfig copy = original.copy();
        copy.set(Face.NORTH, ConnectionMode.BLOCKED);
        assertEquals(ConnectionMode.OPEN, original.mode(Face.NORTH));
        assertNotEquals(original, copy);
    }

    // --- Commands ----------------------------------------------------------------------------

    @Test
    void everyCommandSurvivesEncoding() {
        for (SideResource resource : SideResource.values()) {
            assertEquals(SideCommand.reset(resource), SideCommand.decode(SideCommand.reset(resource).encode()));
            for (ConnectionMode mode : ConnectionMode.values()) {
                SideCommand all = SideCommand.allTo(resource, mode);
                assertEquals(all, SideCommand.decode(all.encode()));
                for (Face face : Face.values()) {
                    SideCommand set = SideCommand.set(resource, face, mode);
                    assertEquals(set, SideCommand.decode(set.encode()));
                }
            }
            for (Face face : Face.values()) {
                SideCommand copy = SideCommand.copyToAll(resource, face);
                assertEquals(copy, SideCommand.decode(copy.encode()));
            }
        }
    }

    @Test
    void noTwoCommandsShareANumber() {
        Set<Integer> seen = new HashSet<>();
        int count = 0;
        for (SideResource resource : SideResource.values()) {
            count += add(seen, SideCommand.reset(resource));
            for (ConnectionMode mode : ConnectionMode.values()) {
                count += add(seen, SideCommand.allTo(resource, mode));
                for (Face face : Face.values()) {
                    count += add(seen, SideCommand.set(resource, face, mode));
                }
            }
            for (Face face : Face.values()) {
                count += add(seen, SideCommand.copyToAll(resource, face));
            }
        }
        assertEquals(count, seen.size());
    }

    private static int add(Set<Integer> seen, SideCommand command) {
        seen.add(command.encode());
        return 1;
    }

    @Test
    void numbersThatAreNotCommandsAreRefused() {
        for (int id : new int[] {-1, 150, 199, 225, 299, 330, 399, 405, 406, 1_000, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            assertNull(SideCommand.decode(id), "id " + id);
        }
    }

    @Test
    void settingAFaceChangesOnlyThatFace() {
        SideConfigs sides = furnaceSides();
        assertTrue(sides.apply(SideCommand.set(SideResource.ITEMS, Face.NORTH, ConnectionMode.BLOCKED)));
        assertEquals(ConnectionMode.BLOCKED, sides.mode(SideResource.ITEMS, Face.NORTH));
        assertEquals(ConnectionMode.OPEN, sides.mode(SideResource.ITEMS, Face.SOUTH));
        assertEquals(ConnectionMode.IMPORT, sides.mode(SideResource.FLUID, Face.NORTH));
    }

    @Test
    void aClickThatChangesNothingReportsNoChange() {
        SideConfigs sides = furnaceSides();
        assertFalse(sides.apply(SideCommand.set(SideResource.ITEMS, Face.UP, ConnectionMode.IMPORT)));
        assertFalse(sides.apply(SideCommand.reset(SideResource.ITEMS)));
    }

    @Test
    void allOffAndCopyToAllAndResetWork() {
        SideConfigs sides = furnaceSides();
        assertTrue(sides.apply(SideCommand.allTo(SideResource.ITEMS, ConnectionMode.OFF)));
        for (Face face : Face.values()) {
            assertEquals(ConnectionMode.OFF, sides.mode(SideResource.ITEMS, face));
        }
        sides.apply(SideCommand.set(SideResource.ITEMS, Face.EAST, ConnectionMode.EXPORT));
        assertTrue(sides.apply(SideCommand.copyToAll(SideResource.ITEMS, Face.EAST)));
        for (Face face : Face.values()) {
            assertEquals(ConnectionMode.EXPORT, sides.mode(SideResource.ITEMS, face));
        }
        assertTrue(sides.apply(SideCommand.reset(SideResource.ITEMS)));
        assertEquals(SideConfig.defaultFor(SideResource.ITEMS).pack(), sides.packed(SideResource.ITEMS));
    }

    @Test
    void resourcesTheMachineDoesNotUseCannotBeChanged() {
        SideConfigs sides = furnaceSides();
        assertFalse(sides.uses(SideResource.GAS));
        assertFalse(sides.apply(SideCommand.allTo(SideResource.GAS, ConnectionMode.OPEN)));
        assertEquals(ConnectionMode.BLOCKED, sides.mode(SideResource.GAS, Face.UP));
        assertEquals(0, sides.packed(SideResource.GAS));
        sides.restore(SideResource.GAS, 123);
        assertEquals(0, sides.packed(SideResource.GAS));
    }

    @Test
    void restoreBringsBackWhatPackedSaved() {
        SideConfigs sides = furnaceSides();
        sides.apply(SideCommand.set(SideResource.FLUID, Face.WEST, ConnectionMode.EXPORT));
        sides.apply(SideCommand.set(SideResource.ITEMS, Face.DOWN, ConnectionMode.BLOCKED));
        SideConfigs loaded = furnaceSides();
        for (SideResource resource : sides.used()) {
            loaded.restore(resource, sides.packed(resource));
        }
        assertEquals(sides, loaded);
        assertEquals(sides, sides.copy());
    }

    // --- Face names --------------------------------------------------------------------------

    @Test
    void aNorthFacingMachineHasItsLeftOnTheEast() {
        // Standing in front of it, looking south at its front, east is on your left.
        assertEquals(FaceLabel.FRONT, FaceLabel.of(Face.NORTH, Face.NORTH));
        assertEquals(FaceLabel.BACK, FaceLabel.of(Face.NORTH, Face.SOUTH));
        assertEquals(FaceLabel.LEFT, FaceLabel.of(Face.NORTH, Face.EAST));
        assertEquals(FaceLabel.RIGHT, FaceLabel.of(Face.NORTH, Face.WEST));
    }

    @Test
    void topAndBottomDoNotDependOnFacing() {
        for (Face facing : new Face[] {Face.NORTH, Face.EAST, Face.SOUTH, Face.WEST}) {
            assertEquals(FaceLabel.TOP, FaceLabel.of(facing, Face.UP));
            assertEquals(FaceLabel.BOTTOM, FaceLabel.of(facing, Face.DOWN));
        }
    }

    @Test
    void labelsAndFacesConvertBothWays() {
        for (Face facing : new Face[] {Face.NORTH, Face.EAST, Face.SOUTH, Face.WEST}) {
            Set<Face> seen = EnumSet.noneOf(Face.class);
            for (FaceLabel label : FaceLabel.values()) {
                Face face = label.faceFor(facing);
                assertEquals(label, FaceLabel.of(facing, face));
                seen.add(face);
            }
            assertEquals(6, seen.size(), "each label names a different face");
        }
    }
}
