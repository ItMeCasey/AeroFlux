package com.itmecasey.aeroflux.conduit.transfer;

import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.DistributionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * One place where a lane's network touches a machine: a bundle face that ends at a non-pipe block.
 *
 * @param bundle       position of the bundle that owns this face
 * @param face         the face of that bundle
 * @param target       position of the machine block the face points at
 * @param mode         what the pipe does at this face; never OFF or BLOCKED for a collected endpoint
 * @param distribution the distribution mode of the pipe that owns the face
 */
public record NetworkEndpoint(BlockPos bundle, Face face, BlockPos target, ConnectionMode mode,
                              DistributionMode distribution) {

    /** The direction from the bundle toward the machine. */
    public Direction facing() {
        return Direction.from3DDataValue(face.index());
    }

    /** The side of the machine that touches the pipe; this is the side used to ask for capabilities. */
    public Direction machineSide() {
        return facing().getOpposite();
    }

    /** Whether the network takes from this machine. */
    public boolean pulls() {
        return mode.pullsFromNeighbor();
    }

    /** Whether the network gives to this machine. */
    public boolean pushes() {
        return mode.pushesToNeighbor();
    }
}
