package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.machine.FaceRules;
import com.itmecasey.aeroflux.core.machine.PortRole;
import com.itmecasey.aeroflux.core.machine.SideResource;
import java.util.Objects;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * A machine's Flux tank as one face sees it. The tank is a buffer: the face mode alone decides
 * whether Flux may go in, come out, both or neither, and the tank itself still refuses any
 * fluid the machine is not built for.
 */
final class SidedFluidHandler implements ResourceHandler<FluidResource> {
    private final FluxMachineBlockEntity machine;
    private final @Nullable Direction side;

    SidedFluidHandler(FluxMachineBlockEntity machine, @Nullable Direction side) {
        this.machine = machine;
        this.side = side;
    }

    private ConnectionMode mode() {
        return machine.mode(SideResource.FLUID, side);
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public FluidResource getResource(int index) {
        return machine.fluxTank().getResource(index);
    }

    @Override
    public long getAmountAsLong(int index) {
        return machine.fluxTank().getAmountAsLong(index);
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return machine.fluxTank().getCapacityAsLong(index, resource);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return FaceRules.canInsert(PortRole.BUFFER, mode()) && machine.fluxTank().isValid(index, resource);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        Objects.checkIndex(index, size());
        return FaceRules.canInsert(PortRole.BUFFER, mode())
                ? machine.fluxTank().insert(index, resource, amount, transaction) : 0;
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        Objects.checkIndex(index, size());
        return FaceRules.canExtract(PortRole.BUFFER, mode())
                ? machine.fluxTank().extract(index, resource, amount, transaction) : 0;
    }
}
