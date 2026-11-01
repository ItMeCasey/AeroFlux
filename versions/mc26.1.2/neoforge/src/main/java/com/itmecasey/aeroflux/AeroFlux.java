package com.itmecasey.aeroflux;

import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModCreativeTabs;
import com.itmecasey.aeroflux.registry.ModFluidTypes;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Entry point for AeroFlux (NeoForge 26.1.2).
 *
 * <p>AeroFlux collects Aero Flux from the sun by day and Moon Flux from the moon by night,
 * and uses those fluids to power its machines. The constructor wires up the registries and
 * the config; the content itself lives in the {@code registry} package and its siblings.
 */
@Mod(AeroFlux.MODID)
public class AeroFlux {
    /** Mod id; must match {@code mod_id} in gradle.properties and the mods metadata file. */
    public static final String MODID = "aeroflux";

    /** Shared logger for the mod. */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Constructed by NeoForge during mod loading.
     *
     * @param modEventBus  the mod-specific event bus, used for registry events
     * @param modContainer the container for this mod, used to register the config
     */
    public AeroFlux(IEventBus modEventBus, ModContainer modContainer) {
        ModFluidTypes.register(modEventBus);
        ModFluids.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, AeroFluxConfig.SPEC);

        LOGGER.info("AeroFlux skeleton loaded (NeoForge 26.1.2)");
    }
}
