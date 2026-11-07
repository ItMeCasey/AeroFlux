package com.itmecasey.aeroflux.block.entity;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.core.DimensionRules;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

/**
 * Looks up the {@link DimensionRules} for the dimension a block is in.
 *
 * <p>The rules come from data packs: one file per dimension at
 * {@code data/<namespace>/aeroflux/dimension_rules/<path>.json}, so
 * {@code data/minecraft/aeroflux/dimension_rules/the_nether.json} covers {@code minecraft:the_nether}.
 * Every field is optional and falls back to "off" (or 1.0 for the multipliers). A dimension
 * without a file uses the built-in default: the Overworld collects both fluxes, everything else
 * collects nothing. The files are read again on {@code /reload}.
 */
@EventBusSubscriber(modid = AeroFlux.MODID)
public final class FluxDimensions {
    private static final Codec<DimensionRules.TimeSource> TIME_SOURCE = Codec.STRING.xmap(
            name -> DimensionRules.TimeSource.valueOf(name.toUpperCase(Locale.ROOT)),
            source -> source.name().toLowerCase(Locale.ROOT));

    /** The file format; see the class comment. */
    public static final Codec<DimensionRules> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("aero_enabled", false).forGetter(DimensionRules::aeroEnabled),
            Codec.BOOL.optionalFieldOf("moon_enabled", false).forGetter(DimensionRules::moonEnabled),
            Codec.DOUBLE.optionalFieldOf("aero_multiplier", 1.0).forGetter(DimensionRules::aeroMultiplier),
            Codec.DOUBLE.optionalFieldOf("moon_multiplier", 1.0).forGetter(DimensionRules::moonMultiplier),
            TIME_SOURCE.optionalFieldOf("time_source", DimensionRules.TimeSource.DIMENSION_TIME).forGetter(DimensionRules::timeSource),
            Codec.BOOL.optionalFieldOf("requires_sky", true).forGetter(DimensionRules::requiresSky),
            Codec.BOOL.optionalFieldOf("ignore_weather", false).forGetter(DimensionRules::ignoreWeather),
            Codec.BOOL.optionalFieldOf("chunk_loader_allowed", false).forGetter(DimensionRules::chunkLoaderAllowed)
    ).apply(instance, DimensionRules::new));

    /** The rules read from data packs, by dimension. Replaced whole on every reload. */
    private static volatile Map<Identifier, DimensionRules> loaded = Map.of();

    private FluxDimensions() {
    }

    /** The rules that apply in the given level. */
    public static DimensionRules rulesFor(Level level) {
        DimensionRules rules = loaded.get(level.dimension().identifier());
        if (rules != null) {
            return rules;
        }
        return level.dimension() == Level.OVERWORLD ? DimensionRules.OVERWORLD : DimensionRules.DISABLED;
    }

    /** Has the server read the rule files along with the rest of the data packs. */
    @SubscribeEvent
    public static void addReloadListener(AddServerReloadListenersEvent event) {
        event.addListener(Ids.mod("dimension_rules"), new Loader());
    }

    /** Reads every rule file into {@link #loaded}. */
    private static final class Loader extends SimpleJsonResourceReloadListener<DimensionRules> {
        Loader() {
            super(CODEC, FileToIdConverter.json("aeroflux/dimension_rules"));
        }

        @Override
        protected void apply(Map<Identifier, DimensionRules> rules, ResourceManager manager, ProfilerFiller profiler) {
            loaded = Map.copyOf(rules);
            AeroFlux.LOGGER.info("Loaded Flux rules for {} dimension(s)", rules.size());
        }
    }
}
