package com.itmecasey.aeroflux;

import com.itmecasey.aeroflux.chunkloader.ChunkLoaderTickets;
import com.itmecasey.aeroflux.datagen.DataGenerators;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModCreativeTabs;
import com.itmecasey.aeroflux.registry.ModFluidTypes;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModItems;
import com.itmecasey.aeroflux.registry.ModMenus;
import com.itmecasey.aeroflux.registry.ModRecipes;
import com.mojang.logging.LogUtils;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Entry point for AeroFlux (Forge 26.1.2).
 *
 * <p>AeroFlux collects Aero Flux from the sun by day and Moon Flux from the moon by night,
 * and uses those fluids to power its machines. The constructor wires up the registries and
 * the config; the content itself lives in the {@code registry} package and its siblings.
 */
@Mod(AeroFlux.MODID)
public final class AeroFlux {
    /** Mod id; must match {@code mod_id} in the mods metadata file. */
    public static final String MODID = "aeroflux";

    /** Shared logger for the mod. */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Constructed by Forge during mod loading.
     *
     * @param context the loading context, used to reach the mod event bus group and register the config
     */
    public AeroFlux(FMLJavaModLoadingContext context) {
        var modBusGroup = context.getModBusGroup();

        ModFluidTypes.register(modBusGroup);
        ModFluids.register(modBusGroup);
        ModBlocks.register(modBusGroup);
        ModBlockEntities.register(modBusGroup);
        ModItems.register(modBusGroup);
        ModMenus.register(modBusGroup);
        ModRecipes.register(modBusGroup);
        ModCreativeTabs.register(modBusGroup);
        ChunkLoaderTickets.register(modBusGroup);
        GatherDataEvent.getBus(modBusGroup).addListener(DataGenerators::gatherData);
        context.registerConfig(ModConfig.Type.COMMON, AeroFluxConfig.SPEC);

        LOGGER.info("AeroFlux skeleton loaded (Forge 26.1.2)");
    }
}
