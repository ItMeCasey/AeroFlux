package com.itmecasey.aeroflux.fluid;

import java.util.function.Predicate;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * A NeoForge fluid tank that fills up to {@link #getCapacity()} rather than the size it was built with.
 *
 * <p>NeoForge's own tank reads its capacity field directly when it fills, so a subclass that
 * changes {@code getCapacity()} (a machine with Capacity Upgrades, or a tank that reports at least
 * what it holds) is ignored there: the upgrades add no room, and a tank holding more than its size,
 * because the config made it smaller, is cut back to that size by the next fill, which also
 * reports a negative amount to whoever was filling. Here filling and the free space both go by
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
        if (!fluid.isEmpty() && !FluidStack.isSameFluidSameComponents(fluid, resource)) {
            return 0;
        }
        int filled = Math.min(getSpace(), resource.getAmount());
        if (filled <= 0 || action.simulate()) {
            return Math.max(filled, 0);
        }
        if (fluid.isEmpty()) {
            fluid = resource.copyWithAmount(filled);
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
