package com.itmecasey.aeroflux.client.model;

import com.itmecasey.aeroflux.conduit.BundleShapes;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.Box;
import com.itmecasey.aeroflux.core.conduit.BundleState;
import com.itmecasey.aeroflux.core.conduit.ConduitLayout;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeTextures;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
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
 *
 * <p>The layout comes from {@link ConduitLayout}: thin pipe cores and arms, and a junction box,
 * plates and plugs where pipes meet machines. Every pipe type and tier has its own textures,
 * already colored and sized to the part they go on, so nothing is tinted, turned or stretched:
 * {@link PipeTextures} says which file a face takes, and an arm shows only as much of its texture
 * as it is long. The junction box and the machine plates share the dark connector texture.
 */
// Vanilla deprecates these hooks to stop other code calling them; overriding them is the intended way.
@SuppressWarnings("deprecation")
public class BundleBakedModel implements IDynamicBakedModel {
    private static final FaceBakery FACE_BAKERY = new FaceBakery();
    private static final float[] WHOLE = {0.0F, 0.0F, 16.0F, 16.0F};

    /** The pipe textures by name, such as {@code pipe_fluid_arm_v_gold}. */
    private final Map<String, TextureAtlasSprite> pipes;
    private final TextureAtlasSprite node;
    private final TextureAtlasSprite particle;
    /** The model file the quads came from; 1.20.1's face baker wants it for its error messages. */
    private final ResourceLocation modelLocation;
    private final Map<BundleState.ModelKey, List<BakedQuad>> cache = new ConcurrentHashMap<>();

    public BundleBakedModel(Map<String, TextureAtlasSprite> pipes, TextureAtlasSprite node, TextureAtlasSprite particle,
                            ResourceLocation modelLocation) {
        this.pipes = Map.copyOf(pipes);
        this.node = node;
        this.particle = particle;
        this.modelLocation = modelLocation;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, RandomSource random,
                                    ModelData data, @Nullable RenderType renderType) {
        // The pipes do not fill their block's faces, so nothing is ever culled against a neighbor.
        if (direction != null) {
            return List.of();
        }
        BundleState bundle = data.get(BundleModelData.BUNDLE);
        if (bundle == null) {
            return List.of();
        }
        return cache.computeIfAbsent(bundle.modelKey(), key -> build(bundle));
    }

    /**
     * Builds the quads from the bundle's layout: pipe cores, arms and plugs in their own pipe's
     * textures, and the junction box and plates in the shared connector texture.
     */
    private List<BakedQuad> build(BundleState bundle) {
        List<BakedQuad> quads = new ArrayList<>();
        for (ConduitLayout.Piece piece : ConduitLayout.of(bundle).pieces()) {
            switch (piece.kind()) {
                case CORE -> addCore(quads, piece, bundle.tier(piece.type().lane()));
                case ARM -> addArm(quads, piece, bundle.tier(piece.type().lane()));
                case PLUG -> addPlug(quads, piece, bundle.tier(piece.type().lane()));
                case JUNCTION, PLATE -> addConnectorBox(quads, piece.box());
            }
        }
        return List.copyOf(quads);
    }

    /**
     * Adds a pipe's core. A core on a straight run has sides and ends like an arm; a core with no
     * single direction (a corner, a branch, or a pipe with no connections) shows its end texture
     * on every face.
     */
    private void addCore(List<BakedQuad> quads, ConduitLayout.Piece piece, Tier tier) {
        Box box = piece.box();
        if (!BundleShapes.hasVolume(box)) {
            return;
        }
        PipeType type = piece.type();
        for (Direction direction : Direction.values()) {
            Face face = Face.byIndex(direction.get3DDataValue());
            String part = piece.axis() < 0 || PipeTextures.isEnd(piece.axis(), face)
                    ? "core_end"
                    : PipeTextures.vertical(piece.axis(), face) ? "core_v" : "core_h";
            quads.add(bake(box, direction, pipe(type, tier, part), WHOLE));
        }
    }

    /** Adds an arm: its sides show only as much of the arm texture as the arm is long. */
    private void addArm(List<BakedQuad> quads, ConduitLayout.Piece piece, Tier tier) {
        Box box = piece.box();
        if (!BundleShapes.hasVolume(box)) {
            return;
        }
        PipeType type = piece.type();
        int axis = piece.axis();
        double length = switch (axis) {
            case 0 -> box.maxX() - box.minX();
            case 1 -> box.maxY() - box.minY();
            default -> box.maxZ() - box.minZ();
        };
        for (Direction direction : Direction.values()) {
            Face face = Face.byIndex(direction.get3DDataValue());
            if (PipeTextures.isEnd(axis, face)) {
                quads.add(bake(box, direction, pipe(type, tier, "arm_end"), WHOLE));
            } else {
                boolean vertical = PipeTextures.vertical(axis, face);
                quads.add(bake(box, direction, pipe(type, tier, vertical ? "arm_v" : "arm_h"),
                        PipeTextures.armUv(vertical, length)));
            }
        }
    }

    /**
     * Adds a pipe's plug on a machine plate. The end that faces into the bundle shows the glyph of
     * what the pipe carries; the end against the plate is never seen and takes the connector
     * texture.
     */
    private void addPlug(List<BakedQuad> quads, ConduitLayout.Piece piece, Tier tier) {
        Box box = piece.box();
        if (!BundleShapes.hasVolume(box)) {
            return;
        }
        PipeType type = piece.type();
        for (Direction direction : Direction.values()) {
            Face face = Face.byIndex(direction.get3DDataValue());
            TextureAtlasSprite sprite;
            if (face == piece.face()) {
                sprite = node;
            } else if (face == piece.face().opposite()) {
                sprite = pipe(type, tier, PipeTextures.PLUG_FACE);
            } else {
                sprite = pipe(type, tier, PipeTextures.vertical(piece.axis(), face) ? "plug_v" : "plug_h");
            }
            quads.add(bake(box, direction, sprite, WHOLE));
        }
    }

    private TextureAtlasSprite pipe(PipeType type, Tier tier, String part) {
        return pipes.get(PipeTextures.name(type, tier, part));
    }

    /** Adds the six faces of a junction box or plate. */
    private void addConnectorBox(List<BakedQuad> quads, Box box) {
        if (!BundleShapes.hasVolume(box)) {
            return;
        }
        for (Direction direction : Direction.values()) {
            quads.add(bake(box, direction, node, WHOLE));
        }
    }

    /** One face of a box, as the texture is: no tint and no rotation. */
    private BakedQuad bake(Box box, Direction direction, TextureAtlasSprite sprite, float[] uvs) {
        Vector3f from = new Vector3f((float) box.minX(), (float) box.minY(), (float) box.minZ());
        Vector3f to = new Vector3f((float) box.maxX(), (float) box.maxY(), (float) box.maxZ());
        BlockElementFace face = new BlockElementFace(null, -1, "#pipe", new BlockFaceUV(uvs.clone(), 0));
        return FACE_BAKERY.bakeQuad(from, to, face, sprite, direction, BlockModelRotation.X0_Y0, null, true, modelLocation);
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

    /** Forge asks for the particle sprite with model data; this model has just the one. */
    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        return particle;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return particle;
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
