package com.itmecasey.aeroflux.client.model;

import com.itmecasey.aeroflux.conduit.BundleShapes;
import com.itmecasey.aeroflux.core.conduit.Box;
import com.itmecasey.aeroflux.core.conduit.BundleState;
import com.itmecasey.aeroflux.core.conduit.ConduitLayout;
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
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.QuadTransformers;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The model of a conduit bundle. It builds its quads on demand from the bundle's contents, which
 * the block entity supplies as model data, and caches them by the bundle's look so that the many
 * bundles sharing the same set and connections cost one build.
 *
 * <p>The layout comes from {@link ConduitLayout}: thin pipe cores and arms, and a junction box,
 * plates and plugs where pipes meet machines. Pipes are plain: every pipe uses the same two white
 * textures, tinted with its own color. The side texture has a dark line down both long edges and
 * the end texture a dark frame, so the edges stay readable while the color says what the pipe
 * carries. Side faces are turned so the lines always run along the pipe.
 */
public class BundleBakedModel implements IDynamicBakedModel {
    private static final FaceBakery FACE_BAKERY = new FaceBakery();
    private static final float[] WHOLE = {0.0F, 0.0F, 16.0F, 16.0F};

    private final TextureAtlasSprite side;
    private final TextureAtlasSprite end;
    private final TextureAtlasSprite node;
    private final TextureAtlasSprite particle;
    private final Map<BundleState.ModelKey, List<BakedQuad>> cache = new ConcurrentHashMap<>();

    public BundleBakedModel(TextureAtlasSprite side, TextureAtlasSprite end, TextureAtlasSprite node, TextureAtlasSprite particle) {
        this.side = side;
        this.end = end;
        this.node = node;
        this.particle = particle;
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
     * Builds the quads from the bundle's layout: pipe cores and arms in their pipe's color, and the
     * junction box, plates and plugs in the shared connector texture.
     */
    private List<BakedQuad> build(BundleState bundle) {
        List<BakedQuad> quads = new ArrayList<>();
        for (ConduitLayout.Piece piece : ConduitLayout.of(bundle).pieces()) {
            switch (piece.kind()) {
                case CORE, ARM -> addPipeBox(quads, piece.box(), axis(piece.axis()), piece.type());
                case JUNCTION, PLATE, PLUG -> addConnectorBox(quads, piece.box());
            }
        }
        return List.copyOf(quads);
    }

    /** The game's axis for a layout axis (0 x, 1 y, 2 z), or {@code null} for none. */
    @Nullable
    private static Direction.Axis axis(int axis) {
        return switch (axis) {
            case 0 -> Direction.Axis.X;
            case 1 -> Direction.Axis.Y;
            case 2 -> Direction.Axis.Z;
            default -> null;
        };
    }

    /**
     * Adds a piece of pipe running along {@code axis}: its sides show the edge lines along the
     * pipe and its two ends the framed end. A core with no single direction (a corner, or a pipe
     * with no connections) is framed on every face.
     */
    private void addPipeBox(List<BakedQuad> quads, Box box, @Nullable Direction.Axis axis, PipeType type) {
        if (!BundleShapes.hasVolume(box)) {
            return;
        }
        IQuadTransformer tint = QuadTransformers.applyingColor(0xFF000000 | type.color());
        for (Direction direction : Direction.values()) {
            if (axis == null || direction.getAxis() == axis) {
                quads.add(tint.process(bake(box, direction, end, 0)));
            } else {
                // The side texture's lines run down it, so a face whose rows already follow the pipe
                // takes it as it is and the others take it turned a quarter.
                boolean rowsFollowPipe = axis == (direction.getAxis() != Direction.Axis.Y ? Direction.Axis.Y : Direction.Axis.Z);
                quads.add(tint.process(bake(box, direction, side, rowsFollowPipe ? 0 : 90)));
            }
        }
    }

    /** Adds the six faces of a junction box, plate or plug, untinted. */
    private void addConnectorBox(List<BakedQuad> quads, Box box) {
        if (!BundleShapes.hasVolume(box)) {
            return;
        }
        for (Direction direction : Direction.values()) {
            quads.add(bake(box, direction, node, 0));
        }
    }

    private static BakedQuad bake(Box box, Direction direction, TextureAtlasSprite sprite, int rotation) {
        Vector3f from = new Vector3f((float) box.minX(), (float) box.minY(), (float) box.minZ());
        Vector3f to = new Vector3f((float) box.maxX(), (float) box.maxY(), (float) box.maxZ());
        BlockElementFace face = new BlockElementFace(null, -1, "#pipe", new BlockFaceUV(WHOLE.clone(), rotation));
        return FACE_BAKERY.bakeQuad(from, to, face, sprite, direction, BlockModelRotation.X0_Y0, null, true);
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

    /** NeoForge asks for the particle sprite with model data; this model has just the one. */
    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        return particle;
    }

    // NeoForge deprecated the plain getter in favor of the one above but it is still abstract.
    @Override
    @SuppressWarnings("deprecation")
    public TextureAtlasSprite getParticleIcon() {
        return particle;
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}
