package com.itmecasey.aeroflux.core.script;

import com.itmecasey.aeroflux.core.conduit.PipeType;

/**
 * What the script API needs from the game, implemented by each Minecraft version. Every method answers from the server's own
 * state, returns {@code null} when there is nothing at that place (or no server yet), and never changes anything.
 */
public interface ScriptWorld {
    /** The mod's version, such as {@code 2026.11.12}. */
    String version();

    /** How this Minecraft version spells recipe JSON. */
    RecipeShape recipeShape();

    /** The machine at a block, or {@code null}. {@code dimension} is an id such as {@code minecraft:overworld}. */
    ScriptInfo.Machine machine(String dimension, int x, int y, int z);

    /** The Flux held by the machine, collector, tank, converter or chunk loader at a block, or {@code null}. */
    ScriptInfo.Flux flux(String dimension, int x, int y, int z);

    /** The network of one pipe kind through the bundle at a block, or {@code null}. */
    ScriptInfo.Network network(String dimension, int x, int y, int z, PipeType pipe);

    /** An online player's chunk loader numbers, or {@code null} when nobody has that name. */
    ScriptInfo.Player player(String name);
}
