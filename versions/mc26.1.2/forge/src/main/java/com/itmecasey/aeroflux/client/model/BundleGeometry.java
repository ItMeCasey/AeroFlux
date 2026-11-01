package com.itmecasey.aeroflux.client.model;

import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.mojang.math.Quadrant;
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
 * The unbaked form of the conduit bundle model. The model file names one texture per pipe type
 * (after the type's id), plus {@code node} for connectors and {@code particle}.
 *
 * <p>A baked geometry in 26.1 is a fixed list of quads, which cannot depend on the block
 * entity. So this bakes only a swatch per texture: tiny stand-in quads that carry the baked
 * material. {@link BundleBlockStateModel#from} later reads the materials back out and puts
 * the real, data-driven model in place of the one built from these.
 */
public class BundleGeometry implements UnbakedGeometry {
    /** The face every swatch is baked on. Which one does not matter. */
    private static final Direction SWATCH_FACE = Direction.SOUTH;

    @Override
    public QuadCollection bake(TextureSlots textures, ModelBaker baker, ModelState state, ModelDebugName name) {
        QuadCollection.Builder swatches = new QuadCollection.Builder();
        for (PipeType type : PipeType.values()) {
            swatches.addUnculledFace(swatch(textures, baker, state, name, type.id()));
        }
        swatches.addUnculledFace(swatch(textures, baker, state, name, "node"));
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
