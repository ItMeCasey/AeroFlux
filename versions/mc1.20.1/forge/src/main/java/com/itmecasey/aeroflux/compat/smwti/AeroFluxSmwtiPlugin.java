package com.itmecasey.aeroflux.compat.smwti;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.compat.LookInfo;
import com.itmecasey.smwti.api.CommonRegistration;
import com.itmecasey.smwti.api.ISmwtiPlugin;
import com.itmecasey.smwti.api.SmwtiPlugin;

/**
 * The AeroFlux lines in Show Me What This Is, our own look-at overlay. The server works out the
 * same lines the Jade, The One Probe and WTHIT plugins show (see {@link LookInfo}) and SMWTI sends
 * them to the player looking at the block. The Flux a block holds needs no line of its own: SMWTI
 * lists the tanks of any block entity that exposes the standard fluid capability.
 *
 * <p>SMWTI finds this class by its annotation, so AeroFlux never calls into it and nothing here
 * is loaded unless SMWTI is installed.
 */
@SmwtiPlugin
public class AeroFluxSmwtiPlugin implements ISmwtiPlugin {
    @Override
    public void register(CommonRegistration registration) {
        registration.serverBlock((tooltip, level, pos, entity, detailed) -> LookInfo.lines(level, entity).forEach(tooltip::add));
        AeroFlux.LOGGER.info("Registered the AeroFlux lines with Show Me What This Is");
    }
}
