package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.core.FluxType;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The fluid types behind Aero Flux and Moon Flux. A fluid type holds the physical properties
 * (light, density, viscosity); the fluids themselves are registered in {@link ModFluids}.
 */
public final class ModFluidTypes {
    private static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, AeroFlux.MODID);

    /** Sunlight gathered by day. Slightly warm and light. */
    public static final DeferredHolder<FluidType, FluidType> AERO_FLUX =
            FLUID_TYPES.register(FluxType.AERO.id() + "_flux", () -> new FluidType(properties(FluxType.AERO, 320, 900)));

    /** Moonlight gathered by night. Cool and slightly thicker. */
    public static final DeferredHolder<FluidType, FluidType> MOON_FLUX =
            FLUID_TYPES.register(FluxType.MOON.id() + "_flux", () -> new FluidType(properties(FluxType.MOON, 250, 1100)));

    /** Blended from the other two; violet, faintly warm, a touch thicker than Aero Flux. */
    public static final DeferredHolder<FluidType, FluidType> TWILIGHT_FLUX =
            FLUID_TYPES.register(FluxType.TWILIGHT.id() + "_flux", () -> new FluidType(properties(FluxType.TWILIGHT, 290, 1000)));

    private ModFluidTypes() {
    }

    /**
     * Properties shared by both fluids: a faint glow, bucket sounds, and no infinite sources,
     * so Flux can only come from collectors, never from two source blocks side by side.
     *
     * @param type        which flux this is, for its translation key
     * @param temperature temperature in kelvin; only flavor for now
     * @param density     density in kg/m3, close to water so it flows and floats similarly
     */
    private static FluidType.Properties properties(FluxType type, int temperature, int density) {
        return FluidType.Properties.create()
                .descriptionId("fluid_type." + AeroFlux.MODID + "." + type.id() + "_flux")
                .lightLevel(FluxType.LIGHT_LEVEL)
                .temperature(temperature)
                .density(density)
                .viscosity(1000)
                .canConvertToSource(false)
                .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY);
    }

    /** Hooks this registry into the mod event bus; call once from the mod constructor. */
    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
    }
}
