package com.itmecasey.aeroflux.conduit;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

/**
 * A flat dark gray block where pipes break off or the set of pipes changes. It joins bundles of
 * any set on any face. For now it is a plain block; it will carry its own pipe data later.
 */
public class PipeNodeBlock extends Block {
    public static final MapCodec<PipeNodeBlock> CODEC = simpleCodec(PipeNodeBlock::new);

    public PipeNodeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }
}
