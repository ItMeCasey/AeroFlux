package com.itmecasey.aeroflux.core.configurator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Flux Configurator's memory: eight preset slots, one of them selected. Copying a machine
 * writes into the selected slot and pasting reads from it, so the selected slot is the clipboard
 * and the other seven keep setups for later.
 *
 * <p>Instances are mutable; the item reads them from its stack, changes them and writes them
 * back. The stored form is one line of text per slot (see {@link #encode}).
 */
public final class ConfiguratorPresets {
    /** How many presets one Configurator holds. */
    public static final int SLOTS = 8;
    /** The longest name a preset can have, so the screen's row always fits it. */
    public static final int NAME_MAX = 24;

    /** One filled slot: a name and the settings. */
    public record Preset(String name, MachineSettings settings) {
    }

    private final Preset[] slots = new Preset[SLOTS];
    private int selected;

    /** Whether a number names a slot. */
    public static boolean validSlot(int slot) {
        return slot >= 0 && slot < SLOTS;
    }

    /** The selected slot, 0 to 7. */
    public int selected() {
        return selected;
    }

    /** Selects a slot; a number outside 0 to 7 is ignored. */
    public void select(int slot) {
        if (validSlot(slot)) {
            selected = slot;
        }
    }

    /** The preset in a slot, or {@code null} if it is empty or the number names no slot. */
    public Preset get(int slot) {
        return validSlot(slot) ? slots[slot] : null;
    }

    /** The preset in the selected slot, or {@code null}. */
    public Preset current() {
        return slots[selected];
    }

    /**
     * Stores settings in a slot. A slot that already had a name keeps it, since a player who named
     * a slot "Ore line" and copies a new furnace into it still thinks of it by that name; an empty
     * slot takes {@code name}.
     */
    public void store(int slot, String name, MachineSettings settings) {
        if (!validSlot(slot) || settings == null) {
            return;
        }
        String kept = slots[slot] != null ? slots[slot].name() : cleanName(name);
        slots[slot] = new Preset(kept, settings);
    }

    /**
     * Renames a filled slot.
     *
     * @return false if the slot is empty or the cleaned name is empty
     */
    public boolean rename(int slot, String name) {
        Preset preset = get(slot);
        String clean = cleanName(name);
        if (preset == null || clean.isEmpty()) {
            return false;
        }
        slots[slot] = new Preset(clean, preset.settings());
        return true;
    }

    /**
     * Empties a slot.
     *
     * @return false if it was already empty
     */
    public boolean clear(int slot) {
        if (get(slot) == null) {
            return false;
        }
        slots[slot] = null;
        return true;
    }

    /**
     * Tidies a name typed by a player: control characters and the section sign (which would start
     * a formatting code) are dropped, runs of spaces become one, and the result is trimmed and cut
     * to {@link #NAME_MAX} characters.
     */
    public static String cleanName(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        boolean space = false;
        for (int i = 0; i < raw.length() && out.length() < NAME_MAX; i++) {
            char c = raw.charAt(i);
            if (Character.isISOControl(c) || c == '§') {
                continue;
            }
            if (Character.isWhitespace(c)) {
                space = out.length() > 0;
                continue;
            }
            if (space && out.length() < NAME_MAX - 1) {
                out.append(' ');
            }
            space = false;
            out.append(c);
        }
        return out.toString();
    }

    // --- Text form ---------------------------------------------------------------------------

    /**
     * One line per slot: empty for an empty slot, otherwise the name, a tab and the encoded
     * settings. A cleaned name holds no tab, so the first tab always splits the two.
     */
    public List<String> encode() {
        List<String> lines = new ArrayList<>(SLOTS);
        for (Preset preset : slots) {
            lines.add(preset == null ? "" : preset.name() + "\t" + preset.settings().encode());
        }
        return lines;
    }

    /** Reads lines made by {@link #encode}; lines it cannot read leave their slot empty. */
    public static ConfiguratorPresets decode(List<String> lines, int selected) {
        ConfiguratorPresets presets = new ConfiguratorPresets();
        for (int slot = 0; slot < SLOTS && lines != null && slot < lines.size(); slot++) {
            String line = lines.get(slot);
            int tab = line == null ? -1 : line.indexOf('\t');
            if (tab < 0) {
                continue;
            }
            MachineSettings settings = MachineSettings.decode(line.substring(tab + 1));
            if (settings != null) {
                presets.slots[slot] = new Preset(cleanName(line.substring(0, tab)), settings);
            }
        }
        presets.select(selected);
        return presets;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ConfiguratorPresets that && selected == that.selected && Arrays.equals(slots, that.slots);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(slots) + selected;
    }
}
