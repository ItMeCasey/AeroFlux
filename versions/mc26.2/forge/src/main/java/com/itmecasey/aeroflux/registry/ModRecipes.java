package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipeSerializer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * The recipe types and serializers of the machines that have recipes of their own. Each type is
 * named after its machine ({@code aeroflux:grinder} and so on), which is also the {@code type}
 * a recipe file gives.
 */
public final class ModRecipes {
    private static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, AeroFlux.MODID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, AeroFlux.MODID);

    private static final Map<MachineType, RegistryObject<RecipeType<FluxRecipe>>> BY_TYPE =
            new EnumMap<>(MachineType.class);
    private static final Map<MachineType, RegistryObject<RecipeSerializer<FluxRecipe>>> BY_SERIALIZER =
            new EnumMap<>(MachineType.class);

    static {
        for (MachineType machine : MachineType.values()) {
            if (machine.hasOwnRecipes()) {
                BY_TYPE.put(machine, TYPES.register(machine.id(),
                        () -> RecipeType.<FluxRecipe>simple(Ids.mod(machine.id()))));
                BY_SERIALIZER.put(machine, SERIALIZERS.register(machine.id(),
                        () -> FluxRecipeSerializer.create(machine)));
            }
        }
    }

    private ModRecipes() {
    }

    /** The recipe type of a machine that has recipes of its own. */
    public static RegistryObject<RecipeType<FluxRecipe>> type(MachineType machine) {
        return BY_TYPE.get(machine);
    }

    /** The serializer of a machine that has recipes of its own. */
    public static RegistryObject<RecipeSerializer<FluxRecipe>> serializer(MachineType machine) {
        return BY_SERIALIZER.get(machine);
    }

    /** Every recipe of one type the server has loaded, in no particular order. */
    @SuppressWarnings("unchecked")
    public static <T extends Recipe<?>> List<RecipeHolder<T>> all(ServerLevel level, RecipeType<T> type) {
        List<RecipeHolder<T>> found = new ArrayList<>();
        for (RecipeHolder<?> holder : level.recipeAccess().getRecipes()) {
            if (holder.value().getType() == type) {
                found.add((RecipeHolder<T>) holder);
            }
        }
        return found;
    }

    /** Hooks this registry into the mod event bus group; call once from the mod constructor. */
    public static void register(BusGroup modBusGroup) {
        TYPES.register(modBusGroup);
        SERIALIZERS.register(modBusGroup);
    }
}
