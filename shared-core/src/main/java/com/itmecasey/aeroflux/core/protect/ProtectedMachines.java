package com.itmecasey.aeroflux.core.protect;

import com.itmecasey.aeroflux.core.machine.MachineType;

/**
 * Which machines reach into the world enough to be held to the protection check, and how.
 *
 * <p>Most machines only work on what is inside them and need no check. The ones below act on the
 * world around them (the clock and the weather, the animals and dropped items in range) or on
 * something a claim could cover (a farm, a mob farm), so they ask before they act.
 */
public final class ProtectedMachines {
    /** How a machine reaches out. */
    public enum Reach {
        /** Works only inside itself: no check. */
        NONE,
        /** Acts on the world as a whole or on its own spot: one check at the machine's position. */
        OWN_SPOT,
        /** Acts on entities in its range: a check for each place it touches. */
        ENTITIES
    }

    private ProtectedMachines() {
    }

    public static Reach reachOf(MachineType machine) {
        switch (machine) {
            case SKY_CONTROLLER:
            case SPAWNER:
            case PLANT_GROWER:
                return Reach.OWN_SPOT;
            case MAGNET:
            case BREEDER:
                return Reach.ENTITIES;
            default:
                return Reach.NONE;
        }
    }

    /** The action a machine's check is made as. */
    public static ProtectionAction actionOf(Reach reach) {
        return reach == Reach.ENTITIES ? ProtectionAction.USE_ENTITY : ProtectionAction.USE_BLOCK;
    }
}
