package com.itmecasey.aeroflux;

import com.itmecasey.aeroflux.core.Tier;
import java.util.EnumMap;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The common (server and client) configuration, stored in {@code aeroflux-common.toml}.
 *
 * <p>Tier numbers default to the values in {@link Tier} and can be rebalanced here without
 * touching code. Values are read lazily, so call the accessors only after the config has loaded.
 */
public final class AeroFluxConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue DEBUG_LOGGING = BUILDER
            .comment("Log extra detail about collectors, networks and chunk loaders.")
            .define("debugLogging", false);

    private static final Map<Tier, TierValues> TIER_VALUES = new EnumMap<>(Tier.class);

    static {
        BUILDER.comment("Per-tier stats for collectors and tanks. Defaults are the built-in balance.")
                .push("tiers");
        for (Tier tier : Tier.values()) {
            BUILDER.push(tier.id());
            TIER_VALUES.put(tier, new TierValues(
                    BUILDER.comment("Fluid made per tick in full, clear light (mB/t).")
                            .defineInRange("collectorRate", tier.collectorRate(), 1, 1_000_000),
                    BUILDER.comment("Collector internal buffer (buckets).")
                            .defineInRange("collectorBufferBuckets", tier.collectorBuffer() / 1_000, 1, 1_000_000),
                    BUILDER.comment("Storage tank capacity (mB).")
                            .defineInRange("tankCapacityMb", tier.tankCapacity(), 1_000, Integer.MAX_VALUE)));
            BUILDER.pop();
        }
        BUILDER.pop();
    }

    // Chunk loaders. The area and Flux cost of each tier are fixed in ChunkLoaderMath; only the
    // server's limits live here.
    static {
        BUILDER.comment("Flux Chunk Loaders.").push("chunkLoaders");
    }

    private static final ModConfigSpec.BooleanValue CHUNK_LOADERS_ENABLED = BUILDER
            .comment("Server-wide switch. When false, every chunk loader lets its chunks go and stays off.")
            .define("enabled", true);

    private static final ModConfigSpec.IntValue CHUNK_LOADER_GRACE_TICKS = BUILDER
            .comment("Ticks a loader keeps its chunks loaded after running out of Flux, so a slow supply does not make it flicker.")
            .defineInRange("graceTicks", 100, 0, 12_000);

    private static final ModConfigSpec.IntValue CHUNK_LOADERS_PER_PLAYER = BUILDER
            .comment("Running loaders one player may own across all dimensions. 0 means no limit.")
            .defineInRange("maxPerPlayer", 8, 0, 10_000);

    private static final ModConfigSpec.IntValue CHUNK_LOADERS_PER_DIMENSION = BUILDER
            .comment("Running loaders allowed in one dimension, whoever owns them. 0 means no limit.")
            .defineInRange("maxPerDimension", 0, 0, 10_000);

    static {
        BUILDER.pop();
    }

    /** The finished spec, registered with the mod container in the mod constructor. */
    public static final ModConfigSpec SPEC = BUILDER.build();

    private AeroFluxConfig() {
    }

    /** Whether extra diagnostic logging is enabled. */
    public static boolean debugLogging() {
        return DEBUG_LOGGING.get();
    }

    /** Collector output per tick in full, clear light, in mB/t, for the given tier. */
    public static int collectorRate(Tier tier) {
        return TIER_VALUES.get(tier).collectorRate.get();
    }

    /** Collector buffer size in mB for the given tier. */
    public static int collectorBuffer(Tier tier) {
        return TIER_VALUES.get(tier).collectorBufferBuckets.get() * 1_000;
    }

    /** Storage tank capacity in mB for the given tier. */
    public static int tankCapacity(Tier tier) {
        return TIER_VALUES.get(tier).tankCapacityMb.get();
    }

    /** Whether chunk loaders may load chunks at all on this server. */
    public static boolean chunkLoadersEnabled() {
        return CHUNK_LOADERS_ENABLED.get();
    }

    /** Ticks a chunk loader keeps its chunks after its Flux runs out. */
    public static int chunkLoaderGraceTicks() {
        return CHUNK_LOADER_GRACE_TICKS.get();
    }

    /** Running chunk loaders one player may own; 0 for no limit. */
    public static int chunkLoadersPerPlayer() {
        return CHUNK_LOADERS_PER_PLAYER.get();
    }

    /** Running chunk loaders one dimension may have; 0 for no limit. */
    public static int chunkLoadersPerDimension() {
        return CHUNK_LOADERS_PER_DIMENSION.get();
    }

    /** The three config entries that make up one tier. */
    private record TierValues(
            ModConfigSpec.IntValue collectorRate,
            ModConfigSpec.IntValue collectorBufferBuckets,
            ModConfigSpec.IntValue tankCapacityMb) {
    }
}
