package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.machine.FaceRules;
import com.itmecasey.aeroflux.core.machine.PortRole;
import com.itmecasey.aeroflux.core.machine.SideResource;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * A machine's Flux tank as one face sees it. The tank is a buffer: the face mode alone decides
 * whether Flux may go in, come out, both or neither, and the tank itself still refuses any
 * fluid the machine is not built for.
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

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return machine.fluxTank().getFluid();
    }

    @Override
    public int getTankCapacity(int tank) {
        return machine.fluxTank().getCapacity();
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return FaceRules.canInsert(PortRole.BUFFER, mode()) && machine.acceptsFlux(stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return FaceRules.canInsert(PortRole.BUFFER, mode()) ? machine.fluxTank().fill(resource, action) : 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return FaceRules.canExtract(PortRole.BUFFER, mode()) ? machine.fluxTank().drain(resource, action) : FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return FaceRules.canExtract(PortRole.BUFFER, mode()) ? machine.fluxTank().drain(maxDrain, action) : FluidStack.EMPTY;
    }
}
