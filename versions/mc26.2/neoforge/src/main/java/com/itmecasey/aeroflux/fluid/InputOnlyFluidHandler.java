package com.itmecasey.aeroflux.fluid;

import net.neoforged.neoforge.transfer.DelegatingResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A view of a tank that can be filled but never drained, the mirror of
 * {@link OutputOnlyFluidHandler}. Chunk loaders expose their fuel tank this way, so a pipe or a
 * bucket can top it up but nothing can pump the Flux back out.
 */
public final class InputOnlyFluidHandler extends DelegatingResourceHandler<FluidResource> {
    public InputOnlyFluidHandler(ResourceHandler<FluidResource> delegate) {
        super(delegate);
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return 0;
    }

    @Override
    public int extract(FluidResource resource, int amount, TransactionContext transaction) {
        return 0;
    }
}
