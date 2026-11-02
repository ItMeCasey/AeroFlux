package com.itmecasey.aeroflux.core.machine;

import com.itmecasey.aeroflux.core.conduit.Face;
import java.util.Locale;

/**
 * The names a player uses for the sides of a machine: front, back, left, right, top and bottom.
 *
 * <p>Left and right are what you see when you stand in front of the machine and look at its
 * front, which is also how the unfolded cube on the side-config screen is drawn. A machine that
 * faces north has its front toward you when you stand north of it looking south, so its left is
 * on your left, the east side.
 */
public enum FaceLabel {
    FRONT,
    BACK,
    LEFT,
    RIGHT,
    TOP,
    BOTTOM;

    /** Horizontal faces in clockwise order seen from above. */
    private static final Face[] CLOCKWISE = {Face.NORTH, Face.EAST, Face.SOUTH, Face.WEST};

    /** Lowercase name used in translation keys, such as {@code "front"}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * What a machine facing {@code facing} calls one of its faces. A machine that somehow faces
     * up or down is treated as facing north.
     */
    public static FaceLabel of(Face facing, Face face) {
        if (face == Face.UP) {
            return TOP;
        }
        if (face == Face.DOWN) {
            return BOTTOM;
        }
        int steps = Math.floorMod(clockwiseIndex(face) - clockwiseIndex(facing), 4);
        return switch (steps) {
            case 0 -> FRONT;
            case 1 -> LEFT;
            case 2 -> BACK;
            default -> RIGHT;
        };
    }

    /** The face of a machine facing {@code facing} that this label names; the reverse of {@link #of}. */
    public Face faceFor(Face facing) {
        return switch (this) {
            case TOP -> Face.UP;
            case BOTTOM -> Face.DOWN;
            case FRONT -> CLOCKWISE[clockwiseIndex(facing)];
            case LEFT -> CLOCKWISE[(clockwiseIndex(facing) + 1) % 4];
            case BACK -> CLOCKWISE[(clockwiseIndex(facing) + 2) % 4];
            case RIGHT -> CLOCKWISE[(clockwiseIndex(facing) + 3) % 4];
        };
    }

    private static int clockwiseIndex(Face face) {
        for (int i = 0; i < CLOCKWISE.length; i++) {
            if (CLOCKWISE[i] == face) {
                return i;
            }
        }
        return 0;
    }
}
