package com.itmecasey.aeroflux.core.conduit;

import java.util.ArrayList;
import java.util.List;

/**
 * Works out which blocks lie on a straight run between two clicked positions, for tools that act
 * on a whole length of pipe at once. It knows nothing about the world; positions are plain
 * integer coordinates.
 */
public final class RunPlanner {
    /** The longest run a tool will act on in one use, so one click cannot strip a whole base. */
    public static final int MAX_LENGTH = 64;

    private RunPlanner() {
    }

    /** A block position as three integers. */
    public record Pos(int x, int y, int z) {
    }

    /**
     * The positions from {@code start} to {@code end}, both included, in order.
     *
     * @return the run, or an empty list if the two positions are not on one straight line (they
     *         must differ along at most one axis) or the run is longer than {@link #MAX_LENGTH}
     */
    public static List<Pos> line(Pos start, Pos end) {
        int dx = end.x() - start.x();
        int dy = end.y() - start.y();
        int dz = end.z() - start.z();
        int axes = (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);
        if (axes > 1) {
            return List.of();
        }
        int length = Math.abs(dx) + Math.abs(dy) + Math.abs(dz) + 1;
        if (length > MAX_LENGTH) {
            return List.of();
        }
        int stepX = Integer.signum(dx);
        int stepY = Integer.signum(dy);
        int stepZ = Integer.signum(dz);
        List<Pos> run = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            run.add(new Pos(start.x() + stepX * i, start.y() + stepY * i, start.z() + stepZ * i));
        }
        return run;
    }
}
