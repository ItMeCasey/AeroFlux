package com.itmecasey.aeroflux.converter;

import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.converter.ConverterType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * What the converter screen needs to know about a converter. The FE and AE converters are one
 * block entity ({@link FluxConverterBlockEntity}); the Stress converter has to be a Create block
 * entity and lives in the Create compat package, so the menu talks to both through this.
 */
public interface ConverterView {
    BlockPos getBlockPos();

    boolean isRemoved();

    ConverterType type();

    Tier tier();

    /** Which way the converter's front points. */
    Direction facing();

    /** Flux in the tank, in mB. */
    int fluxAmount();

    @Nullable
    FluxType storedFlux();

    /** What the bar on the right shows: the buffer in FE or AE, or the stress in use in SU. */
    int stored();

    /** The bar's full mark: the buffer's size, or the stress capacity. */
    int bufferCapacity();

    /** What it puts out: FE or AE per tick, or the stress capacity it supplies right now. */
    int output();

    /** Flux burned last tick, in mB. */
    int lastBurn();

    /** The faces that put power out, one bit per {@link Face} index. */
    int outputMask();

    /** Switches power output on or off at one face, where the converter allows it. */
    void toggleOutput(Face face);
}
