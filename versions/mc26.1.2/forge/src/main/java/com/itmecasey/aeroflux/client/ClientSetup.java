package com.itmecasey.aeroflux.client;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.client.model.BundleBlockStateModel;
import com.itmecasey.aeroflux.client.model.BundleGeometryLoader;
import com.itmecasey.aeroflux.client.render.FluxTankRenderer;
import com.itmecasey.aeroflux.client.screen.MachineScreen;
import com.itmecasey.aeroflux.client.screen.PipeConfigScreen;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only setup: teaches the model system how to draw a conduit bundle. */
@Mod.EventBusSubscriber(modid = AeroFlux.MODID, value = Dist.CLIENT)
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
        event.register(Ids.mod("conduit_bundle"), new BundleGeometryLoader());
    }

    /**
     * Replaces the baked bundle model with one that draws from the block entity's data. The
     * model file can only describe a fixed set of quads, so it serves as the source of the
     * textures and the real model is swapped in once everything is baked.
     */
    @SubscribeEvent
    public static void swapInBundleModel(ModelEvent.ModifyBakingResult event) {
        for (BlockState state : ModBlocks.CONDUIT_BUNDLE.get().getStateDefinition().getPossibleStates()) {
            BlockStateModel baked = event.getResults().blockStateModels().get(state);
            if (baked == null) {
                continue;
            }
            BundleBlockStateModel dynamic = BundleBlockStateModel.from(baked);
            if (dynamic == null) {
                AeroFlux.LOGGER.error("The conduit bundle model for {} was not built by the bundle loader; "
                        + "check models/block/conduit_bundle.json", state);
            } else {
                event.getResults().blockStateModels().put(state, dynamic);
            }
        }
    }
}
