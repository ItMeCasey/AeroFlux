package com.itmecasey.aeroflux.block.entity;

import com.itmecasey.aeroflux.core.DimensionRules;
import net.minecraft.world.level.Level;

/**
 * Looks up the {@link DimensionRules} for the dimension a block is in.
 *
 * <p>For now only the Overworld collects flux. The data-driven per-dimension rule files from
 * the plan replace this lookup later without changing its callers.
 */
public final class FluxDimensions {
    private FluxDimensions() {
    }

    /** The rules that apply in the given level. */
    public static DimensionRules rulesFor(Level level) {
        return level.dimension() == Level.OVERWORLD ? DimensionRules.OVERWORLD : DimensionRules.DISABLED;
    }
}
