package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import com.itmecasey.aeroflux.registry.ModRecipes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/**
 * Where a machine finds its recipes. Since Minecraft 26.1 only the server knows recipes, so the
 * machine recipe types are listed here to be sent to every player, and the client keeps what it
 * receives in {@link #received}. That lets the menu on the client judge an item against the same
 * recipes as the server does when a player drags it over an input slot.
 */
@EventBusSubscriber(modid = AeroFlux.MODID)
public final class MachineRecipes {
    /** The recipes the server last sent this client; empty on a server or before the first sync. */
    private static volatile RecipeMap received = RecipeMap.EMPTY;

    private MachineRecipes() {
    }

    /** Asks for the recipes of every machine with recipes of its own to go to the players. */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        for (MachineType machine : MachineType.values()) {
            if (machine.hasOwnRecipes()) {
                event.sendRecipes(ModRecipes.type(machine).get());
            }
        }
    }

    /** Client side: remembers what the server sent. Called from the client setup. */
    public static void receive(RecipeMap recipes) {
        received = recipes;
    }

    /** Only the machine's own recipes (not the imported ones), as the given side knows them. */
    public static Collection<RecipeHolder<FluxRecipe>> ownOf(Level level, MachineType machine) {
        RecipeType<FluxRecipe> type = ModRecipes.type(machine).get();
        if (level instanceof ServerLevel server) {
            return new ArrayList<>(server.recipeAccess().recipeMap().byType(type));
        }
        return new ArrayList<>(received.byType(type));
    }

    /** The recipes of one machine, as the given side knows them. */
    public static Collection<RecipeHolder<FluxRecipe>> of(Level level, MachineType machine) {
        RecipeType<FluxRecipe> type = ModRecipes.type(machine).get();
        if (level instanceof ServerLevel server) {
            List<RecipeHolder<FluxRecipe>> recipes = new ArrayList<>(server.recipeAccess().recipeMap().byType(type));
            recipes.addAll(RecipeImports.of(server, machine));
            return recipes;
        }
        return new ArrayList<>(received.byType(type));
    }
}
