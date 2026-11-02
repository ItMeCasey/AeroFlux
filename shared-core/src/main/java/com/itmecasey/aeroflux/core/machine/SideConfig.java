package com.itmecasey.aeroflux.core.machine;

import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import java.util.Arrays;

/**
 * The mode of each of a machine's six faces for one kind of resource. Instances are mutable;
 * the owning block entity saves them and tells neighbors when they change.
 *
 * <p>The whole configuration packs into one small number (see {@link #pack}), which is how it
 * is saved and how it travels to an open screen: six faces with five modes each come to 15,625
 * combinations, which fits a signed 16-bit value, the size of a menu data slot.
 */
public final class SideConfig {
    private static final int MODES = ConnectionMode.values().length;
    private static final int FACES = Face.values().length;
    /** One more than the largest packed value. */
    private static final int COMBINATIONS = 15_625;

    private final ConnectionMode[] modes = new ConnectionMode[FACES];

    /** A configuration with every face set to the same mode. */
    public SideConfig(ConnectionMode mode) {
        Arrays.fill(modes, mode);
    }

    /**
     * What a fresh machine starts with. Items go in from the top and come out of the bottom, and
     * the four sides do both, so a hopper or pipe on any side just works. Fluid only goes in. The
     * other kinds start off, since a machine that does not use them never shows them.
     */
    public static SideConfig defaultFor(SideResource resource) {
        return switch (resource) {
            case ITEMS -> {
                SideConfig config = new SideConfig(ConnectionMode.OPEN);
                config.set(Face.UP, ConnectionMode.IMPORT);
                config.set(Face.DOWN, ConnectionMode.EXPORT);
                yield config;
            }
            case FLUID -> new SideConfig(ConnectionMode.IMPORT);
            default -> new SideConfig(ConnectionMode.OFF);
        };
    }

    /** The order the screen cycles a face through when it is clicked: Import, Export, Both, Off, Blocked. */
    private static final ConnectionMode[] CYCLE = {
            ConnectionMode.IMPORT, ConnectionMode.EXPORT, ConnectionMode.OPEN, ConnectionMode.OFF, ConnectionMode.BLOCKED
    };

    /** The mode a click moves a face to; a backward click (right-click) goes the other way around. */
    public static ConnectionMode cycle(ConnectionMode current, boolean backward) {
        int index = 0;
        for (int i = 0; i < CYCLE.length; i++) {
            if (CYCLE[i] == current) {
                index = i;
            }
        }
        return CYCLE[Math.floorMod(index + (backward ? -1 : 1), CYCLE.length)];
    }

    /** The mode at a face. */
    public ConnectionMode mode(Face face) {
        return modes[face.index()];
    }

    /** Sets the mode at a face. */
    public void set(Face face, ConnectionMode mode) {
        modes[face.index()] = mode;
    }

    /** Sets every face to one mode. */
    public void setAll(ConnectionMode mode) {
        Arrays.fill(modes, mode);
    }

    /** Copies another configuration's faces into this one. */
    public void copyFrom(SideConfig other) {
        System.arraycopy(other.modes, 0, modes, 0, FACES);
    }

    /** An independent copy. */
    public SideConfig copy() {
        SideConfig copy = new SideConfig(ConnectionMode.OFF);
        copy.copyFrom(this);
        return copy;
    }

    /** Packs the six modes into a number from 0 up to 15,624, one base-5 digit per face. */
    public int pack() {
        int value = 0;
        for (int face = FACES - 1; face >= 0; face--) {
            value = value * MODES + modes[face].ordinal();
        }
        return value;
    }

    /**
     * Reads a number made by {@link #pack}. Any value is accepted, so damaged data still gives a
     * valid configuration: a negative value counts from the other end and anything too large wraps.
     */
    public static SideConfig unpack(int packed) {
        SideConfig config = new SideConfig(ConnectionMode.OFF);
        int value = Math.floorMod(packed, COMBINATIONS);
        for (int face = 0; face < FACES; face++) {
            config.modes[face] = ConnectionMode.values()[value % MODES];
            value /= MODES;
        }
        return config;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SideConfig config && Arrays.equals(modes, config.modes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(modes);
    }

    @Override
    public String toString() {
        return Arrays.toString(modes);
    }
}
