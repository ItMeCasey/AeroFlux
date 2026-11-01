package com.itmecasey.aeroflux.datagen;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModItems;
import java.util.Locale;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

/** Generates the American English ({@code en_us}) translations. */
public class ModLanguageProvider extends LanguageProvider {
    public ModLanguageProvider(PackOutput output) {
        super(output, AeroFlux.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        add("itemGroup." + AeroFlux.MODID, "AeroFlux");

        addItem(ModItems.FLUX_DUST, "Flux Dust");
        addItem(ModItems.AERO_FLUX_BUCKET, "Aero Flux Bucket");
        addItem(ModItems.MOON_FLUX_BUCKET, "Moon Flux Bucket");

        addBlock(ModBlocks.AERO_FLUX, "Aero Flux");
        addBlock(ModBlocks.MOON_FLUX, "Moon Flux");

        for (Tier tier : Tier.values()) {
            String name = displayName(tier);
            addBlock(ModBlocks.collector(tier), name + " Flux Collector");
            addBlock(ModBlocks.tank(tier), name + " Flux Tank");
        }

        addConduitTranslations();

        // Fluid type names show in fluid gauges and tooltips from other mods.
        add("fluid_type." + AeroFlux.MODID + ".aero_flux", "Aero Flux");
        add("fluid_type." + AeroFlux.MODID + ".moon_flux", "Moon Flux");
    }

    /** Names for the conduit bundle, the pipes, and the wrench's messages. */
    private void addConduitTranslations() {
        addBlock(ModBlocks.CONDUIT_BUNDLE, "Conduit Bundle");
        addBlock(ModBlocks.PIPE_NODE, "Pipe Node");
        addItem(ModItems.FLUX_WRENCH, "Flux Wrench");

        for (PipeType type : PipeType.values()) {
            String name = pipeName(type);
            addItem(ModItems.pipe(type), name + " Pipe");
            add("pipe." + AeroFlux.MODID + "." + type.id(), name);
        }

        for (Face face : Face.values()) {
            add("face." + AeroFlux.MODID + "." + face.name().toLowerCase(Locale.ROOT), capitalize(face.name()));
        }
        for (ConnectionMode mode : ConnectionMode.values()) {
            add("connection_mode." + AeroFlux.MODID + "." + mode.name().toLowerCase(Locale.ROOT), capitalize(mode.name()));
        }
        add("message." + AeroFlux.MODID + ".pipe_mode", "%s pipe, %s side: %s");
    }

    private static String pipeName(PipeType type) {
        return type == PipeType.ME ? "ME" : type == PipeType.DENSE_ME ? "Dense ME" : capitalize(type.name());
    }

    /** "NORTH" becomes "North". */
    private static String capitalize(String upperCase) {
        return upperCase.charAt(0) + upperCase.substring(1).toLowerCase(Locale.ROOT);
    }

    /** "copper" becomes "Copper". */
    private static String displayName(Tier tier) {
        String id = tier.id();
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }
}
