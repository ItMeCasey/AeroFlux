package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.fluid.OutputOnlyFluidHandler;
import com.itmecasey.aeroflux.machine.FluxMachineBlockEntity;
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
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.COLLECTOR.get(),
                (collector, side) -> new OutputOnlyFluidHandler(collector.buffer()));

        // Tanks accept and give fluid on every side.
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.TANK.get(),
                (tank, side) -> tank.tank());

        // Machines show what their face settings allow; a blocked face shows nothing at all.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.MACHINE.get(),
                FluxMachineBlockEntity::itemHandler);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.MACHINE.get(),
                FluxMachineBlockEntity::fluidHandler);
    }
}
