package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.machine.FaceRules;
import com.itmecasey.aeroflux.core.machine.PortRole;
import com.itmecasey.aeroflux.core.machine.SideResource;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * A machine's inventory as one face sees it. The face's mode is read on every call, so changing
 * a setting takes effect at once without rebuilding anything: input slots take items when the
 * mode lets things in, output slots give them up when it lets things out, and a slot never does
 * the other thing (a hopper cannot steal the input or stuff the output).
 */
final class SidedItemHandler implements IItemHandler {
    private final FluxMachineBlockEntity machine;
    @Nullable
    private final Direction side;

    SidedItemHandler(FluxMachineBlockEntity machine, @Nullable Direction side) {
        this.machine = machine;
        this.side = side;
    }

    private ConnectionMode mode() {
        return machine.mode(SideResource.ITEMS, side);
    }

    @Override
    public int getSlots() {
        return machine.inventory().getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return machine.inventory().getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (!machine.isInputSlot(slot) || !FaceRules.canInsert(PortRole.INPUT, mode())
                || !machine.acceptsFromOutside(slot, stack)) {
            return stack;
        }
        return machine.inventory().insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!machine.isOutputSlot(slot) || !FaceRules.canExtract(PortRole.OUTPUT, mode())) {
            return ItemStack.EMPTY;
        }
        return machine.inventory().extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return machine.inventory().getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return machine.isInputSlot(slot) && FaceRules.canInsert(PortRole.INPUT, mode())
                && machine.inventory().isItemValid(slot, stack) && machine.acceptsFromOutside(slot, stack);
    }
}
