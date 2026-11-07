package com.itmecasey.aeroflux.creative;

import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.registry.ModFluids;
import java.util.function.Supplier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * The endless tank inside a Creative Flux Tank: it always holds its flux, gives any amount of it,
 * and swallows any flux poured in without filling up. It reports itself as full, so gauges, Jade
 * and the tank renderer all show a full tank. Nothing here changes, so transactions need no undo.
 */
public final class CreativeFluxHandler implements ResourceHandler<FluidResource> {
    /** What the tank claims to hold and to have room for: full, and far more than anything drains. */
    public static final int SHOWN_AMOUNT = 1_000_000_000;

    private final Supplier<FluxType> flux;

    /** A handler that gives whatever flux {@code flux} names at the time. */
    public CreativeFluxHandler(Supplier<FluxType> flux) {
        this.flux = flux;
    }

    private FluidResource held() {
        return FluidResource.of(ModFluids.fluidOf(flux.get()));
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public FluidResource getResource(int index) {
        return index == 0 ? held() : FluidResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        return index == 0 ? SHOWN_AMOUNT : 0;
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return index == 0 ? SHOWN_AMOUNT : 0;
    }

    /** Any flux may be poured in; it simply disappears. */
    @Override
    public boolean isValid(int index, FluidResource resource) {
        return index == 0 && ModFluids.isAnyFlux(resource);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return isValid(index, resource) ? Math.max(0, amount) : 0;
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return index == 0 && resource.equals(held()) ? Math.max(0, amount) : 0;
    }
}
