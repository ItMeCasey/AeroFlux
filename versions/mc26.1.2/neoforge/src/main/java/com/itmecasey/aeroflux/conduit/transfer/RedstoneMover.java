package com.itmecasey.aeroflux.conduit.transfer;

import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import net.minecraft.server.level.ServerLevel;

/**
 * Carries a redstone signal (channel 0 for now). The network's signal is the strongest one any
 * importing endpoint sees from its machine, and every exporting endpoint outputs that level. The
 * outputs are stored in the bundles, which expose them to the world through the block's signal
 * methods; a bundle only tells its neighbors when its output actually changes.
 */
final class RedstoneMover implements ResourceMover {
    /**
     * True while the mover is reading the world. Bundles report no output during that time, so a
     * pipe can never read back its own signal through a block it is powering (a lamp or a solid
     * block passes strong power on to its neighbors). Only the server thread touches this.
     */
    private static boolean sampling;

    /** Whether the engine is currently sampling machines, in which case bundles must output nothing. */
    static boolean isSampling() {
        return sampling;
    }

    @Override
    public void run(ServerLevel level, LaneNetwork network, LaneState state) {
        int signal = 0;
        sampling = true;
        try {
            for (NetworkEndpoint endpoint : network.endpoints()) {
                if (endpoint.pulls() && level.isLoaded(endpoint.target())) {
                    // The direction points from the pipe toward the machine, as getSignal expects.
                    signal = Math.max(signal, level.getSignal(endpoint.target(), endpoint.facing()));
                }
            }
        } finally {
            sampling = false;
        }

        for (NetworkEndpoint endpoint : network.endpoints()) {
            if (level.isLoaded(endpoint.bundle())
                    && level.getBlockEntity(endpoint.bundle()) instanceof ConduitBundleBlockEntity bundle) {
                bundle.setRedstoneOutput(endpoint.face(), endpoint.pushes() ? signal : 0);
            }
        }
    }
}
