package com.itmecasey.aeroflux.fluid;

import net.minecraft.core.HolderLookup;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * A view of a tank that can be drained but never filled. Collectors expose their buffer this way,
 * so a pipe can pull flux out but nothing can push fluid back in.
 */
public final class OutputOnlyFluidHandler implements IFluidHandler {
    private final IFluidHandler delegate;

    public OutputOnlyFluidHandler(IFluidHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public int getTanks() {
        return delegate.getTanks();
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return delegate.getFluidInTank(tank);
    }

    @Override
    public int getTankCapacity(int tank) {
        return delegate.getTankCapacity(tank);
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return false;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return delegate.drain(resource, action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return delegate.drain(maxDrain, action);
    }
}
