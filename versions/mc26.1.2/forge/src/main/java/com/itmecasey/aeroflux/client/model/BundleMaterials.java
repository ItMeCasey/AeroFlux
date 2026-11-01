package com.itmecasey.aeroflux.client.model;

import com.itmecasey.aeroflux.core.conduit.PipeType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.resources.model.geometry.BakedQuad;

/**
 * The baked look of every texture a bundle draws with: one per pipe type, plus the connector
 * texture. Minecraft 26.1 bakes a material into each quad rather than handing out sprites, so the
 * model gets these by baking one swatch quad per texture (see {@link BundleGeometry}) and
 * keeping the material that came out.
 */
final class BundleMaterials {
    private final Map<PipeType, BakedQuad.MaterialInfo> pipes = new EnumMap<>(PipeType.class);
    private final BakedQuad.MaterialInfo node;

    /**
     * Reads the swatches back in the order {@link BundleGeometry} baked them.
     *
     * @param swatches one quad per pipe type, then one for the node
     */
    BundleMaterials(List<BakedQuad> swatches) {
        PipeType[] types = PipeType.values();
        if (swatches.size() != types.length + 1) {
            throw new IllegalArgumentException("Expected " + (types.length + 1) + " swatches but found " + swatches.size());
        }
        for (int i = 0; i < types.length; i++) {
            pipes.put(types[i], swatches.get(i).materialInfo());
        }
        node = swatches.get(types.length).materialInfo();
    }

    BakedQuad.MaterialInfo pipe(PipeType type) {
        return pipes.get(type);
    }

    BakedQuad.MaterialInfo node() {
        return node;
    }
}
