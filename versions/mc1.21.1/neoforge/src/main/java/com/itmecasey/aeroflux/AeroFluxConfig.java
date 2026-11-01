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
                    BUILDER.comment("Storage tank capacity (buckets).")
                            .defineInRange("tankCapacityBuckets", tier.tankCapacity() / 1_000, 1, 1_000_000)));
            BUILDER.pop();
        }
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
        return TIER_VALUES.get(tier).tankCapacityBuckets.get() * 1_000;
    }

    /** The three config entries that make up one tier. */
    private record TierValues(
            ModConfigSpec.IntValue collectorRate,
            ModConfigSpec.IntValue collectorBufferBuckets,
            ModConfigSpec.IntValue tankCapacityBuckets) {
    }
}
