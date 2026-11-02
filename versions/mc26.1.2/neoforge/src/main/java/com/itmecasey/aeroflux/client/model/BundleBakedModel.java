package com.itmecasey.aeroflux.client.model;

import com.itmecasey.aeroflux.conduit.BundleShapes;
import com.itmecasey.aeroflux.core.conduit.Box;
import com.itmecasey.aeroflux.core.conduit.BundleState;
import com.itmecasey.aeroflux.core.conduit.ConduitLayout;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeTextures;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.mojang.blaze3d.platform.Transparency;
import com.mojang.math.Quadrant;
import java.util.HashMap;
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
 * <p>Every pipe type and tier has its own textures, already colored and sized to the part they
 * go on, so nothing is tinted, turned or stretched: {@link PipeTextures} says which file a face
 * takes, and an arm shows only as much of its texture as it is long. The junction box and the
 * machine plates share the dark connector texture.
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

    /** The pipe textures by name, such as {@code pipe_fluid_arm_v_gold}. */
    private final Map<String, BakedQuad.MaterialInfo> pipes = new HashMap<>();
    private final BakedQuad.MaterialInfo node;
    private final Material.Baked particle;
    @BakedQuad.MaterialFlags
    private final int materialFlags;
    private final Map<BundleState.ModelKey, BlockStateModelPart> cache = new ConcurrentHashMap<>();

    /** What identifies the geometry of one bundle to the chunk renderer. */
    private record GeometryKey(BundleState.ModelKey bundle, BundleBakedModel model) {
    }

    public BundleBakedModel(Map<String, Material.Baked> pipes, Material.Baked node, Material.Baked particle) {
        this.node = materialFor(node);
        this.particle = particle;
        int flags = this.node.flags();
        for (Map.Entry<String, Material.Baked> pipe : pipes.entrySet()) {
            BakedQuad.MaterialInfo material = materialFor(pipe.getValue());
            this.pipes.put(pipe.getKey(), material);
            flags |= material.flags();
        }
        this.materialFlags = flags;
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
        return new SimpleModelWrapper(quads.build(), true, particle);
    }

    /**
     * Adds a pipe's core. A core on a straight run has sides and ends like an arm; a core with no
     * single direction (a corner, a branch, or a pipe with no connections) shows its end texture
     * on every face. The pipes do not fill their block's faces, so every quad is unculled.
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
            addFace(quads, box, direction, WHOLE, pipe(type, tier, part));
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
                addFace(quads, box, direction, WHOLE, pipe(type, tier, "arm_end"));
            } else {
                boolean vertical = PipeTextures.vertical(axis, face);
                float[] uv = PipeTextures.armUv(vertical, length);
                addFace(quads, box, direction, new CuboidFace.UVs(uv[0], uv[1], uv[2], uv[3]),
                        pipe(type, tier, vertical ? "arm_v" : "arm_h"));
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
                material = node;
            } else if (face == piece.face().opposite()) {
                material = pipe(type, tier, PipeTextures.PLUG_FACE);
            } else {
                material = pipe(type, tier, PipeTextures.vertical(piece.axis(), face) ? "plug_v" : "plug_h");
            }
            addFace(quads, box, direction, WHOLE, material);
        }
    }

    private BakedQuad.MaterialInfo pipe(PipeType type, Tier tier, String part) {
        return pipes.get(PipeTextures.name(type, tier, part));
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

    /** Adds one face of a box, as the texture is: no tint and no rotation. */
    private static void addFace(QuadCollection.Builder quads, Box box, Direction direction, CuboidFace.UVs uvs,
                                BakedQuad.MaterialInfo material) {
        Vector3f from = new Vector3f((float) box.minX(), (float) box.minY(), (float) box.minZ());
        Vector3f to = new Vector3f((float) box.maxX(), (float) box.maxY(), (float) box.maxZ());
        quads.addUnculledFace(FaceBakery.bakeQuad(NO_INTERNING, from, to, uvs, Quadrant.R0, material,
                direction, BlockModelRotation.IDENTITY, null, ExtraFaceData.DEFAULT));
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
