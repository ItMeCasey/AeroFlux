package com.itmecasey.aeroflux.client;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.client.model.BundleGeometryLoader;
import com.itmecasey.aeroflux.client.render.FluxTankRenderer;
import com.itmecasey.aeroflux.client.screen.MachineScreen;
import com.itmecasey.aeroflux.client.screen.PipeConfigScreen;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only setup that cannot be expressed in data. */
@Mod.EventBusSubscriber(modid = AeroFlux.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    /** Gives the tanks their fluid-level renderer. */
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.TANK.get(), FluxTankRenderer::new);
    }

    /** Pairs the pipe menu and the machine menus with their screens. */
    @SubscribeEvent
    public static void registerScreens(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.PIPE_CONFIG.get(), PipeConfigScreen::new);
            for (MachineType machine : MachineType.values()) {
                MenuScreens.register(ModMenus.machine(machine).get(), MachineScreen::new);
            }
        });
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
