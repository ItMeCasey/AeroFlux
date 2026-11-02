package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.fluid.TankContents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Item data AeroFlux keeps on its own items. */
public final class ModDataComponents {
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, AeroFlux.MODID);

    /** The flux inside a Flux Tank that was broken, put back when the tank is placed again. */
    public static final RegistryObject<DataComponentType<TankContents>> TANK_FLUID =
            COMPONENTS.register("tank_fluid", () -> DataComponentType.<TankContents>builder()
                    .persistent(TankContents.CODEC)
                    .networkSynchronized(TankContents.STREAM_CODEC)
                    .build());

    private ModDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
