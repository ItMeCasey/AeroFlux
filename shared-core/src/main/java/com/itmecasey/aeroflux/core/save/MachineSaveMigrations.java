package com.itmecasey.aeroflux.core.save;

import java.util.ArrayList;
import java.util.List;

/**
 * The migration steps for a machine's saved inventory.
 *
 * <p>The one layout change so far is the Sifter's mesh slot: it gained a second input slot, so a
 * save from before has one slot too few, and every output slot sits one place earlier than it
 * does now. Version 1 is the first layout that was stamped with a number; version 3 added the owner,
 * which older data simply lacks, so that step changes nothing here.
 */
public final class MachineSaveMigrations {
    private MachineSaveMigrations() {
    }

    /**
     * A machine's slots as saved, with the layout the machine has now.
     *
     * @param stacks      the saved slots, inputs first and then outputs
     * @param inputSlots  how many input slots the machine has now
     * @param outputSlots how many output slots the machine has now
     * @param empty       the value of an empty slot
     */
    public record Inventory<T>(List<T> stacks, int inputSlots, int outputSlots, T empty) {
        /** The slot count the machine has now. */
        public int wanted() {
            return inputSlots + outputSlots;
        }
    }

    /**
     * Brings a saved inventory up to the current layout.
     *
     * @param storedVersion the version found with the data; 0 when there was none
     */
    public static <T> Inventory<T> migrate(Inventory<T> saved, int storedVersion) {
        MigrationChain<Inventory<T>> chain = MigrationChain.<Inventory<T>>builder()
                .step(0, inventory -> inventory)
                .step(1, MachineSaveMigrations::addedInputSlots)
                .step(2, inventory -> inventory)
                .build();
        Inventory<T> migrated = chain.migrate(saved, storedVersion, SaveSchema.Kind.MACHINE.current());
        // A damaged or hand-edited save can still have the wrong size; line it up the same way.
        return migrated.stacks().size() == migrated.wanted() ? migrated : addedInputSlots(migrated);
    }

    /**
     * Version 1 to 2: a machine gained input slots. The saved inputs keep their places, and so do
     * the outputs, counted from the first output slot, so nothing lands in the wrong kind of slot.
     * A save with more inputs than the machine has loses the extra slots' contents, which cannot be
     * placed anywhere; that only happens to a hand-edited save.
     */
    static <T> Inventory<T> addedInputSlots(Inventory<T> saved) {
        List<T> old = saved.stacks();
        int savedInputs = Math.max(0, old.size() - saved.outputSlots());
        List<T> resized = new ArrayList<>();
        for (int i = 0; i < saved.wanted(); i++) {
            resized.add(saved.empty());
        }
        for (int i = 0; i < Math.min(savedInputs, saved.inputSlots()); i++) {
            resized.set(i, old.get(i));
        }
        for (int i = 0; i < Math.min(old.size() - savedInputs, saved.outputSlots()); i++) {
            resized.set(saved.inputSlots() + i, old.get(savedInputs + i));
        }
        return new Inventory<>(resized, saved.inputSlots(), saved.outputSlots(), saved.empty());
    }
}
