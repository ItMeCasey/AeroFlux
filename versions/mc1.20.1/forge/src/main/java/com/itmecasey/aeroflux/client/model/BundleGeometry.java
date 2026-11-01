package com.itmecasey.aeroflux.client.model;

import com.itmecasey.aeroflux.core.conduit.PipeType;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;

/**
 * The unbaked form of the conduit bundle model. Baking looks up one texture per pipe type (named
 * after the type in the model file), plus {@code node} for connectors and {@code particle}.
 */
public class BundleGeometry implements IUnbakedGeometry<BundleGeometry> {
    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                           Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState,
                           ItemOverrides overrides, ResourceLocation modelLocation) {
        Map<PipeType, TextureAtlasSprite> pipeSprites = new EnumMap<>(PipeType.class);
        for (PipeType type : PipeType.values()) {
            pipeSprites.put(type, spriteGetter.apply(context.getMaterial(type.id())));
        }
        TextureAtlasSprite node = spriteGetter.apply(context.getMaterial("node"));
        TextureAtlasSprite particle = spriteGetter.apply(context.getMaterial("particle"));
        return new BundleBakedModel(pipeSprites, node, particle, modelLocation);
    }
}
