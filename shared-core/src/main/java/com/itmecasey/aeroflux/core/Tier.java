package com.itmecasey.aeroflux.core;

import java.util.Locale;

/**
 * The six quality levels shared by collectors, tanks and conduits.
 *
 * <p>All amounts are in millibuckets (mB); one bucket is 1,000 mB. The numbers here are
 * the built-in defaults and are meant to be rebalanced during the balance pass, so
 * nothing else should hard-code them.
 */
public enum Tier {
    COPPER(8, 8, 32, 0xB87333),
    IRON(20, 24, 128, 0xD8D8D8),
    GOLD(50, 64, 512, 0xFCDB5A),
    DIAMOND(120, 160, 2_048, 0x4AEDD9),
    NETHERITE(300, 400, 8_192, 0x4B3F3F),
    FLUXIUM(750, 1_000, 32_768, 0xFFF2A8);

    private static final int MB_PER_BUCKET = 1_000;

    private final int collectorRate;
    private final int collectorBuffer;
    private final int tankCapacity;
    private final int color;

    /**
     * @param collectorRate   fluid a collector makes per tick in full, clear light (mB/t)
     * @param collectorBuffer collector internal buffer, in buckets
     * @param tankCapacity    storage tank capacity, in buckets
     * @param color           RGB color used for frames, badges and GUI headers
     */
    Tier(int collectorRate, int collectorBuffer, int tankCapacity, int color) {
        this.collectorRate = collectorRate;
        this.collectorBuffer = collectorBuffer * MB_PER_BUCKET;
        this.tankCapacity = tankCapacity * MB_PER_BUCKET;
        this.color = color;
    }

    /** Fluid produced per tick by a collector of this tier in full, clear light, in mB/t. */
    public int collectorRate() {
        return collectorRate;
    }

    /** Capacity of a collector's internal buffer, in mB. */
    public int collectorBuffer() {
        return collectorBuffer;
    }

    /** Capacity of a storage tank of this tier, in mB. */
    public int tankCapacity() {
        return tankCapacity;
    }

    /** RGB color (no alpha) that identifies this tier in the UI. */
    public int color() {
        return color;
    }

    /** One-based tier number, 1 for copper through 6 for fluxium. */
    public int level() {
        return ordinal() + 1;
    }

    /** Lowercase name used in registry ids and translation keys, such as {@code "copper"}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
