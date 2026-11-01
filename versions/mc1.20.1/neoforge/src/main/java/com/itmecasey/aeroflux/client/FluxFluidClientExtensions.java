package com.itmecasey.aeroflux.client;

import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.core.FluxType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;

/**
 * How a flux fluid looks in the world. Both fluids share the same soft, animated textures and
 * are tinted with their flux color, so a texture pack only has to replace one pair.
 *
 * @param flux which flux this is; decides the tint
 */
public record FluxFluidClientExtensions(FluxType flux) implements IClientFluidTypeExtensions {
    private static final ResourceLocation STILL = Ids.mod("block/flux_still");
    private static final ResourceLocation FLOWING = Ids.mod("block/flux_flow");

    @Override
    public ResourceLocation getStillTexture() {
        return STILL;
    }

    @Override
    public ResourceLocation getFlowingTexture() {
        return FLOWING;
    }

    @Override
    public int getTintColor() {
        // Fully opaque tint; the textures carry their own transparency.
        return 0xFF000000 | flux.tint();
    }
}
