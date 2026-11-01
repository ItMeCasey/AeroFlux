package com.itmecasey.aeroflux.client;

import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.core.FluxType;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;

/**
 * How a flux fluid looks in the world. Both fluids share the same soft, animated textures and
 * are tinted with their flux color, so a texture pack only has to replace one pair. The textures
 * are slightly see-through, which the game detects and renders translucent.
 *
 * @param flux which flux this is; decides the tint
 */
public record FluxFluidClientExtensions(FluxType flux) implements IClientFluidTypeExtensions {
    private static final Identifier STILL = Ids.mod("block/flux_still");
    private static final Identifier FLOWING = Ids.mod("block/flux_flow");

    @Override
    public Identifier getStillTexture() {
        return STILL;
    }

    @Override
    public Identifier getFlowingTexture() {
        return FLOWING;
    }

    @Override
    public int getTintColor() {
        // Fully opaque tint; the textures carry their own transparency.
        return 0xFF000000 | flux.tint();
    }
}
