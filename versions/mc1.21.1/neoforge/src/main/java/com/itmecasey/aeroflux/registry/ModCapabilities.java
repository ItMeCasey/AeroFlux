package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.compat.ae2.Ae2Compat;
import com.itmecasey.aeroflux.converter.FluxConverterBlockEntity;
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
                (collector, side) -> collector.outputView());

        // Tanks accept and give fluid on every side.
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.TANK.get(),
                (tank, side) -> tank.tank());

        // Machines show what their face settings allow; a blocked face shows nothing at all.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.MACHINE.get(),
                FluxMachineBlockEntity::itemHandler);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.MACHINE.get(),
                FluxMachineBlockEntity::fluidHandler);

        // Chunk loaders take Flux on every side and never give it back.
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.CHUNK_LOADER.get(),
                (loader, side) -> loader.fluidHandler());

        // Converters take Flux on every side; the FE converter also lets anything draw its energy.
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.CONVERTER.get(),
                (converter, side) -> converter.fluidHandler());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.CONVERTER.get(),
                FluxConverterBlockEntity::energyHandler);

        // Lamps take their Flux on every side.
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.LAMP.get(),
                (lamp, side) -> lamp.tank());

        // With AE2 installed, a bundle's ME pipe is a grid node AE2 can join.
        Ae2Compat.registerCapabilities(event);
    }
}
