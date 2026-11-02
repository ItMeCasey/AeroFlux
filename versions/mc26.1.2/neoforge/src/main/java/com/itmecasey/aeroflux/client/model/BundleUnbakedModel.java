package com.itmecasey.aeroflux.client.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;

/**
 * The conduit bundle model as written in the block state file: a {@code type} of
 * {@code aeroflux:conduit_bundle} and a {@code textures} map. Baking looks up {@code side} and
 * {@code end}, the two white pipe textures every pipe shares in its own tint, plus {@code node}
 * for junctions and machine plates and {@code particle} for break particles. The quads themselves
 * are built later, per bundle.
 *
 * @param textures texture key to texture, from the block state file
 */
public record BundleUnbakedModel(Map<String, Material> textures) implements CustomUnbakedBlockStateModel {
    public static final MapCodec<BundleUnbakedModel> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Material.CODEC).fieldOf("textures").forGetter(BundleUnbakedModel::textures)
    ).apply(instance, BundleUnbakedModel::new));

    private static final ModelDebugName DEBUG_NAME = () -> "aeroflux:conduit_bundle";

    @Override
    public BundleBakedModel bake(ModelBaker baker) {
        MaterialBaker materials = baker.materials();
        return new BundleBakedModel(resolve(materials, "side"), resolve(materials, "end"),
                resolve(materials, "node"), resolve(materials, "particle"));
    }

    private Material.Baked resolve(MaterialBaker materials, String key) {
        Material material = textures.get(key);
        return material != null
                ? materials.get(material, DEBUG_NAME)
                : materials.reportMissingReference(key, DEBUG_NAME);
    }

    /** Nothing to load first: the model uses sprites from the block atlas and no other model files. */
    @Override
    public void resolveDependencies(Resolver resolver) {
    }

    @Override
    public MapCodec<BundleUnbakedModel> codec() {
        return CODEC;
    }
}
