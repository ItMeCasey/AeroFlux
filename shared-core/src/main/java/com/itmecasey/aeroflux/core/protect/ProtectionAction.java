package com.itmecasey.aeroflux.core.protect;

/**
 * What a machine or tool is about to do to a place or an entity, in the terms claim mods protect.
 * The game side turns each one into the standard permission event for it.
 */
public enum ProtectionAction {
    /** Break a block: the check a player breaking it would meet. */
    BREAK_BLOCK,
    /** Place a block. */
    PLACE_BLOCK,
    /** Use or change a block without breaking it, such as editing a pipe or pasting a setting. */
    USE_BLOCK,
    /** Interact with an entity: feeding an animal, picking an item up. */
    USE_ENTITY
}
