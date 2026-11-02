package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.conduit.UpgradeItem;
import com.itmecasey.aeroflux.core.conduit.PipeUpgrades;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.machine.MachineUpgrades;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * A machine's upgrade slots: each holds a single upgrade item and nothing else. The machine keeps
 * one of these next to its inventory and never hands it to pipes or hoppers, so upgrades only go
 * in and out through the screen. The client's menu builds a plain one of the same size to show
 * what the server sends.
 *
 * <p>Every machine takes Speed and Capacity Upgrades. The machine-only upgrades go only into the
 * machines they do something in, so a Fortune Upgrade never sits uselessly in a furnace.
 */
public class MachineUpgradeSlots extends ItemStackHandler {
    private final MachineType machine;
    private final Runnable onChange;

    /**
     * @param size     the number of slots, from {@link MachineUpgrades#slots}
     * @param machine  the kind of machine, which decides the machine-only upgrades it takes
     * @param onChange called after any slot changed
     */
    public MachineUpgradeSlots(int size, MachineType machine, Runnable onChange) {
        super(size);
        this.machine = machine;
        this.onChange = onChange;
    }

    /** Slots for the client's copy of a menu, which has nothing to tell. */
    public MachineUpgradeSlots(int size, MachineType machine) {
        this(size, machine, () -> { });
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return accepts(machine, stack.getItem());
    }

    /** Whether a machine of this kind takes this item as an upgrade. */
    public static boolean accepts(MachineType machine, Item item) {
        if (item instanceof UpgradeItem) {
            return true;
        }
        return item instanceof MachineUpgradeItem upgrade && upgrade.kind().fits(machine);
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    protected void onContentsChanged(int slot) {
        onChange.run();
    }

    /** How many upgrades of one kind are installed. */
    public int count(PipeUpgrades.Kind kind) {
        int count = 0;
        for (ItemStack stack : stacks) {
            if (stack.getItem() instanceof UpgradeItem upgrade && upgrade.kind() == kind) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** How many machine-only upgrades of one kind are installed. */
    public int count(MachineUpgrades.Special kind) {
        int count = 0;
        for (ItemStack stack : stacks) {
            if (stack.getItem() instanceof MachineUpgradeItem upgrade && upgrade.kind() == kind) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** What a slot holds, as a stack of its own that can be changed freely. */
    public ItemStack stackIn(int slot) {
        return stacks.get(slot).copy();
    }

    /** Saved by a build with a different slot count: keep this machine's number of slots. */
    @Override
    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
        int wanted = getSlots();
        super.deserializeNBT(registries, tag);
        if (getSlots() != wanted) {
            // The count comes from the tier alone, so this only happens if that rule changes.
            ItemStack[] kept = new ItemStack[Math.min(wanted, getSlots())];
            for (int i = 0; i < kept.length; i++) {
                kept[i] = stacks.get(i);
            }
            setSize(wanted);
            for (int i = 0; i < kept.length; i++) {
                stacks.set(i, kept[i]);
            }
        }
    }
}
