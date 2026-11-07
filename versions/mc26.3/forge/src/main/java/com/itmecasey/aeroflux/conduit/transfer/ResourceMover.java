package com.itmecasey.aeroflux.conduit.transfer;

import net.minecraft.server.level.ServerLevel;

/** Does one tick of work for a lane's network. One implementation exists for each kind of resource. */
interface ResourceMover {
    /**
     * Moves whatever this resource moves, once. Called only by the network's leader.
     *
     * @param level   the level the network is in
     * @param network a fresh-enough snapshot of the network
     * @param state   the leader's memory between ticks
     */
    void run(ServerLevel level, LaneNetwork network, LaneState state);
}
