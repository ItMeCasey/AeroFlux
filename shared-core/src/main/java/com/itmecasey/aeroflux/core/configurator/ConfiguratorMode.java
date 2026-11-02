package com.itmecasey.aeroflux.core.configurator;

import java.util.Locale;

/** What the Flux Configurator does when it is used on a block, in the order sneak-use in the air cycles them. */
public enum ConfiguratorMode {
    /** Sneak-use copies a machine, use pastes onto one. */
    PASTE,
    /** Two uses mark the corners of a box, a third pastes onto every machine inside it. */
    AREA;

    /** Lowercase name used in translation keys and the stored mode, such as {@code "area"}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public ConfiguratorMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** The mode with this id; {@link #PASTE} for anything unknown. */
    public static ConfiguratorMode byId(String id) {
        for (ConfiguratorMode mode : values()) {
            if (mode.id().equals(id)) {
                return mode;
            }
        }
        return PASTE;
    }
}
