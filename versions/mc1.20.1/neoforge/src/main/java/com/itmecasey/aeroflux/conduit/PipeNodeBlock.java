package com.itmecasey.aeroflux.conduit;

import net.minecraft.world.level.block.Block;

/**
 * A flat dark gray block where pipes break off or the set of pipes changes. It joins bundles of
 * any set on any face. For now it is a plain block; it will carry its own pipe data later.
 */
public class PipeNodeBlock extends Block {
    public PipeNodeBlock(Properties properties) {
        super(properties);
    }
}
