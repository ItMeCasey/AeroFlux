package com.itmecasey.aeroflux.compat;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.compat.top.AeroFluxProbeProvider;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;

/**
 * Hooks AeroFlux into optional mods that want to be told about it. Every hook checks that its mod
 * is installed first, so nothing of theirs is loaded when they are absent. (Jade needs nothing
 * here: it finds {@code AeroFluxJadePlugin} by its annotation.)
 */
@Mod.EventBusSubscriber(modid = AeroFlux.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CompatSetup {
    private static final String THE_ONE_PROBE = "theoneprobe";

    private CompatSetup() {
    }

    /** Hands The One Probe our provider, the way its API asks for it. */
    @SubscribeEvent
    public static void enqueue(InterModEnqueueEvent event) {
        if (ModList.get().isLoaded(THE_ONE_PROBE)) {
            InterModComms.sendTo(THE_ONE_PROBE, "getTheOneProbe", () -> new AeroFluxProbeProvider());
        }
    }
}
