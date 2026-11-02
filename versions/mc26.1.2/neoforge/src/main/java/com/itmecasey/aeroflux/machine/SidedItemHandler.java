package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.machine.FaceRules;
import com.itmecasey.aeroflux.core.machine.PortRole;
import com.itmecasey.aeroflux.core.machine.SideResource;
import java.util.Objects;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * A machine's inventory as one face sees it. The face's mode is read on every call, so changing
 * a setting takes effect at once without rebuilding anything: input slots take items when the
 * mode lets things in, output slots give them up when it lets things out, and a slot never does
 * the other thing (a hopper cannot steal the input or stuff the output).
 *
 * <p>Only the per-slot calls are written out; the whole-handler calls of {@link ResourceHandler}
 * walk the slots and so go through the same checks.
 */
final class SidedItemHandler implements ResourceHandler<ItemResource> {
    private final FluxMachineBlockEntity machine;
    private final @Nullable Direction side;

    SidedItemHandler(FluxMachineBlockEntity machine, @Nullable Direction side) {
        this.machine = machine;
        this.side = side;
    }

    private ConnectionMode mode() {
        return machine.mode(SideResource.ITEMS, side);
    }

    @Override
    public int size() {
        return machine.inventory().size();
    }

    @Override
    public ItemResource getResource(int index) {
        return machine.inventory().getResource(index);
    }

    @Override
    public long getAmountAsLong(int index) {
        return machine.inventory().getAmountAsLong(index);
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return machine.inventory().getCapacityAsLong(index, resource);
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return machine.isInputSlot(index) && FaceRules.canInsert(PortRole.INPUT, mode())
                && machine.inventory().isValid(index, resource);
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        Objects.checkIndex(index, size());
        if (!machine.isInputSlot(index) || !FaceRules.canInsert(PortRole.INPUT, mode())) {
            return 0;
        }
        return machine.inventory().insert(index, resource, amount, transaction);
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        Objects.checkIndex(index, size());
        if (!machine.isOutputSlot(index) || !FaceRules.canExtract(PortRole.OUTPUT, mode())) {
            return 0;
        }
        return machine.inventory().extract(index, resource, amount, transaction);
    }
}
