package com.itmecasey.aeroflux.fluid;

import net.neoforged.neoforge.transfer.DelegatingResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A view of a tank that can be drained but never filled. Collectors expose their buffer this way,
 * so a pipe can pull flux out but nothing can push fluid back in.
 */
public final class OutputOnlyFluidHandler extends DelegatingResourceHandler<FluidResource> {
    public OutputOnlyFluidHandler(ResourceHandler<FluidResource> delegate) {
        super(delegate);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return false;
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return 0;
    }

    @Override
    public int insert(FluidResource resource, int amount, TransactionContext transaction) {
        return 0;
    }
}
