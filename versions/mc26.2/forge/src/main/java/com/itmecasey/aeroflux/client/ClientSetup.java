package com.itmecasey.aeroflux.client;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.client.model.BundleBlockStateModel;
import com.itmecasey.aeroflux.client.model.BundleGeometryLoader;
import com.itmecasey.aeroflux.client.model.PlantGrowerModel;
import com.itmecasey.aeroflux.client.render.FluxTankRenderer;
import com.itmecasey.aeroflux.client.screen.ChunkLoaderScreen;
import com.itmecasey.aeroflux.client.screen.CollectorScreen;
import com.itmecasey.aeroflux.client.screen.ConfiguratorScreen;
import com.itmecasey.aeroflux.client.screen.ConverterScreen;
import com.itmecasey.aeroflux.client.screen.GuideScreen;
import com.itmecasey.aeroflux.client.screen.MachineScreen;
import com.itmecasey.aeroflux.client.screen.PipeConfigScreen;
import com.itmecasey.aeroflux.client.screen.TankScreen;
import com.itmecasey.aeroflux.client.sound.MachineSounds;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.item.GuidebookItem;
import com.itmecasey.aeroflux.machine.configurator.FluxConfiguratorItem;
import com.itmecasey.aeroflux.machine.FluxMachineBlock;
import com.itmecasey.aeroflux.machine.FluxMachineBlock;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModMenus;
import java.util.Map;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only setup: the screens, the tank renderer, and the two models that are built in code,
 * the conduit bundle and the plant grower's crop.
 */
@Mod.EventBusSubscriber(modid = AeroFlux.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    /** Gives the tanks their fluid-level renderer. */
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.TANK.get(), FluxTankRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CREATIVE_TANK.get(), FluxTankRenderer::new);
    }

    /** Pairs the menus with their screens, and lets the guidebook open its screen. */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        GuidebookItem.setOpener(GuideScreen::open);
        FluxConfiguratorItem.setOpener(ConfiguratorScreen::open);
        FluxMachineBlock.setClientTicker(MachineSounds::tick);
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.PIPE_CONFIG.get(), PipeConfigScreen::new);
            MenuScreens.register(ModMenus.CHUNK_LOADER.get(), ChunkLoaderScreen::new);
            MenuScreens.register(ModMenus.CONVERTER.get(), ConverterScreen::new);
            MenuScreens.register(ModMenus.COLLECTOR.get(), CollectorScreen::new);
            MenuScreens.register(ModMenus.TANK.get(), TankScreen::new);
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
        Map<BlockState, BlockStateModel> models = event.getResults().blockStateModels();
        for (BlockState state : ModBlocks.CONDUIT_BUNDLE.get().getStateDefinition().getPossibleStates()) {
            BlockStateModel baked = models.get(state);
            if (baked == null) {
                continue;
            }
            BundleBlockStateModel dynamic = BundleBlockStateModel.from(baked);
            if (dynamic == null) {
                AeroFlux.LOGGER.error("The conduit bundle model for {} was not built by the bundle loader; "
                        + "check models/block/conduit_bundle.json", state);
            } else {
                models.put(state, dynamic);
            }
        }
    }

    /**
     * Wraps every plant grower state's model so it also draws the planted crop in its chamber.
     * The casing models come from the block state files as usual; only the crop is added. Each
     * state gets its own wrapper because the crop has to know which way the grower faces.
     */
    @SubscribeEvent
    public static void wrapPlantGrowers(ModelEvent.ModifyBakingResult event) {
        Map<BlockState, BlockStateModel> models = event.getResults().blockStateModels();
        for (Tier tier : Tier.values()) {
            for (BlockState state : ModBlocks.machine(MachineType.PLANT_GROWER, tier).get().getStateDefinition().getPossibleStates()) {
                BlockStateModel casing = models.get(state);
                if (casing != null) {
                    Direction facing = state.hasProperty(FluxMachineBlock.FACING) ? state.getValue(FluxMachineBlock.FACING) : Direction.NORTH;
                    models.put(state, new PlantGrowerModel(casing, facing));
                }
            }
        }
    }
}
