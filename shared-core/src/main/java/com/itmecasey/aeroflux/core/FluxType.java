package com.itmecasey.aeroflux.core;

import java.util.Locale;

/** The two fluids AeroFlux collects: sunlight by day and moonlight by night. */
public enum FluxType {
    /** Gathered from the sun during the day; powers the production machines. */
    AERO(0xFFF2A8),
    /** Gathered from the moon at night; powers the magic machines. */
    MOON(0xD6E8FF);

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

    /** Lowercase name used in registry ids, such as {@code "aero"} or {@code "moon"}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
