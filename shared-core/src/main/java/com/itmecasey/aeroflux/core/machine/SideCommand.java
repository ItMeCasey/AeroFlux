package com.itmecasey.aeroflux.core.machine;

import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;

/**
 * One click on the side-config window, as it travels from the screen to the server. A menu button
 * carries a single number, so each command is encoded into one; {@link #decode} turns a number
 * back into a command and refuses anything that is not one, which is what keeps a modified client
 * from sending nonsense.
 *
 * @param kind     what the click does
 * @param resource the kind of resource whose faces it changes
 * @param face     the face it is about; only meaningful for {@link Kind#SET} and {@link Kind#COPY_TO_ALL}
 * @param mode     the mode to set; only meaningful for {@link Kind#SET} and {@link Kind#ALL_TO}
 */
public record SideCommand(Kind kind, SideResource resource, Face face, ConnectionMode mode) {
    /** What a command does. */
    public enum Kind {
        /** Sets one face to one mode. */
        SET,
        /** Sets every face of the resource to one mode ("All Off"). */
        ALL_TO,
        /** Gives every face the mode the given face has ("Copy to all faces"). */
        COPY_TO_ALL,
        /** Puts the resource back to its defaults. */
        RESET
    }

    private static final int MODES = ConnectionMode.values().length;
    private static final int FACES = Face.values().length;
    private static final int RESOURCES = SideResource.values().length;

    // Each kind owns a range of numbers; the ranges are far enough apart not to meet.
    private static final int SET_BASE = 0;
    private static final int ALL_TO_BASE = 200;
    private static final int COPY_BASE = 300;
    private static final int RESET_BASE = 400;

    /** Sets one face. */
    public static SideCommand set(SideResource resource, Face face, ConnectionMode mode) {
        return new SideCommand(Kind.SET, resource, face, mode);
    }

    /** Sets every face to one mode. */
    public static SideCommand allTo(SideResource resource, ConnectionMode mode) {
        return new SideCommand(Kind.ALL_TO, resource, Face.UP, mode);
    }

    /** Copies one face's mode to all the others. */
    public static SideCommand copyToAll(SideResource resource, Face face) {
        return new SideCommand(Kind.COPY_TO_ALL, resource, face, ConnectionMode.OFF);
    }

    /** Restores the default modes. */
    public static SideCommand reset(SideResource resource) {
        return new SideCommand(Kind.RESET, resource, Face.UP, ConnectionMode.OFF);
    }

    /** The number that stands for this command. */
    public int encode() {
        int resourceIndex = resource.ordinal();
        return switch (kind) {
            case SET -> SET_BASE + (resourceIndex * FACES + face.index()) * MODES + mode.ordinal();
            case ALL_TO -> ALL_TO_BASE + resourceIndex * MODES + mode.ordinal();
            case COPY_TO_ALL -> COPY_BASE + resourceIndex * FACES + face.index();
            case RESET -> RESET_BASE + resourceIndex;
        };
    }

    /** The command a number stands for, or {@code null} if it stands for none. */
    public static SideCommand decode(int id) {
        if (id >= SET_BASE && id < SET_BASE + RESOURCES * FACES * MODES) {
            int value = id - SET_BASE;
            return set(SideResource.values()[value / (FACES * MODES)],
                    Face.values()[value / MODES % FACES], ConnectionMode.values()[value % MODES]);
        }
        if (id >= ALL_TO_BASE && id < ALL_TO_BASE + RESOURCES * MODES) {
            int value = id - ALL_TO_BASE;
            return allTo(SideResource.values()[value / MODES], ConnectionMode.values()[value % MODES]);
        }
        if (id >= COPY_BASE && id < COPY_BASE + RESOURCES * FACES) {
            int value = id - COPY_BASE;
            return copyToAll(SideResource.values()[value / FACES], Face.values()[value % FACES]);
        }
        if (id >= RESET_BASE && id < RESET_BASE + RESOURCES) {
            return reset(SideResource.values()[id - RESET_BASE]);
        }
        return null;
    }
}
