package com.itmecasey.aeroflux.client.model;

import java.util.function.Function;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;

/**
 * The unbaked form of the conduit bundle model. Baking looks up the pipe's {@code side} and
 * {@code end} textures, which every pipe shares and tints with its own color, plus {@code node}
 * for the junction boxes and plates and {@code particle}.
 */
public class BundleGeometry implements IUnbakedGeometry<BundleGeometry> {
    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                           Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState,
                           ItemOverrides overrides) {
        return new BundleBakedModel(
                spriteGetter.apply(context.getMaterial("side")),
                spriteGetter.apply(context.getMaterial("end")),
                spriteGetter.apply(context.getMaterial("node")),
                spriteGetter.apply(context.getMaterial("particle")));
    }
}
