package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.AeroFlux;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The source and flowing fluids for Aero Flux and Moon Flux. Each pair is tied together by a
 * {@link ForgeFlowingFluid.Properties} that points at its fluid type, bucket and block.
 */
public final class ModFluids {
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, AeroFlux.MODID);

    public static final RegistryObject<ForgeFlowingFluid.Source> AERO_FLUX =
            FLUIDS.register("aero_flux", () -> new ForgeFlowingFluid.Source(aeroProperties()));
    public static final RegistryObject<ForgeFlowingFluid.Flowing> FLOWING_AERO_FLUX =
            FLUIDS.register("flowing_aero_flux", () -> new ForgeFlowingFluid.Flowing(aeroProperties()));

    public static final RegistryObject<ForgeFlowingFluid.Source> MOON_FLUX =
            FLUIDS.register("moon_flux", () -> new ForgeFlowingFluid.Source(moonProperties()));
    public static final RegistryObject<ForgeFlowingFluid.Flowing> FLOWING_MOON_FLUX =
            FLUIDS.register("flowing_moon_flux", () -> new ForgeFlowingFluid.Flowing(moonProperties()));

    private ModFluids() {
    }

    /** Whether the fluid is Aero Flux or Moon Flux, source or flowing. */
    public static boolean isFlux(Fluid fluid) {
        return fluid.isSame(AERO_FLUX.get()) || fluid.isSame(MOON_FLUX.get());
    }

    // The properties are built inside the suppliers, after every registry object exists,
    // because the fluids, block and bucket all refer to each other.
    private static ForgeFlowingFluid.Properties aeroProperties() {
        return new ForgeFlowingFluid.Properties(ModFluidTypes.AERO_FLUX, AERO_FLUX, FLOWING_AERO_FLUX)
                .block(ModBlocks.AERO_FLUX)
                .bucket(ModItems.AERO_FLUX_BUCKET);
    }

    private static ForgeFlowingFluid.Properties moonProperties() {
        return new ForgeFlowingFluid.Properties(ModFluidTypes.MOON_FLUX, MOON_FLUX, FLOWING_MOON_FLUX)
                .block(ModBlocks.MOON_FLUX)
                .bucket(ModItems.MOON_FLUX_BUCKET);
    }

    /** Hooks this registry into the mod event bus; call once from the mod constructor. */
    public static void register(IEventBus modEventBus) {
        FLUIDS.register(modEventBus);
    }
}
