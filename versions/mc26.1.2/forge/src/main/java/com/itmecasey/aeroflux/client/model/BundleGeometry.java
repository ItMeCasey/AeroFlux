package com.itmecasey.aeroflux.client.model;

import com.mojang.math.Quadrant;
import java.util.List;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.cuboid.CuboidFace;
import net.minecraft.client.resources.model.cuboid.FaceBakery;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.geometry.UnbakedGeometry;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.core.Direction;
import org.joml.Vector3f;

/**
 * The unbaked form of the conduit bundle model. The model file names the white pipe textures
 * {@code side} and {@code end}, which every pipe shares and tints with its color, plus
 * {@code node} for connectors and {@code particle}.
 *
 * <p>A baked geometry in 26.1 is a fixed list of quads, which cannot depend on the block
 * entity. So this bakes only a swatch per texture: tiny stand-in quads that carry the baked
 * material. {@link BundleBlockStateModel#from} later reads the materials back out and puts
 * the real, data-driven model in place of the one built from these.
 */
public class BundleGeometry implements UnbakedGeometry {
    /** The face every swatch is baked on. Which one does not matter. */
    private static final Direction SWATCH_FACE = Direction.SOUTH;
    /** The texture slots, in the order {@link BundleMaterials} reads them back. */
    private static final List<String> SLOTS = List.of("side", "end", "node");

    @Override
    public QuadCollection bake(TextureSlots textures, ModelBaker baker, ModelState state, ModelDebugName name) {
        QuadCollection.Builder swatches = new QuadCollection.Builder();
        for (String slot : SLOTS) {
            swatches.addUnculledFace(swatch(textures, baker, state, name, slot));
        }
        return swatches.build();
    }

    private static BakedQuad swatch(TextureSlots textures, ModelBaker baker, ModelState state,
                                    ModelDebugName name, String slot) {
        Material.Baked material = baker.materials().resolveSlot(textures, slot, name);
        // The whole texture is covered, so the layer chosen from its transparency fits every face drawn with it.
        CuboidFace face = new CuboidFace(null, CuboidFace.NO_TINT, slot,
                new CuboidFace.UVs(0.0F, 0.0F, 16.0F, 16.0F), Quadrant.R0);
        return FaceBakery.bakeQuad(baker, new Vector3f(6.0F, 6.0F, 6.0F), new Vector3f(10.0F, 10.0F, 10.0F),
                face, material, SWATCH_FACE, state, null, true, 0);
    }
}
