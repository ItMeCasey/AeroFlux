package com.itmecasey.aeroflux.core.conduit;

/** What the Conduit Tool does when it is used, in the order the player cycles through them. */
public enum ToolMode {
    /** Cycle the mode of the nearest pipe at the clicked face. */
    EDIT,
    /** Remove the nearest pipe; sneaking removes the whole bundle. */
    REMOVE,
    /** Remove every pipe of one kind along a straight run between two clicks. */
    REMOVE_RUN,
    /** Change the tier of the nearest pipe to the tier of the pipe item in the off hand. */
    REPLACE_TIER,
    /** Remember a bundle's settings. */
    COPY,
    /** Apply remembered settings to a bundle. */
    PASTE,
    /** Describe the nearest pipe's network and highlight it. */
    INSPECT;

    /** The next mode in the cycle. */
    public ToolMode next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
