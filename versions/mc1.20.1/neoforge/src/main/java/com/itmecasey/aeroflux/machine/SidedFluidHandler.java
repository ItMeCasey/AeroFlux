package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.machine.ExtraTank;
import com.itmecasey.aeroflux.core.machine.FaceRules;
import com.itmecasey.aeroflux.core.machine.PortRole;
import com.itmecasey.aeroflux.core.machine.SideResource;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

/**
 * A machine's tanks as one face sees them: the Flux tank first, then any extra tanks. The Flux
 * tank is a buffer, so the face mode alone decides whether Flux may go in, come out, both or
 * neither. An extra input tank (water, Moon Flux) only ever takes fluid in, and an extra output
 * tank (Twilight Flux) only ever lets it out. Every tank still refuses any fluid it is not for.
 */
final class SidedFluidHandler implements IFluidHandler {
    private final FluxMachineBlockEntity machine;
    @Nullable
    private final Direction side;

    SidedFluidHandler(FluxMachineBlockEntity machine, @Nullable Direction side) {
        this.machine = machine;
        this.side = side;
    }

    private ConnectionMode mode() {
        return machine.mode(SideResource.FLUID, side);
    }

    private FluidTank tank(int index) {
        return index == 0 ? machine.fluxTank() : machine.extraTank(index - 1);
    }

    @Nullable
    private ExtraTank spec(int index) {
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
    public int getTanks() {
        return 1 + machine.extraTankCount();
    }

    @Override
    public FluidStack getFluidInTank(int index) {
        return tank(index).getFluid();
    }

    @Override
    public int getTankCapacity(int index) {
        return tank(index).getCapacity();
    }

    @Override
    public boolean isFluidValid(int index, FluidStack stack) {
        return mayInsert(index) && tank(index).isFluidValid(stack);
    }

    /** Fills the first tank that takes the fluid and may be filled from this face. */
    @Override
    public int fill(FluidStack resource, FluidAction action) {
        for (int index = 0; index < getTanks(); index++) {
            if (mayInsert(index) && tank(index).isFluidValid(resource)) {
                return tank(index).fill(resource, action);
            }
        }
        return 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        for (int index = 0; index < getTanks(); index++) {
            if (mayExtract(index) && tank(index).getFluid().isFluidEqual(resource)) {
                return tank(index).drain(resource, action);
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        for (int index = 0; index < getTanks(); index++) {
            if (mayExtract(index) && !tank(index).isEmpty()) {
                return tank(index).drain(maxDrain, action);
            }
        }
        return FluidStack.EMPTY;
    }
}
