package com.itmecasey.aeroflux.client.model;

import com.itmecasey.aeroflux.conduit.BundleShapes;
import com.itmecasey.aeroflux.core.conduit.Box;
import com.itmecasey.aeroflux.core.conduit.BundleState;
import com.itmecasey.aeroflux.core.conduit.ConduitLayout;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.mojang.blaze3d.platform.Transparency;
import com.mojang.math.Quadrant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.cuboid.CuboidFace;
import net.minecraft.client.resources.model.cuboid.FaceBakery;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.client.model.ExtraFaceData;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * The model of a conduit bundle. It builds its quads on demand from the bundle's contents, which
 * the block entity supplies as model data, and caches them by the bundle's look so that the many
 * bundles sharing the same set and connections cost one build.
 *
 * <p>Pipes are plain: every pipe uses the same two white textures, tinted with its own color. The
 * side texture has a dark line down both long edges and the end texture a dark frame, so the
 * edges stay readable while the color says what the pipe carries. Side faces are turned so the
 * lines always run along the pipe.
 */
public class BundleBakedModel implements DynamicBlockStateModel {
    /**
     * Quads are built long after baking, on the meshing threads, so the baker's own interner
     * (which is not made for that) stays out of it. Sharing identical vertices would save a
     * little memory but is not worth the locking.
     */
    private static final ModelBaker.Interner NO_INTERNING = new ModelBaker.Interner() {
        @Override
        public Vector3fc vector(Vector3fc vector) {
            return vector;
        }

        @Override
        public BakedQuad.MaterialInfo materialInfo(BakedQuad.MaterialInfo material) {
            return material;
        }
    };

    private static final CuboidFace.UVs WHOLE = new CuboidFace.UVs(0, 0, 16, 16);

    private final BakedQuad.MaterialInfo side;
    private final BakedQuad.MaterialInfo end;
    private final BakedQuad.MaterialInfo node;
    private final Material.Baked particle;
    @BakedQuad.MaterialFlags
    private final int materialFlags;
    private final Map<BundleState.ModelKey, BlockStateModelPart> cache = new ConcurrentHashMap<>();

    /** What identifies the geometry of one bundle to the chunk renderer. */
    private record GeometryKey(BundleState.ModelKey bundle, BundleBakedModel model) {
    }

    public BundleBakedModel(Material.Baked side, Material.Baked end, Material.Baked node, Material.Baked particle) {
        this.side = materialFor(side);
        this.end = materialFor(end);
        this.node = materialFor(node);
        this.particle = particle;
        this.materialFlags = this.side.flags() | this.end.flags() | this.node.flags();
    }

    private static BakedQuad.MaterialInfo materialFor(Material.Baked material) {
        Transparency transparency = material.forceTranslucent()
                ? Transparency.TRANSLUCENT
                : material.sprite().contents().computeTransparency(0.0F, 0.0F, 1.0F, 1.0F);
        return BakedQuad.MaterialInfo.of(material, transparency, -1, true, 0, true);
    }

    @Override
    public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state,
                                              RandomSource random) {
        BundleState bundle = level.getModelData(pos).get(BundleModelData.BUNDLE);
        // Without data (the block entity has not arrived yet) nothing is drawn, which is one shared geometry.
        return bundle == null ? this : new GeometryKey(bundle.modelKey(), this);
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
                             List<BlockStateModelPart> parts) {
        BundleState bundle = level.getModelData(pos).get(BundleModelData.BUNDLE);
        if (bundle != null) {
            parts.add(cache.computeIfAbsent(bundle.modelKey(), key -> build(bundle)));
        }
    }

    /**
     * Builds the quads from the bundle's layout: pipe cores and arms in their pipe's color, and the
     * junction box, plates and plugs in the shared connector texture.
     */
    private BlockStateModelPart build(BundleState bundle) {
        QuadCollection.Builder quads = new QuadCollection.Builder();
        for (ConduitLayout.Piece piece : ConduitLayout.of(bundle).pieces()) {
            switch (piece.kind()) {
                case CORE, ARM -> addPipeBox(quads, piece.box(), axis(piece.axis()), piece.type());
                case JUNCTION, PLATE, PLUG -> addConnectorBox(quads, piece.box());
            }
        }
        return new SimpleModelWrapper(quads.build(), true, particle);
    }

    /** The game's axis for a layout axis (0 x, 1 y, 2 z), or {@code null} for none. */
    private static Direction.@Nullable Axis axis(int axis) {
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
     * with no connections) is framed on every face. The pipes do not fill their block's faces, so
     * every quad is unculled.
     */
    private void addPipeBox(QuadCollection.Builder quads, Box box, Direction.@Nullable Axis axis, PipeType type) {
        if (!BundleShapes.hasVolume(box)) {
            return;
        }
        Vector3f from = new Vector3f((float) box.minX(), (float) box.minY(), (float) box.minZ());
        Vector3f to = new Vector3f((float) box.maxX(), (float) box.maxY(), (float) box.maxZ());
        ExtraFaceData color = new ExtraFaceData(0xFF000000 | type.color(), 0, true);
        for (Direction direction : Direction.values()) {
            if (axis == null || direction.getAxis() == axis) {
                addFace(quads, from, to, direction, Quadrant.R0, end, color);
            } else {
                // The side texture's lines run down it, so a face whose rows already follow the pipe
                // takes it as it is and the others take it turned a quarter.
                boolean rowsFollowPipe = axis == (direction.getAxis() != Direction.Axis.Y ? Direction.Axis.Y : Direction.Axis.Z);
                addFace(quads, from, to, direction, rowsFollowPipe ? Quadrant.R0 : Quadrant.R90, side, color);
            }
        }
    }

    /** Adds the six faces of a junction box, plate or plug, untinted. */
    private void addConnectorBox(QuadCollection.Builder quads, Box box) {
        if (!BundleShapes.hasVolume(box)) {
            return;
        }
        Vector3f from = new Vector3f((float) box.minX(), (float) box.minY(), (float) box.minZ());
        Vector3f to = new Vector3f((float) box.maxX(), (float) box.maxY(), (float) box.maxZ());
        for (Direction direction : Direction.values()) {
            quads.addUnculledFace(FaceBakery.bakeQuad(NO_INTERNING, from, to, FaceBakery.defaultFaceUV(from, to, direction),
                    Quadrant.R0, node, direction, BlockModelRotation.IDENTITY, null, ExtraFaceData.DEFAULT));
        }
    }

    private static void addFace(QuadCollection.Builder quads, Vector3f from, Vector3f to, Direction direction,
                                Quadrant rotation, BakedQuad.MaterialInfo material, ExtraFaceData color) {
        quads.addUnculledFace(FaceBakery.bakeQuad(NO_INTERNING, from, to, WHOLE, rotation, material,
                direction, BlockModelRotation.IDENTITY, null, color));
    }

    /** The level-aware variant is inherited and falls back to this one, as the particle never varies. */
    @Override
    @Deprecated
    public Material.Baked particleMaterial() {
        return particle;
    }

    @Override
    @Deprecated
    @BakedQuad.MaterialFlags
    public int materialFlags() {
        return materialFlags;
    }
}
