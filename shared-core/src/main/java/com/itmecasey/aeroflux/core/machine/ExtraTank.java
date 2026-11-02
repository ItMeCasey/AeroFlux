package com.itmecasey.aeroflux.core.machine;

/**
 * A fluid tank a machine has on top of its Flux tank, such as the Purifier's water or the
 * Blender's Moon Flux and Twilight Flux. Each one holds a single fluid, named by its registry id
 * so this class needs nothing from the game.
 *
 * @param fluid  the registry id of the only fluid the tank takes, such as {@code "minecraft:water"}
 * @param output true for a tank the machine fills and pipes drain; false for one it drinks from
 */
public record ExtraTank(String fluid, boolean output) {
    public static final ExtraTank WATER = new ExtraTank("minecraft:water", false);
    public static final ExtraTank MOON_FLUX = new ExtraTank("aeroflux:moon_flux", false);
    public static final ExtraTank TWILIGHT_FLUX = new ExtraTank("aeroflux:twilight_flux", true);
}
