package com.itmecasey.aeroflux.core.configurator;

import com.itmecasey.aeroflux.core.conduit.RunPlanner;
import java.util.List;

/**
 * What a paste overwrote, kept so the paste can be taken back once. It holds each changed
 * machine's settings from before the paste and goes stale after {@link #LIFETIME} ticks, so an
 * undo can never wipe out changes made long after the paste.
 *
 * @param dimension the dimension the machines are in
 * @param before    each machine's position with its settings before the paste
 * @param madeAt    the game time of the paste, in ticks
 */
public record PasteUndo(String dimension, List<Entry> before, long madeAt) {
    /** How long a paste can be undone: one minute. */
    public static final long LIFETIME = 20L * 60;

    /** One machine's old settings. */
    public record Entry(RunPlanner.Pos pos, MachineSettings settings) {
    }

    public PasteUndo {
        before = List.copyOf(before);
    }

    /** Whether the paste can still be undone at {@code now}, in the given dimension. */
    public boolean usable(String inDimension, long now) {
        return dimension.equals(inDimension) && now >= madeAt && now - madeAt <= LIFETIME && !before.isEmpty();
    }
}
