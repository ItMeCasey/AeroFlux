package com.itmecasey.aeroflux.compat;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.compat.top.AeroFluxProbeProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;

/**
 * Hooks AeroFlux into optional mods that want to be told about it. Every hook checks that its mod
 * is installed first, so nothing of theirs is loaded when they are absent. (Jade needs nothing
 * here: it finds {@code AeroFluxJadePlugin} by its annotation.)
 */
@EventBusSubscriber(modid = AeroFlux.MODID)
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
