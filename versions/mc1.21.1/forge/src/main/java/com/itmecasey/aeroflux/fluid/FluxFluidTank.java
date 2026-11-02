package com.itmecasey.aeroflux.fluid;

import java.util.function.Predicate;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;

/**
 * A Forge fluid tank that fills up to {@link #getCapacity()} rather than the size it was built with.
 *
 * <p>Forge's own tank reads its capacity field directly when it fills, so a subclass that changes
 * {@code getCapacity()} (a machine with Capacity upgrades, or a tank that reports at least what
 * it holds) is ignored there: the upgrades add no room, and a tank holding more than its size is
 * cut back to that size by the next fill. Here filling and the free space both go by
 * {@code getCapacity()}, so a tank that is too full simply takes nothing until it drains.
 */
public class FluxFluidTank extends FluidTank {
    public FluxFluidTank(int capacity, Predicate<FluidStack> validator) {
        super(capacity, validator);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !isFluidValid(resource)) {
            return 0;
        }
        if (!fluid.isEmpty() && !fluid.isFluidEqual(resource)) {
            return 0;
        }
        int filled = Math.min(getSpace(), resource.getAmount());
        if (filled <= 0 || action.simulate()) {
            return Math.max(filled, 0);
        }
        if (fluid.isEmpty()) {
            fluid = new FluidStack(resource, filled);
        } else {
            fluid.grow(filled);
        }
        onContentsChanged();
        return filled;
    }

    @Override
    public int getSpace() {
        return Math.max(0, getCapacity() - fluid.getAmount());
    }
}
