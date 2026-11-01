package com.itmecasey.aeroflux;

import net.minecraft.resources.ResourceLocation;

/**
 * Builds resource locations. The 1.20.1 NeoForge fork only has the plain constructor, while
 * the Forge build uses a factory method, so construction lives in one place per project.
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
        return new ResourceLocation(namespace, path);
    }
}
