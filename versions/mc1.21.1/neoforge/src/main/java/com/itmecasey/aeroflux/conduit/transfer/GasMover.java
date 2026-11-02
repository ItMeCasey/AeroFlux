package com.itmecasey.aeroflux.conduit.transfer;

import net.minecraft.server.level.ServerLevel;

/**
 * The gas network's mover.
 *
 * <p>Vanilla NeoForge has no gas capability, so for now a gas network finds no endpoints and this
 * mover has nothing to do. Gas support arrives with a soft dependency on Mekanism: the endpoint
 * lookup will ask for Mekanism's gas handler capability and this class will plan and move gas the
 * same way {@link FluidMover} moves fluid, using {@link AmountMover}.
 */
final class GasMover implements ResourceMover {
    @Override
    public void run(ServerLevel level, LaneNetwork network, LaneState state) {
        // Intentionally empty until the Mekanism gas capability is available.
    }
}
