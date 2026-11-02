package com.itmecasey.aeroflux.conduit.transfer;

import com.itmecasey.aeroflux.compat.mekanism.MekanismCompat;
import net.minecraft.server.level.ServerLevel;

/**
 * The gas network's mover.
 *
 * <p>Forge has no gas capability of its own; gases come from Mekanism. Without Mekanism a gas
 * network finds no endpoints and there is nothing to do. With it, {@link ChemicalMover} plans and
 * moves gas the way {@link FluidMover} moves fluid. That class names Mekanism classes, so it is only
 * reached once Mekanism is known to be installed.
 */
final class GasMover implements ResourceMover {
    @Override
    public void run(ServerLevel level, LaneNetwork network, LaneState state) {
        if (MekanismCompat.loaded()) {
            ChemicalMover.run(level, network, state);
        }
    }
}
