package com.itmecasey.aeroflux.devtest;

import com.itmecasey.aeroflux.AeroFlux;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * A block that takes energy, for the self-test only: no AeroFlux block accepts FE, so the energy
 * pipe test needs one. A gold block takes it when {@code AEROFLUX_SELFTEST} is set, and no other
 * game does.
 */
@EventBusSubscriber(modid = AeroFlux.MODID)
final class EnergySink {
    /** What every sink block holds; the test places one. */
    static final SimpleEnergyHandler HANDLER = new SimpleEnergyHandler(1_000_000, 1_000_000, 0);

    private EnergySink() {
    }

    @SubscribeEvent
    static void register(RegisterCapabilitiesEvent event) {
        if (System.getenv("AEROFLUX_SELFTEST") != null) {
            event.registerBlock(Capabilities.Energy.BLOCK, (level, pos, state, entity, side) -> HANDLER, Blocks.GOLD_BLOCK);
        }
    }
}
