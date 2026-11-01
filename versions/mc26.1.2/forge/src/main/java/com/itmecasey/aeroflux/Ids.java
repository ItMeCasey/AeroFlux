package com.itmecasey.aeroflux;

import net.minecraft.resources.Identifier;

/** Builds resource identifiers. Minecraft 26.1 renamed {@code ResourceLocation} to {@code Identifier}. */
public final class Ids {
    private Ids() {
    }

    /** An identifier in this mod's namespace, such as {@code aeroflux:block/flux_still}. */
    public static Identifier mod(String path) {
        return Identifier.fromNamespaceAndPath(AeroFlux.MODID, path);
    }
}
