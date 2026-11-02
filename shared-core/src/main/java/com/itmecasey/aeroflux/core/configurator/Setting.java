package com.itmecasey.aeroflux.core.configurator;

import com.itmecasey.aeroflux.core.machine.SideResource;
import java.util.Locale;

/**
 * One thing the Flux Configurator copies from a machine. A paste reports which of these it set
 * and which the target had no use for, so the player can see why a field did not carry over.
 */
public enum Setting {
    ITEM_SIDES,
    FLUID_SIDES,
    ENERGY_SIDES,
    GAS_SIDES,
    REDSTONE_SIDES,
    REDSTONE_MODE,
    AUTO_IMPORT,
    AUTO_EXPORT,
    /** The Plant Grower's seed refill. */
    AUTO_INSERT,
    /** A switch only one kind of machine has, such as the Magnet's whitelist. */
    MACHINE_MODE;

    /** Lowercase name used in translation keys, such as {@code "item_sides"}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The face settings of one kind of resource. */
    public static Setting sidesOf(SideResource resource) {
        return switch (resource) {
            case ITEMS -> ITEM_SIDES;
            case FLUID -> FLUID_SIDES;
            case ENERGY -> ENERGY_SIDES;
            case GAS -> GAS_SIDES;
            case REDSTONE -> REDSTONE_SIDES;
        };
    }
}
