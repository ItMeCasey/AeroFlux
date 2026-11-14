package com.itmecasey.aeroflux.core.script;

/**
 * How AeroFlux's recipe files spell ingredients and results on one Minecraft version. A script writes the same call on every
 * version; {@link MachineRecipeBuilder} turns it into the shape that version's recipe loader reads.
 */
public enum RecipeShape {
    /** 1.20.1: an ingredient is an object, and a result is one flat object with the item id, the count and the chance. */
    V1_20(true, false),
    /** 1.21.1: an ingredient is an object, and a result holds an item stack object. */
    V1_21(true, true),
    /** 26.x: an ingredient is a string ({@code #} marks a tag), and a result holds an item stack object. */
    V26(false, true);

    final boolean objectIngredients;
    final boolean stackResults;

    RecipeShape(boolean objectIngredients, boolean stackResults) {
        this.objectIngredients = objectIngredients;
        this.stackResults = stackResults;
    }
}
