package com.itmecasey.aeroflux.core.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SaveSchemaTest {
    @Test
    void everyKindHasAPositiveCurrentVersionAndAName() {
        for (SaveSchema.Kind kind : SaveSchema.Kind.values()) {
            assertTrue(kind.current() >= 1, kind.name());
            assertFalse(kind.id().isEmpty(), kind.name());
        }
    }

    @Test
    void missingZeroAndNegativeVersionsAreUnversioned() {
        for (int stored : new int[] {0, -1, Integer.MIN_VALUE}) {
            assertEquals(0, SaveSchema.effectiveVersion(stored));
            assertEquals(SaveSchema.Status.UNVERSIONED, SaveSchema.inspect(SaveSchema.Kind.TANK, stored));
            assertTrue(SaveSchema.needsMigration(SaveSchema.Kind.TANK, stored));
        }
    }

    @Test
    void theCurrentVersionNeedsNothing() {
        for (SaveSchema.Kind kind : SaveSchema.Kind.values()) {
            assertEquals(SaveSchema.Status.CURRENT, SaveSchema.inspect(kind, kind.current()));
            assertFalse(SaveSchema.needsMigration(kind, kind.current()));
        }
    }

    @Test
    void olderAndNewerVersionsAreTold() {
        assertEquals(SaveSchema.Status.OLDER, SaveSchema.inspect(SaveSchema.Kind.MACHINE, 2));
        assertTrue(SaveSchema.needsMigration(SaveSchema.Kind.MACHINE, 1));
        int future = SaveSchema.Kind.MACHINE.current() + 1;
        assertEquals(SaveSchema.Status.NEWER, SaveSchema.inspect(SaveSchema.Kind.MACHINE, future));
        assertFalse(SaveSchema.needsMigration(SaveSchema.Kind.MACHINE, future));
    }

    @Test
    void theMachineChainReachesTheCurrentVersionFromEveryOldOne() {
        // Migrating an empty-ish inventory from each old version must not throw for a missing step.
        for (int from = 0; from <= SaveSchema.Kind.MACHINE.current(); from++) {
            MachineSaveMigrations.Inventory<String> saved = new MachineSaveMigrations.Inventory<>(List.of("a", "x"), 1, 1, "");
            MachineSaveMigrations.migrate(saved, from);
        }
    }

    @Test
    void aChainRunsStepsInOrder() {
        MigrationChain<List<String>> chain = MigrationChain.<List<String>>builder()
                .step(0, log -> with(log, "0->1"))
                .step(1, log -> with(log, "1->2"))
                .step(2, log -> with(log, "2->3"))
                .build();
        assertEquals(List.of("0->1", "1->2", "2->3"), chain.migrate(List.of(), 0, 3));
        assertEquals(List.of("1->2", "2->3"), chain.migrate(List.of(), 1, 3));
        assertEquals(List.of("1->2"), chain.migrate(List.of(), 1, 2));
    }

    @Test
    void aChainLeavesCurrentAndNewerDataAlone() {
        MigrationChain<List<String>> chain = MigrationChain.<List<String>>builder().step(0, log -> with(log, "0->1")).build();
        assertEquals(List.of(), chain.migrate(List.of(), 1, 1));
        assertEquals(List.of(), chain.migrate(List.of(), 5, 1));
    }

    @Test
    void aMissingStepIsAnError() {
        MigrationChain<List<String>> chain = MigrationChain.<List<String>>builder().step(0, log -> with(log, "0->1")).build();
        assertTrue(chain.covers(0, 1));
        assertFalse(chain.covers(0, 2));
        assertThrows(IllegalStateException.class, () -> chain.migrate(List.of(), 0, 2));
    }

    @Test
    void twoStepsForOneVersionAreRefused() {
        MigrationChain.Builder<String> builder = MigrationChain.<String>builder().step(0, data -> data);
        assertThrows(IllegalStateException.class, () -> builder.step(0, data -> data));
    }

    @Test
    void aNegativeStartingVersionCountsAsZero() {
        MigrationChain<List<String>> chain = MigrationChain.<List<String>>builder().step(0, log -> with(log, "0->1")).build();
        assertEquals(List.of("0->1"), chain.migrate(List.of(), -4, 1));
    }

    private static List<String> with(List<String> log, String entry) {
        List<String> next = new ArrayList<>(log);
        next.add(entry);
        return next;
    }
}
