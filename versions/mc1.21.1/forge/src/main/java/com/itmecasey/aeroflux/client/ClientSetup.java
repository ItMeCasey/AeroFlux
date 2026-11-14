package com.itmecasey.aeroflux.client;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.client.model.BundleGeometryLoader;
import com.itmecasey.aeroflux.client.model.PlantGrowerModel;
import com.itmecasey.aeroflux.client.render.FluxTankRenderer;
import com.itmecasey.aeroflux.client.screen.ChunkLoaderScreen;
import com.itmecasey.aeroflux.client.screen.CollectorScreen;
import com.itmecasey.aeroflux.client.screen.ConfiguratorScreen;
import com.itmecasey.aeroflux.client.screen.ConverterScreen;
import com.itmecasey.aeroflux.client.screen.MachineScreen;
import com.itmecasey.aeroflux.client.screen.PipeConfigScreen;
import com.itmecasey.aeroflux.client.screen.TankScreen;
import com.itmecasey.aeroflux.client.sound.MachineSounds;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.machine.FluxMachineBlock;
import com.itmecasey.aeroflux.machine.configurator.FluxConfiguratorItem;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModMenus;
import java.util.Map;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only setup that cannot be expressed in data: renderers, models, screens and render layers. */
@Mod.EventBusSubscriber(modid = AeroFlux.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    /** Gives the tanks their fluid-level renderer. */
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.TANK.get(), FluxTankRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CREATIVE_TANK.get(), FluxTankRenderer::new);
    }

    /** Teaches the model system the {@code aeroflux:conduit_bundle} model type. */
    @SubscribeEvent
    public static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        // Forge prefixes the name with the active mod id, giving aeroflux:conduit_bundle.
        event.register("conduit_bundle", new BundleGeometryLoader());
    }

    /**
     * Wraps the plant grower's models so its glass chamber shows the crop it is growing (see
     * {@link PlantGrowerModel}). Every tier, facing and lit state gets the wrapper.
     */
    @SubscribeEvent
    public static void wrapPlantGrowers(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        for (Tier tier : Tier.values()) {
            for (BlockState state : ModBlocks.machine(MachineType.PLANT_GROWER, tier).get().getStateDefinition().getPossibleStates()) {
                ModelResourceLocation location = BlockModelShaper.stateToModelLocation(state);
                BakedModel casing = models.get(location);
                if (casing != null) {
                    models.put(location, new PlantGrowerModel(casing));
                }
            }
        }
    }

    /**
     * The fluid textures are slightly see-through, so the fluids need the translucent layer.
     * The menus are paired with their screens here too; Forge has no separate event for that.
     */
    @SubscribeEvent
    public static void setRenderLayers(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.PIPE_CONFIG.get(), PipeConfigScreen::new);
            MenuScreens.register(ModMenus.CHUNK_LOADER.get(), ChunkLoaderScreen::new);
            MenuScreens.register(ModMenus.CONVERTER.get(), ConverterScreen::new);
            MenuScreens.register(ModMenus.COLLECTOR.get(), CollectorScreen::new);
            MenuScreens.register(ModMenus.TANK.get(), TankScreen::new);
            for (MachineType machine : MachineType.values()) {
                MenuScreens.register(ModMenus.machine(machine).get(), MachineScreen::new);
            }
            RenderType translucent = RenderType.translucent();
            ItemBlockRenderTypes.setRenderLayer(ModFluids.AERO_FLUX.get(), translucent);
            ItemBlockRenderTypes.setRenderLayer(ModFluids.FLOWING_AERO_FLUX.get(), translucent);
            ItemBlockRenderTypes.setRenderLayer(ModFluids.MOON_FLUX.get(), translucent);
            ItemBlockRenderTypes.setRenderLayer(ModFluids.FLOWING_MOON_FLUX.get(), translucent);
            ItemBlockRenderTypes.setRenderLayer(ModFluids.TWILIGHT_FLUX.get(), translucent);
            ItemBlockRenderTypes.setRenderLayer(ModFluids.FLOWING_TWILIGHT_FLUX.get(), translucent);
            FluxConfiguratorItem.setOpener(ConfiguratorScreen::open);
            // The machines' running sounds are client only, so the block gets its client tick from here.
            FluxMachineBlock.setClientTicker(MachineSounds::tick);
        });
    }
}
