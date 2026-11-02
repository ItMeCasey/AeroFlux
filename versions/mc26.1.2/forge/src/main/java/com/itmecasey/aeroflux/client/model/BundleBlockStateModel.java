package com.itmecasey.aeroflux.client.model;

import com.itmecasey.aeroflux.conduit.BundleShapes;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.Box;
import com.itmecasey.aeroflux.core.conduit.BundleState;
import com.itmecasey.aeroflux.core.conduit.ConduitLayout;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeTextures;
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
 * <p>Every pipe type and tier has its own textures, already colored and sized to the part they
 * go on, so nothing is tinted, turned or stretched: {@link PipeTextures} says which file a face
 * takes, and an arm shows only as much of its texture as it is long. The junction box and the
 * machine plates share the dark connector texture.
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
     * Builds the quads from the bundle's layout: pipe cores, arms and plugs in their own pipe's
     * textures, and the junction box and plates in the shared connector texture.
     */
    private BlockStateModelPart build(BundleState bundle) {
        QuadCollection.Builder quads = new QuadCollection.Builder();
        for (ConduitLayout.Piece piece : ConduitLayout.of(bundle).pieces()) {
            switch (piece.kind()) {
                case CORE -> addCore(quads, piece, bundle.tier(piece.type().lane()));
                case ARM -> addArm(quads, piece, bundle.tier(piece.type().lane()));
                case PLUG -> addPlug(quads, piece, bundle.tier(piece.type().lane()));
                case JUNCTION, PLATE -> addConnectorBox(quads, piece.box());
            }
        }
        // The pipes do not fill their block's faces, so nothing is ever culled against a neighbor.
        return new SimpleModelWrapper(quads.build(), true, particle);
    }

    /**
     * Adds a pipe's core. A core on a straight run has sides and ends like an arm; a core with no
     * single direction (a corner, a branch, or a pipe with no connections) shows its end texture
     * on every face.
     */
    private void addCore(QuadCollection.Builder quads, ConduitLayout.Piece piece, Tier tier) {
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
            addFace(quads, box, WHOLE, direction, materials.pipe(type, tier, part));
        }
    }

    /** Adds an arm: its sides show only as much of the arm texture as the arm is long. */
    private void addArm(QuadCollection.Builder quads, ConduitLayout.Piece piece, Tier tier) {
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
                addFace(quads, box, WHOLE, direction, materials.pipe(type, tier, "arm_end"));
            } else {
                boolean vertical = PipeTextures.vertical(axis, face);
                float[] uv = PipeTextures.armUv(vertical, length);
                addFace(quads, box, new CuboidFace.UVs(uv[0], uv[1], uv[2], uv[3]), direction,
                        materials.pipe(type, tier, vertical ? "arm_v" : "arm_h"));
            }
        }
    }

    /**
     * Adds a pipe's plug on a machine plate. The end that faces into the bundle shows the glyph of
     * what the pipe carries; the end against the plate is never seen and takes the connector
     * texture.
     */
    private void addPlug(QuadCollection.Builder quads, ConduitLayout.Piece piece, Tier tier) {
        Box box = piece.box();
        if (!BundleShapes.hasVolume(box)) {
            return;
        }
        PipeType type = piece.type();
        for (Direction direction : Direction.values()) {
            Face face = Face.byIndex(direction.get3DDataValue());
            BakedQuad.MaterialInfo material;
            if (face == piece.face()) {
                material = materials.node();
            } else if (face == piece.face().opposite()) {
                material = materials.pipe(type, tier, PipeTextures.PLUG_FACE);
            } else {
                material = materials.pipe(type, tier, PipeTextures.vertical(piece.axis(), face) ? "plug_v" : "plug_h");
            }
            addFace(quads, box, WHOLE, direction, material);
        }
    }

    /** Adds the six faces of a junction box or plate. */
    private void addConnectorBox(QuadCollection.Builder quads, Box box) {
        if (!BundleShapes.hasVolume(box)) {
            return;
        }
        for (Direction direction : Direction.values()) {
            addFace(quads, box, uvFor(direction, box), direction, materials.node());
        }
    }

    /** Adds one face of a box, as the texture is: no tint and no rotation. */
    private static void addFace(QuadCollection.Builder quads, Box box, CuboidFace.UVs uvs, Direction direction,
                                BakedQuad.MaterialInfo material) {
        Vector3f from = new Vector3f((float) box.minX(), (float) box.minY(), (float) box.minZ());
        Vector3f to = new Vector3f((float) box.maxX(), (float) box.maxY(), (float) box.maxZ());
        quads.addUnculledFace(FaceBakery.bakeQuad(NO_INTERNING, from, to, uvs, Quadrant.R0, material,
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
