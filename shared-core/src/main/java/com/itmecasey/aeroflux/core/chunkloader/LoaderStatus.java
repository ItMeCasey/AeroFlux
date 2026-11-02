package com.itmecasey.aeroflux.core.chunkloader;

import java.util.Locale;

/**
 * What a Flux Chunk Loader is doing, decided once per tick by {@link #decide}. Only
 * {@link #ACTIVE} and {@link #GRACE} hold chunk tickets; every other state releases them.
 */
public enum LoaderStatus {
    /** Paid for this tick; its chunks are loaded. */
    ACTIVE,
    /** Could not pay, but still inside the grace period, so the chunks stay loaded for now. */
    GRACE,
    /** Out of Flux and past the grace period; nothing is loaded. */
    NO_FLUX,
    /** The server turned chunk loaders off in its config. */
    DISABLED,
    /** This dimension's rules do not allow chunk loaders. */
    DIMENSION,
    /** The owner already runs as many loaders as the config allows. */
    CAP_REACHED;

    /** Whether a loader in this state keeps its chunks loaded. */
    public boolean holdsTickets() {
        return this == ACTIVE || this == GRACE;
    }

    /** Lowercase name used in translation keys, such as {@code "no_flux"}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The status with the given ordinal, or {@link #NO_FLUX} for anything out of range. */
    public static LoaderStatus byIndex(int index) {
        LoaderStatus[] all = values();
        return index >= 0 && index < all.length ? all[index] : NO_FLUX;
    }

    /**
     * Decides a loader's state for one tick. The server-wide switch comes first, then the
     * dimension, then the owner's cap. A loader already holding tickets never trips its own cap,
     * so a full owner keeps the loaders it has and only new ones wait. Running dry only releases
     * the chunks once the grace period has run out, which stops a loader fed by a slow pipe from
     * flickering on and off.
     *
     * @param enabled          the server allows chunk loaders at all
     * @param dimensionAllowed this dimension's rules allow chunk loaders
     * @param holding          the loader holds its tickets right now
     * @param underCap         the owner may start another loader
     * @param paid             the loader paid for this tick
     * @param graceLeft        ticks of grace left; only matters when the loader did not pay
     */
    public static LoaderStatus decide(boolean enabled, boolean dimensionAllowed, boolean holding,
                                      boolean underCap, boolean paid, int graceLeft) {
        if (!enabled) {
            return DISABLED;
        }
        if (!dimensionAllowed) {
            return DIMENSION;
        }
        if (!holding && !underCap) {
            return CAP_REACHED;
        }
        if (paid) {
            return ACTIVE;
        }
        return holding && graceLeft > 0 ? GRACE : NO_FLUX;
    }
}
