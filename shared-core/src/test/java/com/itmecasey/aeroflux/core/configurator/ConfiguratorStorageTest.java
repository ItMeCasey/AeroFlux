package com.itmecasey.aeroflux.core.configurator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.RunPlanner;
import com.itmecasey.aeroflux.core.machine.RedstoneMode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Presets, undo and the area box: the parts of the Configurator that are about storage and geometry. */
class ConfiguratorStorageTest {
    private static MachineSettings furnace(RedstoneMode mode) {
        MachineSettingsTest.FakeMachine machine = MachineSettingsTest.FakeMachine.furnace(Face.NORTH);
        machine.redstone = mode;
        return MachineSettings.capture(machine);
    }

    // --- Presets -----------------------------------------------------------------------------

    @Test
    void aNewConfiguratorHasEightEmptySlotsAndSelectsTheFirst() {
        ConfiguratorPresets presets = new ConfiguratorPresets();
        assertEquals(0, presets.selected());
        for (int slot = 0; slot < ConfiguratorPresets.SLOTS; slot++) {
            assertNull(presets.get(slot));
        }
        assertNull(presets.get(-1));
        assertNull(presets.get(8));
    }

    @Test
    void selectingIgnoresNumbersOutsideTheSlots() {
        ConfiguratorPresets presets = new ConfiguratorPresets();
        presets.select(5);
        presets.select(8);
        presets.select(-1);
        assertEquals(5, presets.selected());
    }

    @Test
    void storingIntoANamedSlotKeepsItsName() {
        ConfiguratorPresets presets = new ConfiguratorPresets();
        presets.store(2, "Furnace", furnace(RedstoneMode.ALWAYS));
        assertTrue(presets.rename(2, "Ore line"));
        presets.store(2, "Grinder", furnace(RedstoneMode.HIGH));
        assertEquals("Ore line", presets.get(2).name());
        assertEquals(RedstoneMode.HIGH, presets.get(2).settings().redstoneMode());
    }

    @Test
    void renameAndClearRefuseEmptySlots() {
        ConfiguratorPresets presets = new ConfiguratorPresets();
        assertFalse(presets.rename(0, "Anything"));
        assertFalse(presets.clear(0));
        presets.store(0, "Furnace", furnace(RedstoneMode.ALWAYS));
        assertFalse(presets.rename(0, "   "), "a blank name is refused");
        assertTrue(presets.clear(0));
        assertNull(presets.get(0));
    }

    @Test
    void namesAreCleaned() {
        assertEquals("Ore line", ConfiguratorPresets.cleanName("  Ore \t  line  "));
        // Without its section sign a formatting code is just a letter.
        assertEquals("credtext", ConfiguratorPresets.cleanName("§cred\u0000text"));
        assertEquals(ConfiguratorPresets.NAME_MAX, ConfiguratorPresets.cleanName("x".repeat(100)).length());
        assertEquals("", ConfiguratorPresets.cleanName(null));
        assertFalse(ConfiguratorPresets.cleanName("a\tb").contains("\t"));
    }

    @Test
    void presetsSurviveTheTextForm() {
        ConfiguratorPresets presets = new ConfiguratorPresets();
        presets.store(0, "Smelting", furnace(RedstoneMode.LOW));
        presets.store(7, "Last", furnace(RedstoneMode.PULSE));
        presets.select(7);
        ConfiguratorPresets read = ConfiguratorPresets.decode(presets.encode(), presets.selected());
        assertEquals(presets, read);
        assertEquals("Last", read.current().name());
    }

    @Test
    void unreadableLinesLeaveTheirSlotEmpty() {
        List<String> lines = new ArrayList<>(List.of("garbage", "Name\tv1;kind=furnace", "Bad\tv7;kind=x"));
        ConfiguratorPresets read = ConfiguratorPresets.decode(lines, 99);
        assertNull(read.get(0));
        assertEquals("Name", read.get(1).name());
        assertNull(read.get(2));
        assertEquals(0, read.selected());
        assertNull(ConfiguratorPresets.decode(null, 0).get(0));
    }

    // --- Undo --------------------------------------------------------------------------------

    @Test
    void anUndoLastsOneMinuteInItsOwnDimension() {
        PasteUndo undo = new PasteUndo("minecraft:overworld",
                List.of(new PasteUndo.Entry(new RunPlanner.Pos(1, 2, 3), furnace(RedstoneMode.ALWAYS))), 1_000);
        assertTrue(undo.usable("minecraft:overworld", 1_000));
        assertTrue(undo.usable("minecraft:overworld", 1_000 + PasteUndo.LIFETIME));
        assertFalse(undo.usable("minecraft:overworld", 1_001 + PasteUndo.LIFETIME));
        assertFalse(undo.usable("minecraft:the_nether", 1_010));
        assertFalse(undo.usable("minecraft:overworld", 999), "time that ran backward means a different world");
        assertFalse(new PasteUndo("minecraft:overworld", List.of(), 0).usable("minecraft:overworld", 0));
    }

    // --- Area --------------------------------------------------------------------------------

    @Test
    void theAreaSpansBothCornersInAnyOrder() {
        ConfigArea area = ConfigArea.between(new RunPlanner.Pos(5, 10, -2), new RunPlanner.Pos(3, 8, 1));
        assertEquals(new ConfigArea(3, 8, -2, 5, 10, 1), area);
        assertEquals(3 * 3 * 4, area.volume());
        assertEquals(area.volume(), area.positions().size());
        assertTrue(area.contains(new RunPlanner.Pos(4, 9, 0)));
        assertFalse(area.contains(new RunPlanner.Pos(6, 9, 0)));
        assertEquals(new RunPlanner.Pos(3, 8, -2), area.positions().get(0));
    }

    @Test
    void aSingleBlockIsAnArea() {
        ConfigArea area = ConfigArea.between(new RunPlanner.Pos(0, 0, 0), new RunPlanner.Pos(0, 0, 0));
        assertEquals(1, area.volume());
        assertFalse(area.tooLarge());
    }

    @Test
    void sidesLongerThanTheLimitAreTooLarge() {
        RunPlanner.Pos origin = new RunPlanner.Pos(0, 0, 0);
        assertFalse(ConfigArea.between(origin, new RunPlanner.Pos(ConfigArea.MAX_SIDE - 1, 0, 0)).tooLarge());
        assertTrue(ConfigArea.between(origin, new RunPlanner.Pos(ConfigArea.MAX_SIDE, 0, 0)).tooLarge());
        assertTrue(ConfigArea.between(origin, new RunPlanner.Pos(0, -ConfigArea.MAX_SIDE, 0)).tooLarge());
    }

    @Test
    void theMachineCountMustBeWithinTheLimit() {
        assertFalse(ConfigArea.checkCount(0, 64));
        assertTrue(ConfigArea.checkCount(1, 64));
        assertTrue(ConfigArea.checkCount(64, 64));
        assertFalse(ConfigArea.checkCount(65, 64));
    }

    @Test
    void theOutlineTracesAllTwelveEdges() {
        ConfigArea area = ConfigArea.between(new RunPlanner.Pos(0, 0, 0), new RunPlanner.Pos(1, 0, 0));
        List<ConfigArea.Point> points = area.outline(0.5);
        // Edges 2, 1 and 1 blocks long at half a block: 5, 3 and 3 points, four edges each.
        assertEquals(4 * 5 + 4 * 3 + 4 * 3, points.size());
        for (double[] corner : new double[][] {{0, 0, 0}, {2, 1, 1}, {2, 0, 1}, {0, 1, 0}}) {
            assertTrue(points.contains(new ConfigArea.Point(corner[0], corner[1], corner[2])));
        }
        for (ConfigArea.Point point : points) {
            int onFace = (point.x() == 0 || point.x() == 2 ? 1 : 0) + (point.y() == 0 || point.y() == 1 ? 1 : 0)
                    + (point.z() == 0 || point.z() == 1 ? 1 : 0);
            assertTrue(onFace >= 2, "every point lies on an edge: " + point);
        }
    }

    @Test
    void configuratorModesCycleAndReadBack() {
        assertEquals(ConfiguratorMode.AREA, ConfiguratorMode.PASTE.next());
        assertEquals(ConfiguratorMode.PASTE, ConfiguratorMode.AREA.next());
        assertEquals(ConfiguratorMode.AREA, ConfiguratorMode.byId("area"));
        assertEquals(ConfiguratorMode.PASTE, ConfiguratorMode.byId("bogus"));
    }
}
