package com.itmecasey.aeroflux.client.model;

import com.itmecasey.aeroflux.machine.CropDisplay;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/**
 * The plant grower's model: its casing, with the crop it is growing drawn inside the glass
 * chamber. The crop is whatever block the seed plants, at the growth stage the job has reached
 * (see {@link CropDisplay}), taken from that block's own model, so modded crops and resource packs
 * look right without any art of ours. The crop's quads are shrunk into the chamber and lifted onto
 * its floor.
 */
public class PlantGrowerModel extends BakedModelWrapper<BakedModel> {
    /** The chamber's inside, in blocks: centered, and this wide and tall. */
    private static final float SCALE = 0.5F;
    private static final float FLOOR = 0.1875F;
    private static final float OFFSET = (1.0F - SCALE) / 2.0F;

    public PlantGrowerModel(BakedModel casing) {
        super(casing);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                    ModelData data, @Nullable RenderType renderType) {
        List<BakedQuad> quads = new ArrayList<>(super.getQuads(state, side, random, data, renderType));
        BlockState crop = data.get(CropDisplay.SHOWN_CROP);
        if (crop == null || side != null) {
            return quads;
        }
        BakedModel cropModel = Minecraft.getInstance().getBlockRenderer().getBlockModel(crop);
        // The crop is cutout; draw it with the casing's own layers when asked for no layer in particular.
        if (renderType != null && !cropModel.getRenderTypes(crop, random, ModelData.EMPTY).contains(renderType)
                && !super.getRenderTypes(state, random, data).contains(renderType)) {
            return quads;
        }
        for (Direction face : Direction.values()) {
            shrink(cropModel.getQuads(crop, face, random, ModelData.EMPTY, null), quads);
        }
        shrink(cropModel.getQuads(crop, null, random, ModelData.EMPTY, null), quads);
        return quads;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        BlockState crop = data.get(CropDisplay.SHOWN_CROP);
        ChunkRenderTypeSet casing = super.getRenderTypes(state, random, data);
        if (crop == null) {
            return casing;
        }
        BakedModel cropModel = Minecraft.getInstance().getBlockRenderer().getBlockModel(crop);
        return ChunkRenderTypeSet.union(casing, cropModel.getRenderTypes(crop, random, ModelData.EMPTY));
    }

    /** Copies each quad shrunk into the chamber. */
    private static void shrink(List<BakedQuad> source, List<BakedQuad> into) {
        for (BakedQuad quad : source) {
            int[] vertices = quad.getVertices().clone();
            int stride = vertices.length / 4;
            for (int v = 0; v < 4; v++) {
                int at = v * stride;
                vertices[at] = Float.floatToRawIntBits(OFFSET + Float.intBitsToFloat(vertices[at]) * SCALE);
                vertices[at + 1] = Float.floatToRawIntBits(FLOOR + Float.intBitsToFloat(vertices[at + 1]) * SCALE);
                vertices[at + 2] = Float.floatToRawIntBits(OFFSET + Float.intBitsToFloat(vertices[at + 2]) * SCALE);
            }
            into.add(new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade(),
                    quad.hasAmbientOcclusion()));
        }
    }
}
