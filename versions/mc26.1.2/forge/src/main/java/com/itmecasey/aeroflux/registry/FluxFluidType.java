package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.client.FluxFluidClientExtensions;
import com.itmecasey.aeroflux.core.FluxType;
import java.util.function.Consumer;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidType;

/**
 * The fluid type for the three fluxes. A fluid type hands its client-side look to the
 * game through {@link #initializeClient}, so each type remembers which flux it is.
 */
public class FluxFluidType extends FluidType {
    private final FluxType flux;

    public FluxFluidType(FluxType flux, Properties properties) {
        super(properties);
        this.flux = flux;
    }

    /** Which flux this is. */
    public FluxType flux() {
        return flux;
    }

    /**
     * Forge calls this from the {@link FluidType} constructor, before {@link #flux} is set, so the
     * client side gets this type itself and asks it for the flux only when it draws.
     */
    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new FluxFluidClientExtensions(this));
    }
}
