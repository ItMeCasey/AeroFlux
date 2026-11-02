package com.itmecasey.aeroflux.client;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.client.model.BundleGeometryLoader;
import com.itmecasey.aeroflux.client.render.FluxTankRenderer;
import com.itmecasey.aeroflux.client.screen.PipeConfigScreen;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.itmecasey.aeroflux.registry.ModFluidTypes;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModMenus;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Client-only setup: how the two fluids look in the world. Both use the same soft, animated
 * textures and are tinted with their flux color, so a texture pack only has to replace one pair.
 */
@EventBusSubscriber(modid = AeroFlux.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    private static final ResourceLocation STILL = ResourceLocation.fromNamespaceAndPath(AeroFlux.MODID, "block/flux_still");
    private static final ResourceLocation FLOWING = ResourceLocation.fromNamespaceAndPath(AeroFlux.MODID, "block/flux_flow");

    private ClientSetup() {
    }

    /** Gives the tanks their fluid-level renderer. */
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.TANK.get(), FluxTankRenderer::new);
    }

    /** Pairs the pipe menu with its screen. */
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.PIPE_CONFIG.get(), PipeConfigScreen::new);
    }

    /** Teaches the model system the {@code aeroflux:conduit_bundle} model type. */
    @SubscribeEvent
    public static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(ResourceLocation.fromNamespaceAndPath(AeroFlux.MODID, "conduit_bundle"),
                new BundleGeometryLoader());
    }

    @SubscribeEvent
    public static void registerFluidExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new FluxFluidExtensions(FluxType.AERO), ModFluidTypes.AERO_FLUX.get());
        event.registerFluidType(new FluxFluidExtensions(FluxType.MOON), ModFluidTypes.MOON_FLUX.get());
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

    /** Supplies the shared textures and the per-flux tint. */
    private record FluxFluidExtensions(FluxType type) implements IClientFluidTypeExtensions {
        @Override
        public ResourceLocation getStillTexture() {
            return STILL;
        }

        @Override
        public ResourceLocation getFlowingTexture() {
            return FLOWING;
        }

        @Override
        public int getTintColor() {
            // Fully opaque tint; the textures carry their own transparency.
            return 0xFF000000 | type.tint();
        }
    }
}
