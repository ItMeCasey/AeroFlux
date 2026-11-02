package com.itmecasey.aeroflux.client.model;

import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.core.conduit.PipeTextures;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

/**
 * The unbaked form of the conduit bundle model. Baking looks up {@code node}, for the junction
 * boxes and plates, and {@code particle} from the model file. The pipes' own textures, one set per
 * pipe type and tier (see {@link PipeTextures}), are found by name in the block atlas.
 */
public class BundleGeometry implements IUnbakedGeometry<BundleGeometry> {
    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                           Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState,
                           ItemOverrides overrides) {
        Map<String, TextureAtlasSprite> pipes = new HashMap<>();
        for (String name : PipeTextures.allNames()) {
            pipes.put(name, spriteGetter.apply(new Material(InventoryMenu.BLOCK_ATLAS, Ids.mod("block/" + name))));
        }
        return new BundleBakedModel(pipes,
                spriteGetter.apply(context.getMaterial("node")),
                spriteGetter.apply(context.getMaterial("particle")));
    }
}
