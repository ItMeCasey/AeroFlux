package com.itmecasey.aeroflux.block.entity;

import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * A block that shows its fluid through glass: the Flux Tanks and the Creative Flux Tank. The tank
 * renderer draws whatever this hands it, up to its fill level.
 */
public interface TankView {
    /** The fluid to draw; tank 0 is the only one read. */
    IFluidHandler tank();
}
