package com.itmecasey.aeroflux.block.entity;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * A block that shows its fluid through glass: the Flux Tanks and the Creative Flux Tank. The tank
 * renderer draws whatever this hands it, up to its fill level.
 */
public interface TankView {
    /** The fluid to draw; slot 0 is the only one read. */
    ResourceHandler<FluidResource> tank();
}
