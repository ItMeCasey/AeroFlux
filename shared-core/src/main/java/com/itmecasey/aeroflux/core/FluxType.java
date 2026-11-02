package com.itmecasey.aeroflux.core;

import java.util.Locale;

/**
 * The Flux fluids: sunlight by day and moonlight by night, which collectors gather, and Twilight
 * Flux, which only the Flux Blender makes from the two.
 */
public enum FluxType {
    /** Gathered from the sun during the day; powers the production machines. */
    AERO(0xFFF2A8),
    /** Gathered from the moon at night; powers the magic machines. */
    MOON(0xD6E8FF),
    /** Blended from Aero and Moon Flux; never collected, used by the top recipes. */
    TWILIGHT(0xC77DDB);

    /** Both fluids glow faintly, at about the level of a dim torch. */
    public static final int LIGHT_LEVEL = 5;

    private final int tint;

    FluxType(int tint) {
        this.tint = tint;
    }

    /** RGB tint applied to the fluid, its bucket and every gauge that shows it. */
    public int tint() {
        return tint;
    }

    /** Whether collectors gather this flux from the sky. Twilight Flux is only ever blended. */
    public boolean collected() {
        return this != TWILIGHT;
    }

    /** Lowercase name used in registry ids, such as {@code "aero"} or {@code "moon"}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
