package com.itmecasey.aeroflux;

import com.itmecasey.aeroflux.chunkloader.ChunkLoaderTickets;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModCreativeTabs;
import com.itmecasey.aeroflux.registry.ModFluidTypes;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModItems;
import com.itmecasey.aeroflux.registry.ModMenus;
import com.itmecasey.aeroflux.registry.ModRecipes;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Entry point for AeroFlux (Forge 1.21.1).
 *
 * <p>AeroFlux collects Aero Flux from the sun by day and Moon Flux from the moon by night,
 * and uses those fluids to power its machines. The constructor wires up the registries and
 * the config; the content itself lives in the {@code registry} package and its siblings.
 */
@Mod(AeroFlux.MODID)
public class AeroFlux {
    /** Mod id; must match {@code mod_id} in the mods metadata file. */
    public static final String MODID = "aeroflux";

    /** Shared logger for the mod. */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Constructed by Forge during mod loading.
     *
     * @param context the loading context, used to reach the mod event bus and register the config
     */
    public AeroFlux(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        ModFluidTypes.register(modEventBus);
        ModFluids.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModMenus.register(modEventBus);
        ModRecipes.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        context.registerConfig(ModConfig.Type.COMMON, AeroFluxConfig.SPEC);
        // Forge checks the chunk loaders' saved tickets as the world loads; this hands it the check.
        ChunkLoaderTickets.register();

        LOGGER.info("AeroFlux skeleton loaded (Forge 1.21.1)");
    }
}
