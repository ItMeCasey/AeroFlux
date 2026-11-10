package com.itmecasey.aeroflux.compat.create;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the Stress converter's turning half shaft out of its front, the way Create draws the
 * shaft of its own motors. The converter's model has a hole in the front for it.
 */
public class StressConverterRenderer extends KineticBlockEntityRenderer<StressConverterBlockEntity> {
    public StressConverterRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(StressConverterBlockEntity converter, BlockState state) {
        return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state);
    }
}
