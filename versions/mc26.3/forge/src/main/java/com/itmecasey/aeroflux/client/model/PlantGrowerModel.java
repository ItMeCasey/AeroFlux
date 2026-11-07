package com.itmecasey.aeroflux.client.model;

import com.itmecasey.aeroflux.machine.CropDisplay;
import com.itmecasey.aeroflux.machine.FluxMachineBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jetbrains.annotations.Nullable;

/**
 * The plant grower's model: the casing as the art draws it, plus the planted crop standing in
 * the glass chamber. The crop is the crop's own model (whatever mod or resource pack made it),
 * shrunk to fit and baked into the chunk like the rest of the block, so it costs nothing per
 * frame. It is lit by the grow lamp, so it stays bright in the dark.
 */
public class PlantGrowerModel implements BlockStateModel {
    /** How much the crop is shrunk: a full-height crop then fits under the lamp. */
    private static final float SCALE = 0.55F;
    /** Where the bottom middle of the crop stands, in block units, for a grower facing north. */
    private static final float FLOOR_Y = 5.0F / 16.0F;
    /** The chamber sits a quarter pixel toward the front of the block. */
    private static final float CHAMBER_FORWARD = 0.25F / 16.0F;
    /** Full light, the grow lamp's. */
    private static final int LAMP_LIGHT = 15;

    private final BlockStateModel casing;
    private final Direction facing;
    private final Map<BlockState, List<BlockStateModelPart>> crops = new ConcurrentHashMap<>();

    /**
     * @param casing the model baked for this grower state
     * @param facing which way that state faces, so the crop stands in the chamber, not behind it
     */
    public PlantGrowerModel(BlockStateModel casing, Direction facing) {
        this.casing = casing;
        this.facing = facing;
    }

    // Forge deprecates the plain overloads in favor of the model data ones, but they stay abstract.
    @SuppressWarnings("deprecation")
    @Override
    public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
        casing.collectParts(random, parts);
    }

    @Override
    public void collectParts(RandomSource random, List<BlockStateModelPart> parts, ModelData data) {
        casing.collectParts(random, parts, data);
        BlockState crop = data.get(CropDisplay.SHOWN_CROP);
        if (crop != null) {
            parts.addAll(crops.computeIfAbsent(crop, key -> shrink(key, facing, random)));
        }
    }

    /** The crop's own model parts, shrunk into the chamber and lit by the lamp. */
    private static List<BlockStateModelPart> shrink(BlockState crop, Direction facing, RandomSource random) {
        BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(crop);
        List<BlockStateModelPart> source = new ArrayList<>();
        // The crop stands alone in the chamber, so it is asked for its parts with no data of its own.
        model.collectParts(random, source, ModelData.EMPTY);

        // The chamber's middle, moved toward whichever side is the front.
        float centerX = 0.5F + facing.getStepX() * CHAMBER_FORWARD;
        float centerZ = 0.5F + facing.getStepZ() * CHAMBER_FORWARD;
        List<BlockStateModelPart> shrunk = new ArrayList<>(source.size());
        for (BlockStateModelPart part : source) {
            QuadCollection.Builder quads = new QuadCollection.Builder();
            for (Direction side : Direction.values()) {
                for (BakedQuad quad : part.getQuads(side)) {
                    quads.addUnculledFace(shrink(quad, centerX, centerZ));
                }
            }
            for (BakedQuad quad : part.getQuads(null)) {
                quads.addUnculledFace(shrink(quad, centerX, centerZ));
            }
            // No ambient occlusion: the lamp lights the whole crop evenly.
            shrunk.add(new SimpleModelWrapper(quads.build(), false, part.particleMaterial()));
        }
        return List.copyOf(shrunk);
    }

    private static BakedQuad shrink(BakedQuad quad, float centerX, float centerZ) {
        BakedQuad.MaterialInfo material = quad.materialInfo();
        BakedQuad.MaterialInfo lit = new BakedQuad.MaterialInfo(material.sprite(), material.layer(), material.itemRenderType(),
                material.itemGlintRenderType(), material.itemGlintSpecialRenderType(), material.tintIndex(), material.shadeDirectionOverride(), LAMP_LIGHT);
        return new BakedQuad(shrink(quad.position0(), centerX, centerZ), shrink(quad.position1(), centerX, centerZ),
                shrink(quad.position2(), centerX, centerZ), shrink(quad.position3(), centerX, centerZ),
                quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(),
                quad.direction(), lit);
    }

    /** Scales a corner about the bottom middle of the block and stands it on the chamber floor. */
    private static Vector3fc shrink(Vector3fc corner, float centerX, float centerZ) {
        return new Vector3f(centerX + (corner.x() - 0.5F) * SCALE, FLOOR_Y + corner.y() * SCALE,
                centerZ + (corner.z() - 0.5F) * SCALE);
    }

    @Override
    @Deprecated
    public Material.Baked particleMaterial() {
        return casing.particleMaterial();
    }

    /**
     * The casing's flags. They already say translucent (the glass), and crops are cutout, which
     * needs no flag of its own.
     */
    @Override
    @Deprecated
    @BakedQuad.MaterialFlags
    public int materialFlags() {
        return casing.materialFlags();
    }
}
