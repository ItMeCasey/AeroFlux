package com.itmecasey.aeroflux.core.conduit;

/** What a pipe does at one face of its bundle. */
public enum ConnectionMode {
    /** Connected and moves its contents both ways. */
    OPEN,
    /** Connected and only pulls from the neighbor into the network. */
    IMPORT,
    /** Connected and only pushes from the network into the neighbor. */
    EXPORT,
    /** Connected, so the stub shows, but passive. */
    OFF,
    /** Not connected at all; the pipe visually detaches and nothing can attach. */
    BLOCKED;

    /** Whether a pipe in this mode pulls from the block next to it into the network. */
    public boolean pullsFromNeighbor() {
        return this == OPEN || this == IMPORT;
    }

    /** Whether a pipe in this mode pushes from the network into the block next to it. */
    public boolean pushesToNeighbor() {
        return this == OPEN || this == EXPORT;
    }

    /** The next mode in the order the wrench cycles through. */
    public ConnectionMode next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
