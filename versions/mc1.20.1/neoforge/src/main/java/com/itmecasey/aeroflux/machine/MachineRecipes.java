package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import com.itmecasey.aeroflux.registry.ModRecipes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Where a machine finds its recipes: its own recipe type, and on the server any recipes imported
 * from other mods (see {@link RecipeImports}). On 1.20.1 the client keeps the full recipe manager,
 * so the menu judges an item against the same recipes the server does.
 */
public final class MachineRecipes {
    private MachineRecipes() {
    }

    /** The recipes of one machine, as the given side knows them. */
    public static Collection<FluxRecipe> of(Level level, MachineType machine) {
        List<FluxRecipe> recipes = new ArrayList<>(level.getRecipeManager().getAllRecipesFor(ModRecipes.type(machine).get()));
        if (level instanceof ServerLevel server) {
            recipes.addAll(RecipeImports.of(server, machine));
        }
        return recipes;
    }
}
