package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.core.FluxType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The source and flowing fluids for Aero Flux and Moon Flux. Each pair is tied together by a
 * {@link BaseFlowingFluid.Properties} that points at its fluid type, bucket and block.
 */
public final class ModFluids {
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, AeroFlux.MODID);

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> AERO_FLUX =
            FLUIDS.register("aero_flux", () -> new BaseFlowingFluid.Source(aeroProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_AERO_FLUX =
            FLUIDS.register("flowing_aero_flux", () -> new BaseFlowingFluid.Flowing(aeroProperties()));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> MOON_FLUX =
            FLUIDS.register("moon_flux", () -> new BaseFlowingFluid.Source(moonProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_MOON_FLUX =
            FLUIDS.register("flowing_moon_flux", () -> new BaseFlowingFluid.Flowing(moonProperties()));

    private ModFluids() {
    }

    /** Whether the fluid is Aero Flux or Moon Flux, source or flowing. */
    public static boolean isFlux(Fluid fluid) {
        return fluid.isSame(AERO_FLUX.get()) || fluid.isSame(MOON_FLUX.get());
    }

    /** Which flux a fluid is, or {@code null} for any other fluid. */
    public static FluxType fluxTypeOf(Fluid fluid) {
        if (fluid.isSame(AERO_FLUX.get())) {
            return FluxType.AERO;
        }
        return fluid.isSame(MOON_FLUX.get()) ? FluxType.MOON : null;
    }

    /** The source fluid of a flux. */
    public static Fluid fluidOf(FluxType type) {
        return type == FluxType.AERO ? AERO_FLUX.get() : MOON_FLUX.get();
    }

    // The properties are built inside the suppliers, after every registry holder exists,
    // because the fluids, block and bucket all refer to each other.
    private static BaseFlowingFluid.Properties aeroProperties() {
        return new BaseFlowingFluid.Properties(ModFluidTypes.AERO_FLUX, AERO_FLUX, FLOWING_AERO_FLUX)
                .block(ModBlocks.AERO_FLUX)
                .bucket(ModItems.AERO_FLUX_BUCKET);
    }

    private static BaseFlowingFluid.Properties moonProperties() {
        return new BaseFlowingFluid.Properties(ModFluidTypes.MOON_FLUX, MOON_FLUX, FLOWING_MOON_FLUX)
                .block(ModBlocks.MOON_FLUX)
                .bucket(ModItems.MOON_FLUX_BUCKET);
    }

    /** Hooks this registry into the mod event bus; call once from the mod constructor. */
    public static void register(IEventBus modEventBus) {
        FLUIDS.register(modEventBus);
    }
}
