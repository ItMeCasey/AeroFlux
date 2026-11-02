package com.itmecasey.aeroflux.core.machine;

import java.util.Locale;

/**
 * How a machine reacts to redstone, as the plan's redstone control lists it. The screen cycles
 * through these with a small button; the click reaches the server as a menu button number, so
 * each mode also has one (see {@link #buttonId()}).
 */
public enum RedstoneMode {
    /** Works whatever the signal. Every machine starts here. */
    ALWAYS,
    /** Works only while it gets a redstone signal. */
    HIGH,
    /** Works only while it gets no signal. */
    LOW,
    /** Finishes one job for each new signal, then waits for the next one. */
    PULSE;

    /**
     * The first menu button number of the redstone modes. It sits well above every
     * {@link SideCommand} number, so the two can share one menu.
     */
    public static final int BUTTON_BASE = 500;

    /** Lowercase name used in translation keys and saves, such as {@code "pulse"}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The next mode in the button's order, or the one before it when going backward; both wrap around. */
    public RedstoneMode cycle(boolean backward) {
        RedstoneMode[] all = values();
        int step = backward ? all.length - 1 : 1;
        return all[(ordinal() + step) % all.length];
    }

    /** The menu button number that selects this mode. */
    public int buttonId() {
        return BUTTON_BASE + ordinal();
    }

    /** The mode a menu button number selects, or {@code null} if the number is not one of them. */
    public static RedstoneMode fromButton(int id) {
        int index = id - BUTTON_BASE;
        return index >= 0 && index < values().length ? values()[index] : null;
    }

    /** The mode for a saved or synced index; anything out of range reads as {@link #ALWAYS}. */
    public static RedstoneMode byIndex(int index) {
        RedstoneMode[] all = values();
        return index >= 0 && index < all.length ? all[index] : ALWAYS;
    }

    /** The mode with this id, or {@link #ALWAYS} for an unknown one, such as from a damaged save. */
    public static RedstoneMode byId(String id) {
        for (RedstoneMode mode : values()) {
            if (mode.id().equals(id)) {
                return mode;
            }
        }
        return ALWAYS;
    }
}
