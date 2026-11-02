package com.itmecasey.aeroflux;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The client configuration, stored in {@code aeroflux-client.toml}: how the mod sounds and looks
 * for this player only. A server never reads it.
 */
public final class AeroFluxClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.DoubleValue MACHINE_LOOP_VOLUME = BUILDER
            .comment("Volume of the sound a machine makes while it works, from 0 to 1. 0 turns the running sounds off;",
                    "the short start, stop and finish sounds follow the game's Blocks volume as usual.")
            .defineInRange("machineLoopVolume", 0.5, 0.0, 1.0);

    private static final ModConfigSpec.BooleanValue PARTICLES = BUILDER
            .comment("Small particles over working collectors and machines.")
            .define("particles", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private AeroFluxClientConfig() {
    }

    /** The running sound's volume, 0 for off. Before the config has loaded it is the default. */
    public static float machineLoopVolume() {
        return SPEC.isLoaded() ? MACHINE_LOOP_VOLUME.get().floatValue() : 0.5F;
    }

    /** Whether collectors and machines give off particles. */
    public static boolean particles() {
        return !SPEC.isLoaded() || PARTICLES.get();
    }
}
