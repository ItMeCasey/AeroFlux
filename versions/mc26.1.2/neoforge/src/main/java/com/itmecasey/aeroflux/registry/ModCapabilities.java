package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.AeroFlux;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Exposes AeroFlux blocks to other mods' pipes, tanks and machines through NeoForge capabilities. */
@EventBusSubscriber(modid = AeroFlux.MODID)
public final class ModCapabilities {
    private ModCapabilities() {
    }

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        // Collectors can be drained from any side but never filled.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.COLLECTOR.get(),
                (collector, side) -> collector.outputView());

        // Tanks accept and give fluid on every side.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.TANK.get(),
                (tank, side) -> tank.tank());
    }
}
