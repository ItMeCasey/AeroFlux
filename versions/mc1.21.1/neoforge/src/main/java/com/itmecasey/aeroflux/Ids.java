package com.itmecasey.aeroflux;

import net.minecraft.resources.ResourceLocation;

/** Builds resource identifiers. Minecraft 26.1 renamed {@code ResourceLocation} to {@code ResourceLocation}. */
public final class Ids {
    private Ids() {
    }

    /** An identifier in this mod's namespace, such as {@code aeroflux:block/flux_still}. */
    public static ResourceLocation mod(String path) {
        return ResourceLocation.fromNamespaceAndPath(AeroFlux.MODID, path);
    }

    /** An identifier in any namespace. */
    public static ResourceLocation of(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
