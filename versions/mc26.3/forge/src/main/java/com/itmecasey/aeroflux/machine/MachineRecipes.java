package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import com.itmecasey.aeroflux.registry.ModRecipes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * Where the recipe viewers find a machine's own recipes. Since Minecraft 26.1 only the server
 * knows recipes, and Forge has no hook to send a mod's recipe types to its players, so
 * {@link RecipeSync} sends them in a message of AeroFlux's own and the client keeps what it
 * receives here. The imported recipes and the sieve drops travel in the same message (see
 * {@link RecipeImports} and {@link SieveImports}).
 */
public final class MachineRecipes {
    /** The machines' own recipes the server last sent this client; empty on a server or before the first sync. */
    private static volatile Map<MachineType, List<RecipeHolder<FluxRecipe>>> received = Map.of();

    private MachineRecipes() {
    }

    /** Client side: remembers what the server sent. */
    static void receive(Map<MachineType, List<RecipeHolder<FluxRecipe>>> recipes) {
        received = Map.copyOf(recipes);
    }

    /** Only the machine's own recipes (not the imported ones), as the given side knows them. */
    public static Collection<RecipeHolder<FluxRecipe>> ownOf(Level level, MachineType machine) {
        if (level instanceof ServerLevel server) {
            RecipeType<FluxRecipe> type = ModRecipes.type(machine).get();
            List<RecipeHolder<FluxRecipe>> own = new ArrayList<>();
            for (RecipeHolder<?> holder : server.recipeAccess().getRecipes()) {
                if (holder.value().getType() == type) {
                    @SuppressWarnings("unchecked")
                    RecipeHolder<FluxRecipe> typed = (RecipeHolder<FluxRecipe>) holder;
                    own.add(typed);
                }
            }
            return own;
        }
        return new ArrayList<>(received.getOrDefault(machine, List.of()));
    }
}
