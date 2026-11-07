package com.itmecasey.aeroflux.creative;

import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.registry.ModFluids;
import java.util.function.Supplier;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;

/**
 * The endless tank inside a Creative Flux Tank: it always holds its flux, gives any amount of it,
 * and swallows any flux poured in without filling up. It reports itself as full, so gauges, Jade
 * and the tank renderer all show a full tank. Nothing here ever changes, so a simulated move and a
 * real one give the same answer.
 */
public final class CreativeFluxHandler implements IFluidHandler, IFluidTank {
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

    // --- The tank, as the renderer and gauges see it -------------------------------------------

    @NotNull
    @Override
    public FluidStack getFluid() {
        return held(SHOWN_AMOUNT);
    }

    @Override
    public int getFluidAmount() {
        return SHOWN_AMOUNT;
    }

    @Override
    public int getCapacity() {
        return SHOWN_AMOUNT;
    }

    /** Any flux may be poured in; it simply disappears. */
    @Override
    public boolean isFluidValid(FluidStack stack) {
        return ModFluids.isAnyFlux(stack.getFluid());
    }

    // --- The handler, as pipes and buckets see it ----------------------------------------------

    @Override
    public int getTanks() {
        return 1;
    }

    @NotNull
    @Override
    public FluidStack getFluidInTank(int tank) {
        return tank == 0 ? getFluid() : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank == 0 ? SHOWN_AMOUNT : 0;
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        return tank == 0 && isFluidValid(stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return !resource.isEmpty() && isFluidValid(resource) ? resource.getAmount() : 0;
    }

    @NotNull
    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return !resource.isEmpty() && resource.getFluid() == ModFluids.fluidOf(flux.get())
                ? held(resource.getAmount()) : FluidStack.EMPTY;
    }

    @NotNull
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return maxDrain > 0 ? held(maxDrain) : FluidStack.EMPTY;
    }
}
