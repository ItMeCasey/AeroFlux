package com.itmecasey.aeroflux.conduit.transfer;

import com.itmecasey.aeroflux.compat.mekanism.MekanismCompat;
import net.minecraft.server.level.ServerLevel;

/**
 * The gas network's mover. Gases and the other Mekanism chemicals only exist with Mekanism, so
 * without it a gas network finds no endpoints and this does nothing; with it the work goes to
 * {@link ChemicalMover}, which is only loaded then.
 */
final class GasMover implements ResourceMover {
    @Override
    public void run(ServerLevel level, LaneNetwork network, LaneState state) {
        if (MekanismCompat.loaded()) {
            ChemicalMover.run(level, network, state);
        }
    }
}
