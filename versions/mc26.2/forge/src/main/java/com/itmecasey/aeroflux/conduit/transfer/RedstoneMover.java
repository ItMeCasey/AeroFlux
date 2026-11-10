package com.itmecasey.aeroflux.conduit.transfer;

import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.core.conduit.EndpointSettings;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import net.minecraft.server.level.ServerLevel;

/**
 * Carries redstone signals, one per channel. Every face of a redstone pipe is tuned to a channel
 * (0 until the player picks another). On each channel the network's signal is the strongest one
 * any importing endpoint tuned to it sees from its machine, and every exporting endpoint tuned to
 * it outputs that level, so two lines can run through the same pipes without mixing. The outputs
 * are stored in the bundles, which expose them to the world through the block's signal methods; a
 * bundle only tells its neighbors when its output actually changes.
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
        int[] signal = new int[EndpointSettings.MAX_CHANNELS];
        sampling = true;
        try {
            for (NetworkEndpoint endpoint : network.endpoints()) {
                if (endpoint.pulls() && level.isLoaded(endpoint.target())) {
                    int channel = channelOf(level, endpoint);
                    // The direction points from the pipe toward the machine, as getSignal expects.
                    signal[channel] = Math.max(signal[channel], level.getSignal(endpoint.target(), endpoint.facing()));
                }
            }
        } finally {
            sampling = false;
        }

        for (NetworkEndpoint endpoint : network.endpoints()) {
            if (level.isLoaded(endpoint.bundle())
                    && level.getBlockEntity(endpoint.bundle()) instanceof ConduitBundleBlockEntity bundle) {
                int channel = bundle.endpoints().channel(PipeType.REDSTONE.lane(), endpoint.face());
                bundle.setRedstoneOutput(endpoint.face(), endpoint.pushes() ? signal[channel] : 0);
            }
        }
    }

    /** The channel the endpoint's face is tuned to; 0 when its bundle is not there. */
    private static int channelOf(ServerLevel level, NetworkEndpoint endpoint) {
        if (level.isLoaded(endpoint.bundle()) && level.getBlockEntity(endpoint.bundle()) instanceof ConduitBundleBlockEntity bundle) {
            return bundle.endpoints().channel(PipeType.REDSTONE.lane(), endpoint.face());
        }
        return 0;
    }
}
