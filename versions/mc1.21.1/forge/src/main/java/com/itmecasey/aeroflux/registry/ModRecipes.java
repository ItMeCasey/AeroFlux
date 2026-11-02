package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipeSerializer;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The recipe types and serializers of the machines that have recipes of their own. Each type is
 * named after its machine ({@code aeroflux:grinder} and so on), which is also the {@code type}
 * a recipe file gives.
 */
public final class ModRecipes {
    private static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, AeroFlux.MODID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, AeroFlux.MODID);

    private static final Map<MachineType, RegistryObject<RecipeType<FluxRecipe>>> BY_TYPE =
            new EnumMap<>(MachineType.class);
    private static final Map<MachineType, RegistryObject<RecipeSerializer<FluxRecipe>>> BY_SERIALIZER =
            new EnumMap<>(MachineType.class);

    static {
        for (MachineType machine : MachineType.values()) {
            if (machine.hasOwnRecipes()) {
                BY_TYPE.put(machine, TYPES.register(machine.id(),
                        () -> RecipeType.<FluxRecipe>simple(Ids.mod(machine.id()))));
                BY_SERIALIZER.put(machine, SERIALIZERS.register(machine.id(), () -> new FluxRecipeSerializer(machine)));
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

    /** Hooks this registry into the mod event bus; call once from the mod constructor. */
    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
        SERIALIZERS.register(modEventBus);
    }
}
