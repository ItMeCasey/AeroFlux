package com.itmecasey.aeroflux.lamp;

import com.itmecasey.aeroflux.core.FluxType;

/** The two lamps. Both keep monsters from spawning around them; each helps different plants grow. */
public enum LampKind {
    /** Burns Aero Flux; crops, saplings and the like grow faster in its light. */
    SUN("sun_lamp", "Sun Lamp", FluxType.AERO),
    /** Burns Moon Flux; keeps the plants of the dark (mushrooms, nether wart) growing even by day. */
    MOON("moon_lamp", "Moon Lamp", FluxType.MOON);

    private final String blockId;
    private final String displayName;
    private final FluxType flux;

    LampKind(String blockId, String displayName, FluxType flux) {
        this.blockId = blockId;
        this.displayName = displayName;
        this.flux = flux;
    }

    /** The registry name of the copper lamp, which the art is named after. */
    public String blockId() {
        return blockId;
    }

    public String displayName() {
        return displayName;
    }

    /** The flux the lamp burns. */
    public FluxType flux() {
        return flux;
    }
}
