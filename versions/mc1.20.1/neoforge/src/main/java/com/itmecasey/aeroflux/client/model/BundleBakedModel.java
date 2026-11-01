package com.itmecasey.aeroflux.client.model;

import com.itmecasey.aeroflux.core.conduit.Box;
import com.itmecasey.aeroflux.core.conduit.BundleState;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Vector3f;

/**
 * The model of a conduit bundle. It builds its quads on demand from the bundle's contents, which
 * the block entity supplies as model data, and caches them by the bundle's look so that the many
 * bundles sharing the same set and connections cost one build.
 */
public class BundleBakedModel implements IDynamicBakedModel {
    private static final FaceBakery FACE_BAKERY = new FaceBakery();

    private final Map<PipeType, TextureAtlasSprite> pipeSprites;
    private final TextureAtlasSprite nodeSprite;
    private final TextureAtlasSprite particleSprite;
    private final ResourceLocation modelLocation;
    private final Map<BundleState.ModelKey, List<BakedQuad>> cache = new ConcurrentHashMap<>();

    public BundleBakedModel(Map<PipeType, TextureAtlasSprite> pipeSprites, TextureAtlasSprite nodeSprite,
                            TextureAtlasSprite particleSprite, ResourceLocation modelLocation) {
        this.pipeSprites = pipeSprites;
        this.nodeSprite = nodeSprite;
        this.particleSprite = particleSprite;
        this.modelLocation = modelLocation;
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data,
                                    RenderType renderType) {
        // The pipes do not fill their block's faces, so nothing is ever culled against a neighbor.
        if (side != null) {
            return List.of();
        }
        BundleState bundle = data.get(BundleModelData.BUNDLE);
        if (bundle == null) {
            return List.of();
        }
        return cache.computeIfAbsent(bundle.modelKey(), key -> build(bundle));
    }

    private List<BakedQuad> build(BundleState bundle) {
        List<BakedQuad> quads = new ArrayList<>();
        for (PipeType type : bundle.set().types()) {
            TextureAtlasSprite sprite = pipeSprites.get(type);
            for (Box box : bundle.boxesOf(type)) {
                addBox(quads, box, sprite);
            }
        }
        for (Box box : bundle.connectorBoxes()) {
            addBox(quads, box, nodeSprite);
        }
        return List.copyOf(quads);
    }

    /** Adds the six faces of a box. Faces shared with another box are left in; they are hidden. */
    private void addBox(List<BakedQuad> quads, Box box, TextureAtlasSprite sprite) {
        Vector3f from = new Vector3f((float) box.minX(), (float) box.minY(), (float) box.minZ());
        Vector3f to = new Vector3f((float) box.maxX(), (float) box.maxY(), (float) box.maxZ());
        for (Direction direction : Direction.values()) {
            BlockFaceUV uv = new BlockFaceUV(uvFor(direction, box), 0);
            BlockElementFace face = new BlockElementFace(null, -1, "#pipe", uv);
            quads.add(FACE_BAKERY.bakeQuad(from, to, face, sprite, direction, BlockModelRotation.X0_Y0, null, true,
                    modelLocation));
        }
    }

    /** The default texture region for a face, the same mapping vanilla uses for unspecified UVs. */
    private static float[] uvFor(Direction direction, Box box) {
        float x1 = (float) box.minX();
        float y1 = (float) box.minY();
        float z1 = (float) box.minZ();
        float x2 = (float) box.maxX();
        float y2 = (float) box.maxY();
        float z2 = (float) box.maxZ();
        return switch (direction) {
            case DOWN -> new float[] {x1, 16.0F - z2, x2, 16.0F - z1};
            case UP -> new float[] {x1, z1, x2, z2};
            case NORTH -> new float[] {16.0F - x2, 16.0F - y2, 16.0F - x1, 16.0F - y1};
            case SOUTH -> new float[] {x1, 16.0F - y2, x2, 16.0F - y1};
            case WEST -> new float[] {z1, 16.0F - y2, z2, 16.0F - y1};
            case EAST -> new float[] {16.0F - z2, 16.0F - y2, 16.0F - z1, 16.0F - y1};
        };
    }

    @Override
    public boolean useAmbientOcclusion() {
        return true;
    }

    @Override
    public boolean isGui3d() {
        return false;
    }

    @Override
    public boolean usesBlockLight() {
        return true;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return particleSprite;
    }

    @Override
    public ItemTransforms getTransforms() {
        return ItemTransforms.NO_TRANSFORMS;
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}
