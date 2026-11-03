package com.itmecasey.aeroflux.core.save;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class MachineSaveMigrationsTest {
    private static MachineSaveMigrations.Inventory<String> saved(List<String> stacks, int inputs, int outputs) {
        return new MachineSaveMigrations.Inventory<>(stacks, inputs, outputs, "-");
    }

    @Test
    void aVersionOneSifterGainsItsMeshSlot() {
        // Before the mesh slot: one input and six outputs. Now: two inputs (block, mesh) and six outputs.
        List<String> old = List.of("gravel", "o0", "o1", "-", "-", "o4", "-");
        MachineSaveMigrations.Inventory<String> migrated = MachineSaveMigrations.migrate(saved(old, 2, 6), 1);
        assertEquals(List.of("gravel", "-", "o0", "o1", "-", "-", "o4", "-"), migrated.stacks());
    }

    @Test
    void dataFromBeforeVersioningTakesTheSamePath() {
        List<String> old = List.of("gravel", "o0", "-", "-", "-", "-", "o5");
        assertEquals(List.of("gravel", "-", "o0", "-", "-", "-", "-", "o5"),
                MachineSaveMigrations.migrate(saved(old, 2, 6), 0).stacks());
    }

    @Test
    void currentDataIsUntouched() {
        List<String> current = List.of("gravel", "mesh", "o0", "-", "-", "-", "-", "o5");
        assertEquals(current, MachineSaveMigrations.migrate(saved(current, 2, 6), 3).stacks());
        assertEquals(current, MachineSaveMigrations.migrate(saved(current, 2, 6), 2).stacks());
    }

    @Test
    void anOldSaveWithTheRightSizeKeepsEverythingInPlace() {
        // A furnace never changed shape, so version 1 and 0 saves are already right.
        List<String> furnace = List.of("ore", "ingot");
        assertEquals(furnace, MachineSaveMigrations.migrate(saved(furnace, 1, 1), 1).stacks());
        assertEquals(furnace, MachineSaveMigrations.migrate(saved(furnace, 1, 1), 0).stacks());
    }

    @Test
    void aSaveWithTheWrongSizeIsLinedUpEvenAtTheCurrentVersion() {
        // Nothing records how many inputs a save had, so the outputs are counted from the end: two
        // slots saved for a machine with two outputs are read as two outputs.
        List<String> damaged = List.of("in", "out-a");
        MachineSaveMigrations.Inventory<String> fixed = MachineSaveMigrations.migrate(saved(damaged, 2, 2), 3);
        assertEquals(List.of("-", "-", "in", "out-a"), fixed.stacks());
    }

    @Test
    void extraSavedInputsAreDroppedNotShifted() {
        // Saved with three inputs and one output; the machine now has two inputs and one output.
        List<String> wide = List.of("a", "b", "c", "out");
        List<String> fixed = MachineSaveMigrations.addedInputSlots(saved(wide, 2, 1)).stacks();
        assertEquals(List.of("a", "b", "out"), fixed);
    }

    @Test
    void anEmptySaveBecomesAnEmptyInventory() {
        List<String> fixed = MachineSaveMigrations.migrate(saved(List.of(), 2, 2), 1).stacks();
        assertEquals(List.of("-", "-", "-", "-"), fixed);
    }

    @Test
    void migratingTwiceChangesNothingMore() {
        List<String> old = List.of("gravel", "o0", "o1", "-", "-", "o4", "-");
        MachineSaveMigrations.Inventory<String> once = MachineSaveMigrations.migrate(saved(old, 2, 6), 1);
        MachineSaveMigrations.Inventory<String> twice = MachineSaveMigrations.migrate(once, SaveSchema.Kind.MACHINE.current());
        assertEquals(once.stacks(), twice.stacks());
    }
}
