package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.machine.ExtraTank;
import com.itmecasey.aeroflux.core.machine.FaceRules;
import com.itmecasey.aeroflux.core.machine.PortRole;
import com.itmecasey.aeroflux.core.machine.SideResource;
import java.util.Objects;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * A machine's tanks as one face sees them: the Flux tank first, then any extra tanks. The Flux
 * tank is a buffer, so the face mode alone decides whether Flux may go in, come out, both or
 * neither. An extra input tank (water, Moon Flux) only ever takes fluid in, and an extra output
 * tank (Twilight Flux) only ever lets it out. Every tank still refuses any fluid it is not for.
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

    private FluidStacksResourceHandler tank(int index) {
        return index == 0 ? machine.fluxTank() : machine.extraTank(index - 1);
    }

    private @Nullable ExtraTank spec(int index) {
        return index == 0 ? null : machine.machine().extraTanks().get(index - 1);
    }

    private boolean mayInsert(int index) {
        ExtraTank spec = spec(index);
        if (spec == null) {
            return FaceRules.canInsert(PortRole.BUFFER, mode());
        }
        return !spec.output() && FaceRules.canInsert(PortRole.INPUT, mode());
    }

    private boolean mayExtract(int index) {
        ExtraTank spec = spec(index);
        if (spec == null) {
            return FaceRules.canExtract(PortRole.BUFFER, mode());
        }
        return spec.output() && FaceRules.canExtract(PortRole.OUTPUT, mode());
    }

    @Override
    public int size() {
        return 1 + machine.extraTankCount();
    }

    @Override
    public FluidResource getResource(int index) {
        return tank(index).getResource(0);
    }

    @Override
    public long getAmountAsLong(int index) {
        return tank(index).getAmountAsLong(0);
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return tank(index).getCapacityAsLong(0, resource);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return mayInsert(index) && tank(index).isValid(0, resource);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        Objects.checkIndex(index, size());
        return mayInsert(index) ? tank(index).insert(0, resource, amount, transaction) : 0;
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        Objects.checkIndex(index, size());
        return mayExtract(index) ? tank(index).extract(0, resource, amount, transaction) : 0;
    }
}
