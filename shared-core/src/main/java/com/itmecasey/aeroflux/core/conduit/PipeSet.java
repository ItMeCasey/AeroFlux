package com.itmecasey.aeroflux.core.conduit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The set of pipes inside one bundle: at most one per lane.
 *
 * <p>Sets are immutable. Two blocks line up exactly when their sets are equal, because the
 * layout depends only on how many pipes there are and which ones, in lane order.
 */
public final class PipeSet {
    /** A bundle with no pipes at all. */
    public static final PipeSet EMPTY = new PipeSet(new PipeType[PipeType.LANES]);

    private final PipeType[] lanes;

    private PipeSet(PipeType[] lanes) {
        this.lanes = lanes;
    }

    /** Builds a set from the given pipes; a later pipe replaces an earlier one in the same lane. */
    public static PipeSet of(PipeType... types) {
        PipeSet set = EMPTY;
        for (PipeType type : types) {
            set = set.with(type);
        }
        return set;
    }

    /** Rebuilds a set from {@link #key()}. Unknown codes are ignored. */
    public static PipeSet fromKey(int key) {
        PipeType[] lanes = new PipeType[PipeType.LANES];
        for (int lane = 0; lane < PipeType.LANES; lane++) {
            int code = (key >> (lane * 3)) & 0b111;
            if (code != 0 && code <= PipeType.values().length) {
                PipeType type = PipeType.values()[code - 1];
                if (type.lane() == lane) {
                    lanes[lane] = type;
                }
            }
        }
        return new PipeSet(lanes);
    }

    /** The pipe in a lane, or {@code null} if the lane is empty. */
    public PipeType get(int lane) {
        return lanes[lane];
    }

    /** Whether this set holds exactly this pipe (a dense ME pipe is not an ME pipe). */
    public boolean contains(PipeType type) {
        return lanes[type.lane()] == type;
    }

    /** Whether the lane of this pipe type is taken, by this type or by its ME counterpart. */
    public boolean hasLaneFor(PipeType type) {
        return lanes[type.lane()] != null;
    }

    /** A copy of this set with the pipe added, replacing whatever was in its lane. */
    public PipeSet with(PipeType type) {
        PipeType[] copy = lanes.clone();
        copy[type.lane()] = type;
        return new PipeSet(copy);
    }

    /** A copy of this set with the given lane emptied. */
    public PipeSet withoutLane(int lane) {
        PipeType[] copy = lanes.clone();
        copy[lane] = null;
        return new PipeSet(copy);
    }

    /** Number of pipes in the set, 0 to 6. */
    public int size() {
        int count = 0;
        for (PipeType type : lanes) {
            if (type != null) {
                count++;
            }
        }
        return count;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    /** The pipes in lane order. */
    public List<PipeType> types() {
        List<PipeType> result = new ArrayList<>(PipeType.LANES);
        for (PipeType type : lanes) {
            if (type != null) {
                result.add(type);
            }
        }
        return result;
    }

    /**
     * Where this pipe sits among the pipes present, counting from 0 in lane order; this is its
     * layout slot. Returns -1 if the pipe is not in the set.
     */
    public int slotOf(PipeType type) {
        if (!contains(type)) {
            return -1;
        }
        int slot = 0;
        for (int lane = 0; lane < type.lane(); lane++) {
            if (lanes[lane] != null) {
                slot++;
            }
        }
        return slot;
    }

    /** A compact code for this set, three bits per lane, usable as a cache key or for saving. */
    public int key() {
        int key = 0;
        for (int lane = 0; lane < PipeType.LANES; lane++) {
            if (lanes[lane] != null) {
                key |= (lanes[lane].ordinal() + 1) << (lane * 3);
            }
        }
        return key;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PipeSet set && Arrays.equals(lanes, set.lanes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(lanes);
    }

    @Override
    public String toString() {
        return types().toString();
    }
}
