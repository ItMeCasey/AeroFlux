package com.itmecasey.aeroflux;

import net.minecraft.resources.ResourceLocation;

/**
 * Builds resource locations. Forge deprecated the two-argument {@code ResourceLocation}
 * constructor in 1.20.1 in favor of a factory method, so construction lives in one place.
 */
public final class Ids {
    private Ids() {
    }

    /** A location in this mod's namespace, such as {@code aeroflux:block/flux_still}. */
    public static ResourceLocation mod(String path) {
        return of(AeroFlux.MODID, path);
    }

    /** A location in any namespace. */
    public static ResourceLocation of(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    /** A location written as text, such as {@code aeroflux:block/flux_still} (no namespace means {@code minecraft}). */
    public static ResourceLocation parse(String location) {
        return ResourceLocation.parse(location);
    }
}
