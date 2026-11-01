package com.itmecasey.aeroflux.client;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.client.model.BundleUnbakedModel;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.registry.ModFluids;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterBlockStateModels;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSources;

/**
 * Client-only setup: how the two fluids look in the world. Both use the same soft, animated
 * textures and are tinted with their flux color, so a texture pack only has to replace one pair.
 * The textures are slightly see-through, which the game detects and renders translucent.
 */
@EventBusSubscriber(modid = AeroFlux.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    private static final Material STILL = new Material(Ids.mod("block/flux_still"));
    private static final Material FLOWING = new Material(Ids.mod("block/flux_flow"));

    private ClientSetup() {
    }

    /** Teaches the block state loader the {@code aeroflux:conduit_bundle} model type. */
    @SubscribeEvent
    public static void registerBlockStateModels(RegisterBlockStateModels event) {
        event.registerModel(Ids.mod("conduit_bundle"), BundleUnbakedModel.CODEC);
    }

    @SubscribeEvent
    public static void registerFluidModels(RegisterFluidModelsEvent event) {
        event.register(fluidModel(FluxType.AERO), ModFluids.AERO_FLUX.get(), ModFluids.FLOWING_AERO_FLUX.get());
        event.register(fluidModel(FluxType.MOON), ModFluids.MOON_FLUX.get(), ModFluids.FLOWING_MOON_FLUX.get());
    }

    private static FluidModel.Unbaked fluidModel(FluxType flux) {
        // Fully opaque tint; the textures carry their own transparency.
        return new FluidModel.Unbaked(STILL, FLOWING, null, FluidTintSources.constant(0xFF000000 | flux.tint()));
    }
}
