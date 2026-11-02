package com.itmecasey.aeroflux.client.model;

import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.PipeTextures;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.resources.model.geometry.BakedQuad;

/**
 * The baked look of the textures a bundle draws with: the connector, and every pipe texture by
 * name. Minecraft 26.1 bakes a material into each quad rather than handing out sprites, so the
 * model gets these by baking one swatch quad per texture (see {@link BundleGeometry}) and keeping
 * the material that came out.
 *
 * <p>The pipe textures are already colored, so none of these materials carries a tint.
 */
final class BundleMaterials {
    /** The pipe textures by name, such as {@code pipe_fluid_arm_v_gold}. */
    private final Map<String, BakedQuad.MaterialInfo> pipes = new HashMap<>();
    private final BakedQuad.MaterialInfo node;

    /**
     * Reads the swatches back in the order {@link BundleGeometry} baked them.
     *
     * @param swatches the node, then one per name in {@link PipeTextures#allNames()}
     */
    BundleMaterials(List<BakedQuad> swatches) {
        List<String> names = PipeTextures.allNames();
        if (swatches.size() != names.size() + 1) {
            throw new IllegalArgumentException("Expected " + (names.size() + 1) + " swatches but found " + swatches.size());
        }
        node = swatches.get(0).materialInfo();
        for (int i = 0; i < names.size(); i++) {
            pipes.put(names.get(i), swatches.get(i + 1).materialInfo());
        }
    }

    /** The texture of one part of a pipe, such as {@code arm_v} or {@code plug}. */
    BakedQuad.MaterialInfo pipe(PipeType type, Tier tier, String part) {
        return pipes.get(PipeTextures.name(type, tier, part));
    }

    BakedQuad.MaterialInfo node() {
        return node;
    }
}
