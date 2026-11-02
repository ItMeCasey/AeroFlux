package com.itmecasey.aeroflux.core.machine;

import java.util.Locale;

/**
 * The kinds of things a machine can take in or give out, each with its own per-face
 * configuration. A machine only has settings for the kinds it actually uses; a furnace, for
 * example, works with {@link #ITEMS} and {@link #FLUID}.
 */
public enum SideResource {
    ITEMS,
    FLUID,
    ENERGY,
    GAS,
    REDSTONE;

    /** Lowercase name used in translation keys and saved data, such as {@code "items"}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The resource for a saved or sent index, or {@code null} if the index is out of range. */
    public static SideResource byIndex(int index) {
        SideResource[] all = values();
        return index >= 0 && index < all.length ? all[index] : null;
    }
}
