package com.itmecasey.aeroflux.core.conduit;

import java.util.Arrays;

/**
 * The settings of one Filter upgrade: a short list of item or fluid ids and whether the list says
 * what may pass (a whitelist) or what may not (a blacklist).
 *
 * <p>Entries are plain registry ids such as {@code minecraft:cobblestone}, so this class knows
 * nothing about the game. An id that no longer exists (its mod was removed) simply never matches.
 * A new filter is a whitelist with nothing on it, so it lets nothing through until the player
 * picks what it is for.
 */
public final class EndpointFilter {
    /** How many entries a filter holds; the screen shows them as a 3 by 3 grid. */
    public static final int SLOTS = 9;

    private boolean whitelist = true;
    /** The entries; {@code null} is an empty slot. */
    private final String[] entries = new String[SLOTS];

    /** Whether the entries are what may pass (true) or what may not (false). */
    public boolean isWhitelist() {
        return whitelist;
    }

    public void setWhitelist(boolean whitelist) {
        this.whitelist = whitelist;
    }

    /** The id in a slot, or {@code null} if the slot is empty or out of range. */
    public String entry(int slot) {
        return slot >= 0 && slot < SLOTS ? entries[slot] : null;
    }

    /**
     * Puts an id into a slot; {@code null} or a blank id empties it.
     *
     * @return false if the slot is out of range or already held exactly that
     */
    public boolean setEntry(int slot, String id) {
        if (slot < 0 || slot >= SLOTS) {
            return false;
        }
        String value = id == null || id.isBlank() ? null : id;
        if (value == null ? entries[slot] == null : value.equals(entries[slot])) {
            return false;
        }
        entries[slot] = value;
        return true;
    }

    /** Whether no slot holds anything. */
    public boolean isEmpty() {
        for (String entry : entries) {
            if (entry != null) {
                return false;
            }
        }
        return true;
    }

    /** Whether the list names this id in any slot. */
    public boolean contains(String id) {
        if (id == null) {
            return false;
        }
        for (String entry : entries) {
            if (id.equals(entry)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether something with this id may pass. A whitelist lets through only what it lists (so an
     * empty whitelist stops everything); a blacklist lets through everything it does not list.
     */
    public boolean accepts(String id) {
        if (id == null) {
            return false;
        }
        return whitelist == contains(id);
    }

    /** An independent copy, for snapshots that must not change when the player edits the filter. */
    public EndpointFilter copy() {
        EndpointFilter copy = new EndpointFilter();
        copy.whitelist = whitelist;
        System.arraycopy(entries, 0, copy.entries, 0, SLOTS);
        return copy;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EndpointFilter filter
                && whitelist == filter.whitelist
                && Arrays.equals(entries, filter.entries);
    }

    @Override
    public int hashCode() {
        return 31 * Boolean.hashCode(whitelist) + Arrays.hashCode(entries);
    }
}
