package com.itmecasey.aeroflux.core.conduit;

import java.util.Locale;

/**
 * The kinds of pipe that can share a conduit bundle.
 *
 * <p>A bundle has one lane per network type, in the fixed order below. The ME lane holds either
 * a normal ME pipe or a dense one, never both, so {@link #DENSE_ME} shares lane 5 with
 * {@link #ME}.
 */
public enum PipeType {
    FLUID(0, 0xC9CED3),
    ITEM(1, 0x4B5057),
    REDSTONE(2, 0xD8322C),
    ENERGY(3, 0xE8731A),
    GAS(4, 0xE6B422),
    ME(5, 0x8B4FD8),
    DENSE_ME(5, 0x5E2FA8);

    /** Number of lanes in a bundle: one per network type. */
    public static final int LANES = 6;

    private final int lane;
    private final int color;

    PipeType(int lane, int color) {
        this.lane = lane;
        this.color = color;
    }

    /** The lane this pipe occupies in a bundle, 0 for fluid up to 5 for ME. */
    public int lane() {
        return lane;
    }

    /** The pipe's main color as RGB (no alpha). */
    public int color() {
        return color;
    }

    /** Lowercase name used in ids and texture names, such as {@code "dense_me"}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Whether this pipe chooses among destinations. Redstone and the two ME pipes do not: a signal
     * has one value everywhere and AE2 routes its own network, so a distribution mode means nothing
     * to them.
     */
    public boolean usesDistribution() {
        return this == FLUID || this == ITEM || this == ENERGY || this == GAS;
    }

    /**
     * Whether this pipe can connect to blocks beside it. A dense ME pipe is a trunk that only
     * joins end to end, so it never attaches to side devices.
     */
    public boolean connectsToSides() {
        return this != DENSE_ME;
    }
}
