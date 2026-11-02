package com.itmecasey.aeroflux;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The client configuration, stored in {@code aeroflux-client.toml}: how the mod sounds and looks
 * for this player only. A server never reads it.
 */
public final class AeroFluxClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("Machine sounds. These only change what this player hears.").push("sounds");
    }

    private static final ModConfigSpec.BooleanValue SOUNDS = BUILDER
            .comment("Set to false to silence every AeroFlux sound: running machines and their start, stop and finish sounds.")
            .define("enabled", true);

    private static final ModConfigSpec.DoubleValue MACHINE_LOOP_VOLUME = BUILDER
            .comment("Volume of the sound a machine makes while it works, from 0 to 1. 0 turns the running sounds off.")
            .defineInRange("machineLoopVolume", 0.5, 0.0, 1.0);

    private static final ModConfigSpec.BooleanValue MACHINE_EFFECTS = BUILDER
            .comment("The short sounds when a machine starts, stops and finishes an item. They follow the game's Blocks volume.")
            .define("machineEffects", true);

    static {
        BUILDER.pop();
    }

    private static final ModConfigSpec.BooleanValue PARTICLES = BUILDER
            .comment("Small particles over working collectors and machines.")
            .define("particles", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private AeroFluxClientConfig() {
    }

    /** Whether AeroFlux makes any sound at all for this player. */
    public static boolean sounds() {
        return !SPEC.isLoaded() || SOUNDS.get();
    }

    /** The running sound's volume, 0 for off (also 0 when sounds are switched off). */
    public static float machineLoopVolume() {
        if (!SPEC.isLoaded()) {
            return 0.5F;
        }
        return SOUNDS.get() ? MACHINE_LOOP_VOLUME.get().floatValue() : 0.0F;
    }

    /** Whether the start, stop and finish sounds play. */
    public static boolean machineEffects() {
        return !SPEC.isLoaded() || (SOUNDS.get() && MACHINE_EFFECTS.get());
    }

    /** Whether collectors and machines give off particles. */
    public static boolean particles() {
        return !SPEC.isLoaded() || PARTICLES.get();
    }
}
