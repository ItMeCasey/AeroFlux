package com.itmecasey.aeroflux.client;

import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.registry.FluxFluidType;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;

/**
 * How a flux fluid looks in the world. All three share the same soft, animated textures and
 * are tinted with their flux color, so a texture pack only has to replace one pair. The textures
 * are slightly see-through, which the game detects and renders translucent.
 *
 * @param type the fluid type; its flux decides the tint
 */
public record FluxFluidClientExtensions(FluxFluidType type) implements IClientFluidTypeExtensions {
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
        return 0xFF000000 | type.flux().tint();
    }
}
