package com.itmecasey.aeroflux.client.model;

import com.itmecasey.aeroflux.core.conduit.Box;
import com.itmecasey.aeroflux.core.conduit.BundleState;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.mojang.blaze3d.platform.Transparency;
import com.mojang.math.Quadrant;
import java.util.EnumMap;
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

    private final Map<PipeType, BakedQuad.MaterialInfo> pipeMaterials = new EnumMap<>(PipeType.class);
    private final BakedQuad.MaterialInfo nodeMaterial;
    private final Material.Baked particle;
    @BakedQuad.MaterialFlags
    private final int materialFlags;
    private final Map<BundleState.ModelKey, BlockStateModelPart> cache = new ConcurrentHashMap<>();

    /** What identifies the geometry of one bundle to the chunk renderer. */
    private record GeometryKey(BundleState.ModelKey bundle, BundleBakedModel model) {
    }

    public BundleBakedModel(Map<PipeType, Material.Baked> pipes, Material.Baked node, Material.Baked particle) {
        int flags = 0;
        for (Map.Entry<PipeType, Material.Baked> entry : pipes.entrySet()) {
            BakedQuad.MaterialInfo info = materialFor(entry.getValue());
            pipeMaterials.put(entry.getKey(), info);
            flags |= info.flags();
        }
        this.nodeMaterial = materialFor(node);
        this.particle = particle;
        this.materialFlags = flags | nodeMaterial.flags();
    }

    /** How a quad using this texture is drawn: opaque or cutout is decided by the texture's pixels. */
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

    private BlockStateModelPart build(BundleState bundle) {
        QuadCollection.Builder quads = new QuadCollection.Builder();
        for (PipeType type : bundle.set().types()) {
            BakedQuad.MaterialInfo material = pipeMaterials.get(type);
            for (Box box : bundle.boxesOf(type)) {
                addBox(quads, box, material);
            }
        }
        for (Box box : bundle.connectorBoxes()) {
            addBox(quads, box, nodeMaterial);
        }
        return new SimpleModelWrapper(quads.build(), true, particle);
    }

    /**
     * Adds the six faces of a box. The pipes do not fill their block's faces, so every quad is
     * unculled. Faces shared with another box are left in; they are hidden.
     */
    private static void addBox(QuadCollection.Builder quads, Box box, BakedQuad.MaterialInfo material) {
        Vector3f from = new Vector3f((float) box.minX(), (float) box.minY(), (float) box.minZ());
        Vector3f to = new Vector3f((float) box.maxX(), (float) box.maxY(), (float) box.maxZ());
        for (Direction direction : Direction.values()) {
            CuboidFace.UVs uvs = FaceBakery.defaultFaceUV(from, to, direction);
            quads.addUnculledFace(FaceBakery.bakeQuad(NO_INTERNING, from, to, uvs, Quadrant.R0, material,
                    direction, BlockModelRotation.IDENTITY, null, ExtraFaceData.DEFAULT));
        }
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
