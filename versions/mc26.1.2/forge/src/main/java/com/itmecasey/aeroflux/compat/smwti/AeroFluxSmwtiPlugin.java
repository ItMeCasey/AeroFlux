package com.itmecasey.aeroflux.compat.smwti;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.compat.LookInfo;
import com.itmecasey.smwti.api.SmwtiApi;

/**
 * The AeroFlux lines in Show Me What This Is, our own look-at overlay. The server works out the
 * same lines the Jade, The One Probe and WTHIT plugins show (see {@link LookInfo}) and SMWTI sends
 * them to the player looking at the block. The Flux a block holds needs no line of its own: SMWTI
 * lists the tanks of any block entity that exposes the standard fluid capability. This class is
 * only loaded when SMWTI is installed.
 */
public final class AeroFluxSmwtiPlugin {
    private AeroFluxSmwtiPlugin() {
    }

    /** Registers the server provider; called once from the mod constructor. */
    public static void register() {
        SmwtiApi.registerServerBlock((tooltip, level, pos, entity, detailed) ->
                LookInfo.lines(level, entity).forEach(tooltip::add));
        AeroFlux.LOGGER.info("Registered the AeroFlux lines with Show Me What This Is");
    }
}
