package com.itmecasey.aeroflux.client;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.client.model.BundleUnbakedModel;
import com.itmecasey.aeroflux.client.model.PlantGrowerModel;
import com.itmecasey.aeroflux.client.render.FluxTankRenderer;
import com.itmecasey.aeroflux.client.screen.ChunkLoaderScreen;
import com.itmecasey.aeroflux.client.screen.CollectorScreen;
import com.itmecasey.aeroflux.client.screen.ConverterScreen;
import com.itmecasey.aeroflux.client.screen.ConfiguratorScreen;
import com.itmecasey.aeroflux.machine.configurator.FluxConfiguratorItem;
import com.itmecasey.aeroflux.client.screen.MachineScreen;
import com.itmecasey.aeroflux.client.screen.PipeConfigScreen;
import com.itmecasey.aeroflux.client.screen.TankScreen;
import com.itmecasey.aeroflux.client.sound.MachineSounds;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.machine.FluxMachineBlock;
import com.itmecasey.aeroflux.machine.MachineRecipes;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModMenus;
import java.util.Map;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterBlockStateModels;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
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

    /**
     * Wraps every plant grower state's model so it also draws the planted crop in its chamber.
     * The casing models come from the block state files as usual; only the crop is added.
     */
    @SubscribeEvent
    public static void wrapPlantGrowers(ModelEvent.ModifyBakingResult event) {
        Map<BlockState, BlockStateModel> models = event.getBakingResult().blockStateModels();
        for (Tier tier : Tier.values()) {
            for (BlockState state : ModBlocks.machine(MachineType.PLANT_GROWER, tier).get().getStateDefinition().getPossibleStates()) {
                BlockStateModel casing = models.get(state);
                if (casing != null) {
                    models.put(state, new PlantGrowerModel(casing));
                }
            }
        }
    }

    /** Lets the Configurator open its screen and the machines play their running sounds, all client only. */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        FluxConfiguratorItem.setOpener(ConfiguratorScreen::open);
        FluxMachineBlock.setClientTicker(MachineSounds::tick);
    }

    /** Draws the fluid level inside each tank's glass, and the endless fluid of the creative tank. */
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.TANK.get(), FluxTankRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CREATIVE_TANK.get(), FluxTankRenderer::new);
    }

    /** Pairs the pipe menu with its screen. */
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.PIPE_CONFIG.get(), PipeConfigScreen::new);
        event.register(ModMenus.CHUNK_LOADER.get(), ChunkLoaderScreen::new);
        event.register(ModMenus.CONVERTER.get(), ConverterScreen::new);
        event.register(ModMenus.COLLECTOR.get(), CollectorScreen::new);
        event.register(ModMenus.TANK.get(), TankScreen::new);
        for (MachineType machine : MachineType.values()) {
            event.register(ModMenus.machine(machine).get(), MachineScreen::new);
        }
    }

    /**
     * Keeps the machine recipes the server sends, so the menus can judge items on the client too.
     * It runs first, so recipe viewers that read the same event find the recipes already stored.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRecipesReceived(RecipesReceivedEvent event) {
        MachineRecipes.receive(event.getRecipeMap());
    }

    @SubscribeEvent
    public static void registerFluidModels(RegisterFluidModelsEvent event) {
        event.register(fluidModel(FluxType.AERO), ModFluids.AERO_FLUX.get(), ModFluids.FLOWING_AERO_FLUX.get());
        event.register(fluidModel(FluxType.MOON), ModFluids.MOON_FLUX.get(), ModFluids.FLOWING_MOON_FLUX.get());
        event.register(fluidModel(FluxType.TWILIGHT), ModFluids.TWILIGHT_FLUX.get(), ModFluids.FLOWING_TWILIGHT_FLUX.get());
    }

    private static FluidModel.Unbaked fluidModel(FluxType flux) {
        // Fully opaque tint; the textures carry their own transparency.
        return new FluidModel.Unbaked(STILL, FLOWING, null, FluidTintSources.constant(0xFF000000 | flux.tint()));
    }
}
