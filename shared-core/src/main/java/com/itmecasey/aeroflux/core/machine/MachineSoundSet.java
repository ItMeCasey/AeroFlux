package com.itmecasey.aeroflux.core.machine;

import java.util.List;

/**
 * Which running sound each machine plays while it works. There are eight loops, made for the
 * first eight machines; the later machines borrow the loop that fits them best, and the Magnet,
 * which only pulls items in, stays quiet apart from the start and stop sounds every machine makes.
 * The loops are the sound events {@code aeroflux:machine.<loop>.loop}.
 */
public final class MachineSoundSet {
    /** Every loop there is, in the order the sound files were made. */
    public static final List<String> LOOPS = List.of(
            "furnace", "grinder", "smelter", "sawmill", "infuser", "enchanter", "disenchanter", "grower");

    private MachineSoundSet() {
    }

    /** The loop a machine plays while it works, such as {@code "grinder"}, or {@code null} for none. */
    public static String loopOf(MachineType machine) {
        return switch (machine) {
            case FURNACE -> "furnace";
            // Grinding, crushing and shaking: the Sifter and the Crafter's gears sound like the Grinder.
            case GRINDER, SIFTER, CRAFTER -> "grinder";
            case ALLOY_SMELTER -> "smelter";
            case SAWMILL -> "sawmill";
            // Fluids being worked: soaking, washing and blending.
            case INFUSER, PURIFIER, BLENDER -> "infuser";
            // Magic: the enchanters, the Repairer's experience and the Sky Controller's weather.
            case ENCHANTER, REPAIRER, SKY_CONTROLLER -> "enchanter";
            case DISENCHANTER, SPAWNER -> "disenchanter";
            // Living things: plants and animals.
            case PLANT_GROWER, BREEDER -> "grower";
            case MAGNET -> null;
        };
    }

    /**
     * Whether a sound event path (such as {@code machine.grinder.loop}) is one of the running
     * loops, as opposed to the short start, stop and finish sounds.
     */
    public static boolean isLoop(String soundPath) {
        return soundPath.startsWith("machine.") && soundPath.endsWith(".loop");
    }
}
