package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.AeroFlux;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Item data AeroFlux keeps on its own items. */
public final class ModDataComponents {
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, AeroFlux.MODID);

    /** The flux inside a Flux Tank that was broken, put back when the tank is placed again. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SimpleFluidContent>> TANK_FLUID =
            COMPONENTS.registerComponentType("tank_fluid", builder -> builder
                    .persistent(SimpleFluidContent.CODEC)
                    .networkSynchronized(SimpleFluidContent.STREAM_CODEC));

    private ModDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
