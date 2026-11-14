package com.itmecasey.aeroflux;

import com.itmecasey.aeroflux.compat.create.CreateCompat;
import com.itmecasey.aeroflux.chunkloader.ChunkLoaderTickets;
import com.itmecasey.aeroflux.compat.modjs.AeroFluxScripts;
import com.itmecasey.aeroflux.machine.configurator.ConfiguratorPayload;
import com.itmecasey.aeroflux.machine.RecipeImportsSync;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModCreativeTabs;
import com.itmecasey.aeroflux.registry.ModFluidTypes;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModItems;
import com.itmecasey.aeroflux.registry.ModMenus;
import com.itmecasey.aeroflux.registry.ModRecipes;
import com.itmecasey.aeroflux.registry.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Entry point for AeroFlux (Minecraft 1.20.1).
 *
 * <p>AeroFlux collects Aero Flux from the sun by day and Moon Flux from the moon by night,
 * and uses those fluids to power its machines. The constructor wires up the registries and
 * the config; the content itself lives in the {@code registry} package and its siblings.
 *
 * <p>The NeoForge and Forge 1.20.1 builds share this code: NeoForge's 1.20.1 fork kept
 * Forge's package names and event system.
 */
@Mod(AeroFlux.MODID)
public class AeroFlux {
    /** Mod id; must match {@code mod_id} in gradle.properties and the mods metadata file. */
    public static final String MODID = "aeroflux";

    /** Shared logger for the mod. */
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Constructed by the mod loader during mod loading. */
    public AeroFlux() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModFluidTypes.register(modEventBus);
        // The Stress converter only exists with Create installed.
        CreateCompat.register(modEventBus);
        ModFluids.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModMenus.register(modEventBus);
        ModRecipes.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModSounds.register(modEventBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, AeroFluxConfig.SPEC);
        // Sound and particle settings only matter where the game is drawn.
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, AeroFluxClientConfig.SPEC);
        StorageSizesSync.register();
        ConfiguratorPayload.register();
        RecipeImportsSync.register();
        // The loader checks the chunk loaders' saved tickets as the world loads; this hands it the check.
        ChunkLoaderTickets.register();

        AeroFluxScripts.install();
        LOGGER.info("AeroFlux loaded (Minecraft 1.20.1)");
    }
}
