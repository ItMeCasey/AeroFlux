package com.itmecasey.aeroflux.client.model;

import com.itmecasey.aeroflux.conduit.BundleShapes;
import com.itmecasey.aeroflux.core.conduit.Box;
import com.itmecasey.aeroflux.core.conduit.BundleState;
import com.itmecasey.aeroflux.core.conduit.ConduitLayout;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.mojang.math.Quadrant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.cuboid.CuboidFace;
import net.minecraft.client.resources.model.cuboid.FaceBakery;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3fc;

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
public final class BundleBlockStateModel implements BlockStateModel {
    /** Nothing here needs sharing between quads, so the quads can keep their own vectors. */
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

    private final BundleMaterials materials;
    private final Material.Baked particle;
    private final int materialFlags;
    private final Map<BundleState.ModelKey, BlockStateModelPart> cache = new ConcurrentHashMap<>();

    BundleBlockStateModel(BundleMaterials materials, Material.Baked particle, int materialFlags) {
        this.materials = materials;
        this.particle = particle;
        this.materialFlags = materialFlags;
    }

    /**
     * Builds the dynamic model from the model that baking produced for the bundle's block state.
     * That model only holds the material swatches made by {@link BundleGeometry}.
     *
     * @param baked the model the game baked from {@code models/block/conduit_bundle.json}
     * @return the dynamic model, or {@code null} if the baked model is not what {@link BundleGeometry} makes
     */
    @Nullable
    public static BundleBlockStateModel from(BlockStateModel baked) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        baked.collectParts(RandomSource.create(), parts, ModelData.EMPTY);
        if (parts.size() != 1) {
            return null;
        }
        BlockStateModelPart swatches = parts.getFirst();
        try {
            return new BundleBlockStateModel(new BundleMaterials(swatches.getQuads(null)),
                    swatches.particleMaterial(), swatches.materialFlags());
        } catch (IllegalArgumentException wrongModel) {
            return null;
        }
    }

    // Forge deprecates the plain overloads in favor of the model data ones, but they stay abstract.
    @SuppressWarnings("deprecation")
    @Override
    public void collectParts(RandomSource random, List<BlockStateModelPart> output) {
        // Without model data there is no way to know what is inside, so nothing is drawn.
    }

    @Override
    public void collectParts(RandomSource random, List<BlockStateModelPart> output, ModelData data) {
        BundleState bundle = data.get(BundleModelData.BUNDLE);
        if (bundle != null && !bundle.set().isEmpty()) {
            output.add(cache.computeIfAbsent(bundle.modelKey(), key -> build(bundle)));
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public Material.Baked particleMaterial() {
        return particle;
    }

    @Override
    public int materialFlags() {
        return materialFlags;
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
        // The pipes do not fill their block's faces, so nothing is ever culled against a neighbor.
        return new SimpleModelWrapper(quads.build(), true, particle);
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
    private void addPipeBox(QuadCollection.Builder quads, Box box, @Nullable Direction.Axis axis, PipeType type) {
        if (!BundleShapes.hasVolume(box)) {
            return;
        }
        Vector3f from = new Vector3f((float) box.minX(), (float) box.minY(), (float) box.minZ());
        Vector3f to = new Vector3f((float) box.maxX(), (float) box.maxY(), (float) box.maxZ());
        for (Direction direction : Direction.values()) {
            if (axis == null || direction.getAxis() == axis) {
                addFace(quads, from, to, WHOLE, direction, Quadrant.R0, materials.end(type));
            } else {
                // The side texture's lines run down it, so a face whose rows already follow the pipe
                // takes it as it is and the others take it turned a quarter.
                boolean rowsFollowPipe = axis == (direction.getAxis() != Direction.Axis.Y ? Direction.Axis.Y : Direction.Axis.Z);
                addFace(quads, from, to, WHOLE, direction, rowsFollowPipe ? Quadrant.R0 : Quadrant.R90, materials.side(type));
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
            addFace(quads, from, to, uvFor(direction, box), direction, Quadrant.R0, materials.node());
        }
    }

    private static void addFace(QuadCollection.Builder quads, Vector3f from, Vector3f to, CuboidFace.UVs uvs,
                                Direction direction, Quadrant rotation, BakedQuad.MaterialInfo material) {
        quads.addUnculledFace(FaceBakery.bakeQuad(NO_INTERNING, from, to, uvs, rotation, material,
                direction, BlockModelRotation.IDENTITY, null));
    }

    /** The default texture region for a face, the same mapping vanilla uses for unspecified UVs. */
    private static CuboidFace.UVs uvFor(Direction direction, Box box) {
        float x1 = (float) box.minX();
        float y1 = (float) box.minY();
        float z1 = (float) box.minZ();
        float x2 = (float) box.maxX();
        float y2 = (float) box.maxY();
        float z2 = (float) box.maxZ();
        return switch (direction) {
            case DOWN -> new CuboidFace.UVs(x1, 16.0F - z2, x2, 16.0F - z1);
            case UP -> new CuboidFace.UVs(x1, z1, x2, z2);
            case NORTH -> new CuboidFace.UVs(16.0F - x2, 16.0F - y2, 16.0F - x1, 16.0F - y1);
            case SOUTH -> new CuboidFace.UVs(x1, 16.0F - y2, x2, 16.0F - y1);
            case WEST -> new CuboidFace.UVs(z1, 16.0F - y2, z2, 16.0F - y1);
            case EAST -> new CuboidFace.UVs(16.0F - z2, 16.0F - y2, 16.0F - z1, 16.0F - y1);
        };
    }
}
