package com.itmecasey.aeroflux.creative;

import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.registry.ModFluids;
import java.util.function.Supplier;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * The endless tank inside a Creative Flux Tank: it always holds its flux, gives any amount of it,
 * and swallows any flux poured in without filling up. It reports itself as full, so gauges, Jade
 * and the tank renderer all show a full tank. Nothing here changes, so simulating and executing
 * give the same answer.
 */
public final class CreativeFluxHandler implements IFluidHandler {
    /** What the tank claims to hold and to have room for: full, and far more than anything drains. */
    public static final int SHOWN_AMOUNT = 1_000_000_000;

    private final Supplier<FluxType> flux;

    /** A handler that gives whatever flux {@code flux} names at the time. */
    public CreativeFluxHandler(Supplier<FluxType> flux) {
        this.flux = flux;
    }

    private FluidStack held(int amount) {
        return new FluidStack(ModFluids.fluidOf(flux.get()), amount);
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return tank == 0 ? held(SHOWN_AMOUNT) : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank == 0 ? SHOWN_AMOUNT : 0;
    }

    /** Any flux may be poured in; it simply disappears. */
    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return tank == 0 && ModFluids.isAnyFlux(stack.getFluid());
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return isFluidValid(0, resource) ? resource.getAmount() : 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return !resource.isEmpty() && resource.isFluidEqual(held(1)) ? held(resource.getAmount()) : FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return maxDrain > 0 ? held(maxDrain) : FluidStack.EMPTY;
    }
}
