package com.itmecasey.aeroflux.client;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.client.model.BundleGeometryLoader;
import com.itmecasey.aeroflux.registry.ModFluids;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only setup that cannot be expressed in data. */
@Mod.EventBusSubscriber(modid = AeroFlux.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    /** Teaches the model system the {@code aeroflux:conduit_bundle} model type. */
    @SubscribeEvent
    public static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register("conduit_bundle", new BundleGeometryLoader());
    }

    /** The fluid textures are slightly see-through, so the fluids need the translucent layer. */
    @SubscribeEvent
    public static void setRenderLayers(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            RenderType translucent = RenderType.translucent();
            ItemBlockRenderTypes.setRenderLayer(ModFluids.AERO_FLUX.get(), translucent);
            ItemBlockRenderTypes.setRenderLayer(ModFluids.FLOWING_AERO_FLUX.get(), translucent);
            ItemBlockRenderTypes.setRenderLayer(ModFluids.MOON_FLUX.get(), translucent);
            ItemBlockRenderTypes.setRenderLayer(ModFluids.FLOWING_MOON_FLUX.get(), translucent);
        });
    }
}
