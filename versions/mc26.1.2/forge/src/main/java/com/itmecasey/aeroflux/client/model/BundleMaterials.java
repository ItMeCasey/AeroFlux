package com.itmecasey.aeroflux.client.model;

import com.itmecasey.aeroflux.core.conduit.PipeType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.resources.model.geometry.BakedQuad;

/**
 * The baked look of the three textures a bundle draws with: the pipe side, the pipe end and the
 * connector. Minecraft 26.1 bakes a material into each quad rather than handing out sprites, so the
 * model gets these by baking one swatch quad per texture (see {@link BundleGeometry}) and keeping
 * the material that came out.
 *
 * <p>The pipe textures are white and tinted with the pipe's color. Forge has no per-quad color,
 * so each pipe type gets its own tint index (its ordinal), and the bundle block's tint sources
 * (see {@code ClientSetup}) give each index its color.
 */
final class BundleMaterials {
    private final Map<PipeType, BakedQuad.MaterialInfo> sides = new EnumMap<>(PipeType.class);
    private final Map<PipeType, BakedQuad.MaterialInfo> ends = new EnumMap<>(PipeType.class);
    private final BakedQuad.MaterialInfo node;

    /**
     * Reads the swatches back in the order {@link BundleGeometry} baked them.
     *
     * @param swatches the side, the end and the node, in that order
     */
    BundleMaterials(List<BakedQuad> swatches) {
        if (swatches.size() != 3) {
            throw new IllegalArgumentException("Expected 3 swatches but found " + swatches.size());
        }
        BakedQuad.MaterialInfo side = swatches.get(0).materialInfo();
        BakedQuad.MaterialInfo end = swatches.get(1).materialInfo();
        for (PipeType type : PipeType.values()) {
            sides.put(type, tinted(side, type));
            ends.put(type, tinted(end, type));
        }
        node = swatches.get(2).materialInfo();
    }

    private static BakedQuad.MaterialInfo tinted(BakedQuad.MaterialInfo base, PipeType type) {
        return new BakedQuad.MaterialInfo(base.sprite(), base.layer(), base.itemRenderType(), type.ordinal(),
                base.shade(), base.lightEmission());
    }

    BakedQuad.MaterialInfo side(PipeType type) {
        return sides.get(type);
    }

    BakedQuad.MaterialInfo end(PipeType type) {
        return ends.get(type);
    }

    BakedQuad.MaterialInfo node() {
        return node;
    }
}
