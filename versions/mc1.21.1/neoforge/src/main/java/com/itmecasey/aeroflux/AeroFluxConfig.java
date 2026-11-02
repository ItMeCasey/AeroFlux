package com.itmecasey.aeroflux;

import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.chunkloader.ChunkLoaderMath;
import com.itmecasey.aeroflux.core.converter.ConverterMath;
import com.itmecasey.aeroflux.core.converter.ConverterType;
import com.itmecasey.aeroflux.core.machine.MachineMath;
import com.itmecasey.aeroflux.lamp.FluxLampBlockEntity;
import java.util.EnumMap;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The common (server and client) configuration, stored in {@code aeroflux-common.toml}.
 *
 * <p>Tier numbers default to the values in {@link Tier} and can be rebalanced here without
 * touching code. Values are read lazily, so call the accessors only after the config has loaded.
 *
 * <p>Storage sizes are not read from here directly: code asks {@link StorageSizes}, which hands a
 * client the server's sizes instead of the client's own file, so screens, tooltips and the tank
 * picture match the server. A changed size reaches a block the next time it loads.
 */
public final class AeroFluxConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue DEBUG_LOGGING = BUILDER
            .comment("Log extra detail about collectors, networks and chunk loaders.")
            .define("debugLogging", false);

    private static final Map<Tier, TierValues> TIER_VALUES = new EnumMap<>(Tier.class);

    /**
     * The largest machine tank allowed: four Capacity Upgrades make it five times as big, and that
     * still has to fit in an int.
     */
    private static final int MAX_UPGRADABLE = Integer.MAX_VALUE / 5;

    static {
        BUILDER.comment("Per-tier stats: collector output and how much every block of the tier holds.",
                        "Defaults are the built-in balance. A changed size applies to a block the next time it loads.",
                        "A block that holds more than a smaller new size keeps it and takes nothing more until it drops below.")
                .push("tiers");
        for (Tier tier : Tier.values()) {
            BUILDER.push(tier.id());
            TIER_VALUES.put(tier, new TierValues(
                    BUILDER.comment("Fluid made per tick in full, clear light (mB/t).")
                            .defineInRange("collectorRate", tier.collectorRate(), 1, 1_000_000),
                    BUILDER.comment("Collector internal buffer (buckets).")
                            .defineInRange("collectorBufferBuckets", tier.collectorBuffer() / 1_000, 1, 1_000_000),
                    BUILDER.comment("Storage tank capacity (mB).")
                            .defineInRange("tankCapacityMb", tier.tankCapacity(), 1_000, Integer.MAX_VALUE),
                    BUILDER.comment("Machine Flux tank, and each extra tank (water, Twilight Flux), before Capacity Upgrades (mB).",
                                    "Each Capacity Upgrade adds this much again.")
                            .defineInRange("machineTankMb", MachineMath.inputCapacity(tier), 1_000, MAX_UPGRADABLE),
                    BUILDER.comment("Flux converter tank (mB).")
                            .defineInRange("converterTankMb", ConverterMath.tankCapacity(tier), 1_000, Integer.MAX_VALUE),
                    BUILDER.comment("Energy the Flux to FE converter holds before it must hand some on (FE).")
                            .defineInRange("converterBufferFe", ConverterMath.bufferCapacity(tier, ConverterType.ENERGY), 1_000, Integer.MAX_VALUE),
                    BUILDER.comment("Power the Flux to AE converter holds before it must hand some on (AE).")
                            .defineInRange("converterBufferAe", ConverterMath.bufferCapacity(tier, ConverterType.AE), 1_000, Integer.MAX_VALUE),
                    BUILDER.comment("Flux Chunk Loader tank (mB).")
                            .defineInRange("chunkLoaderTankMb", ChunkLoaderMath.capacity(tier), 1_000, Integer.MAX_VALUE)));
            BUILDER.pop();
        }
        BUILDER.pop();
    }

    private static final ModConfigSpec.IntValue LAMP_TANK = BUILDER
            .comment("Flux tank of every Sun and Moon Lamp, whatever its tier (mB).")
            .defineInRange("lampTankMb", FluxLampBlockEntity.CAPACITY, 1_000, Integer.MAX_VALUE);

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

    // The machines that reach into the world: what a server may want to limit or switch off.
    static {
        BUILDER.comment("Sky Controller, Moon Spawner and Animal Breeder.").push("worldMachines");
    }

    private static final ModConfigSpec.BooleanValue SKY_CONTROLLER_ENABLED = BUILDER
            .comment("When false, Sky Controllers do nothing when their buttons are pressed.")
            .define("skyControllerEnabled", true);

    private static final ModConfigSpec.IntValue SKY_DAY_COST = BUILDER
            .comment("Aero Flux one press of Day costs (mB).")
            .defineInRange("skyDayCost", 500, 0, 1_000_000);

    private static final ModConfigSpec.IntValue SKY_NIGHT_COST = BUILDER
            .comment("Moon Flux one press of Night costs (mB).")
            .defineInRange("skyNightCost", 500, 0, 1_000_000);

    private static final ModConfigSpec.IntValue SKY_WEATHER_COST = BUILDER
            .comment("Aero Flux one press of Clear/Rain costs (mB).")
            .defineInRange("skyWeatherCost", 250, 0, 1_000_000);

    private static final ModConfigSpec.BooleanValue SPAWNER_ENABLED = BUILDER
            .comment("When false, Moon Spawners stay idle.")
            .define("spawnerEnabled", true);

    private static final ModConfigSpec.IntValue SPAWNER_MAX_MOBS = BUILDER
            .comment("Most mobs one Moon Spawner cycle rolls loot for. Higher tiers roll more, up to this.")
            .defineInRange("spawnerMaxMobsPerCycle", 4, 1, 64);

    private static final ModConfigSpec.IntValue BREEDER_ANIMAL_CAP = BUILDER
            .comment("An Animal Breeder stops feeding once this many animals of the fed kind are in its range.")
            .defineInRange("breederAnimalCap", 24, 2, 1_000);

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

    /** A machine's Flux tank before Capacity Upgrades, in mB. */
    public static int machineTank(Tier tier) {
        return TIER_VALUES.get(tier).machineTankMb.get();
    }

    /** A converter's Flux tank, in mB. */
    public static int converterTank(Tier tier) {
        return TIER_VALUES.get(tier).converterTankMb.get();
    }

    /** What a converter's output buffer holds, in FE for the FE converter and AE for the AE one. */
    public static int converterBuffer(Tier tier, ConverterType type) {
        TierValues values = TIER_VALUES.get(tier);
        return (type == ConverterType.AE ? values.converterBufferAe : values.converterBufferFe).get();
    }

    /** A chunk loader's Flux tank, in mB. */
    public static int chunkLoaderTank(Tier tier) {
        return TIER_VALUES.get(tier).chunkLoaderTankMb.get();
    }

    /** A Sun or Moon Lamp's Flux tank, in mB. */
    public static int lampTank() {
        return LAMP_TANK.get();
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

    /** The config entries that make up one tier. */
    private record TierValues(
            ModConfigSpec.IntValue collectorRate,
            ModConfigSpec.IntValue collectorBufferBuckets,
            ModConfigSpec.IntValue tankCapacityMb,
            ModConfigSpec.IntValue machineTankMb,
            ModConfigSpec.IntValue converterTankMb,
            ModConfigSpec.IntValue converterBufferFe,
            ModConfigSpec.IntValue converterBufferAe,
            ModConfigSpec.IntValue chunkLoaderTankMb) {
    }

    /** Whether Sky Controllers work at all. */
    public static boolean skyControllerEnabled() {
        return SKY_CONTROLLER_ENABLED.get();
    }

    /** Aero Flux a press of Day costs, in mB. */
    public static int skyDayCost() {
        return SKY_DAY_COST.get();
    }

    /** Moon Flux a press of Night costs, in mB. */
    public static int skyNightCost() {
        return SKY_NIGHT_COST.get();
    }

    /** Aero Flux a press of Clear/Rain costs, in mB. */
    public static int skyWeatherCost() {
        return SKY_WEATHER_COST.get();
    }

    /** Whether Moon Spawners work at all. */
    public static boolean spawnerEnabled() {
        return SPAWNER_ENABLED.get();
    }

    /** The most mobs one Moon Spawner cycle rolls loot for. */
    public static int spawnerMaxMobsPerCycle() {
        return SPAWNER_MAX_MOBS.get();
    }

    /** How many animals of one kind a breeder's range may hold before it stops feeding them. */
    public static int breederAnimalCap() {
        return BREEDER_ANIMAL_CAP.get();
    }
}
