package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.machine.ExtraTank;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/** Ties the fluid ids in {@link ExtraTank} to the game's fluids. */
public final class ExtraTanks {
    private ExtraTanks() {
    }

    /** The fluid an extra tank holds, or empty if no mod registered it. */
    public static Fluid fluid(ExtraTank spec) {
        return BuiltInRegistries.FLUID.getOptional(ResourceLocation.parse(spec.fluid())).orElse(Fluids.EMPTY);
    }

    /** Whether a stack is the fluid an extra tank holds, source or flowing. */
    public static boolean matches(ExtraTank spec, FluidStack stack) {
        Fluid fluid = fluid(spec);
        return !stack.isEmpty() && fluid != Fluids.EMPTY && stack.getFluid().isSame(fluid);
    }
}
