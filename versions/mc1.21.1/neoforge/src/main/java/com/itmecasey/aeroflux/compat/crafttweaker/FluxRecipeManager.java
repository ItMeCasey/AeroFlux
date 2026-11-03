package com.itmecasey.aeroflux.compat.crafttweaker;

import com.blamejared.crafttweaker.api.CraftTweakerAPI;
import com.blamejared.crafttweaker.api.action.recipe.ActionAddRecipe;
import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.ingredient.IIngredient;
import com.blamejared.crafttweaker.api.ingredient.IIngredientWithAmount;
import com.blamejared.crafttweaker.api.item.IItemStack;
import com.blamejared.crafttweaker.api.recipe.manager.base.IRecipeManager;
import com.blamejared.crafttweaker.api.util.random.Percentaged;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import com.itmecasey.aeroflux.registry.ModRecipes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;
import org.openzen.zencode.java.ZenCodeType;

/**
 * CraftTweaker support: the recipe type of each recipe machine gets a manager, so a script can
 * write
 *
 * <pre>{@code <recipetype:aeroflux:grinder>.addRecipe("iron_dust", [<tag:item:c:ores/iron>], [<item:minecraft:raw_iron> * 2, <item:minecraft:gravel> % 10], 200);}</pre>
 *
 * <p>The arguments follow the recipe files and the KubeJS schema: the name, the inputs (each
 * with its count), the outputs (each with its chance, always when none is given), the time in
 * ticks, and optionally the extra Flux, the flux type ({@code "aero"} or {@code "moon"}, or
 * {@code ""} for either) and the water a Purifier run takes. {@link #addSilkRecipe} adds a Plant
 * Grower recipe with Silk Touch products. Removing works the way it does for every CraftTweaker
 * manager ({@code remove}, {@code removeByName}, {@code removeByInput}, {@code removeByModid},
 * {@code removeAll}), and {@link FluxRecipeHandler} lets the replacer edit machine recipes.
 *
 * <p>CraftTweaker finds the managers by their {@link ZenRegister} annotation and only when it is
 * installed. It wants one class per recipe type, hence the small classes at the bottom; this is
 * an interface, the way CraftTweaker's own cooking managers share their methods, so CraftTweaker
 * never tries to make one of it.
 */
@ZenRegister
@ZenCodeType.Name("mods.aeroflux.FluxRecipeManager")
public interface FluxRecipeManager extends IRecipeManager<FluxRecipe> {
    /** The machine whose recipes this manager edits. */
    MachineType machine();

    @Override
    default RecipeType<FluxRecipe> getRecipeType() {
        return ModRecipes.type(machine()).get();
    }

    /** Adds a machine recipe. */
    @ZenCodeType.Method
    default void addRecipe(String name, IIngredientWithAmount[] inputs, Percentaged<IItemStack>[] outputs,
                          @ZenCodeType.OptionalInt(200) int time, @ZenCodeType.OptionalInt(0) int flux,
                          @ZenCodeType.OptionalString("") String fluxType, @ZenCodeType.OptionalInt(0) int water) {
        add(name, inputs, outputs, new Percentaged[0], time, flux, fluxType, water);
    }

    /** Adds a Plant Grower recipe that gives {@code silkOutputs} instead when the grower has a Silk Touch Upgrade. */
    @ZenCodeType.Method
    default void addSilkRecipe(String name, IIngredientWithAmount[] inputs, Percentaged<IItemStack>[] outputs,
                              Percentaged<IItemStack>[] silkOutputs, @ZenCodeType.OptionalInt(200) int time,
                              @ZenCodeType.OptionalInt(0) int flux, @ZenCodeType.OptionalString("") String fluxType) {
        add(name, inputs, outputs, silkOutputs, time, flux, fluxType, 0);
    }

    private void add(String name, IIngredientWithAmount[] inputs, Percentaged<IItemStack>[] outputs,
                     Percentaged<IItemStack>[] silkOutputs, int time, int flux, String fluxType, int water) {
        FluxRecipe recipe = build(machine(), List.of(inputs), List.of(outputs), List.of(silkOutputs), time, flux, fluxType(fluxType), water);
        CraftTweakerAPI.apply(new ActionAddRecipe<>(this, createHolder(fixRecipeId(name), recipe)));
    }

    /**
     * A machine recipe from CraftTweaker's types, after the same checks the recipe files get.
     *
     * @throws IllegalArgumentException when the recipe could never run in the machine
     */
    static FluxRecipe build(MachineType machine, List<IIngredientWithAmount> inputs, List<Percentaged<IItemStack>> outputs,
                            List<Percentaged<IItemStack>> silkOutputs, int time, int flux, @Nullable FluxType fluxType, int water) {
        if (inputs.isEmpty() || inputs.size() > machine.inputSlots()) {
            throw new IllegalArgumentException(machine.displayName() + " recipes take 1 to " + machine.inputSlots() + " inputs");
        }
        if (outputs.isEmpty()) {
            throw new IllegalArgumentException("A machine recipe needs at least one output");
        }
        if (time < 1 || flux < 0 || water < 0) {
            throw new IllegalArgumentException("Time must be at least 1 tick, and Flux and water cannot be negative");
        }
        List<FluxRecipe.Input> recipeInputs = new ArrayList<>();
        for (IIngredientWithAmount input : inputs) {
            // An item written as <item:x> * 2 carries its count in the stack too; the count belongs
            // to the recipe input, so the ingredient itself must take a single item.
            IIngredient ingredient = input.ingredient() instanceof IItemStack stack ? stack.withAmount(1) : input.ingredient();
            recipeInputs.add(new FluxRecipe.Input(ingredient.asVanillaIngredient(), Math.clamp(input.amount(), 1, 64)));
        }
        return new FluxRecipe(machine, recipeInputs, outputs(outputs), time, flux, fluxType, water, outputs(silkOutputs));
    }

    private static List<FluxRecipe.Output> outputs(List<Percentaged<IItemStack>> outputs) {
        List<FluxRecipe.Output> result = new ArrayList<>();
        for (Percentaged<IItemStack> output : outputs) {
            result.add(new FluxRecipe.Output(output.getData().getInternal().copy(), (float) Math.clamp(output.getPercentage(), 0.0, 1.0)));
        }
        return result;
    }

    /** {@code "aero"} or {@code "moon"} as a flux type; empty for either. */
    private static @Nullable FluxType fluxType(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "aero" -> FluxType.AERO;
            case "moon" -> FluxType.MOON;
            default -> throw new IllegalArgumentException("Unknown flux type '" + name + "', use \"aero\", \"moon\" or \"\"");
        };
    }

    // --- One manager per machine; CraftTweaker finds each through its INSTANCE field -------------

    @ZenRegister
    @ZenCodeType.Name("mods.aeroflux.Grinder")
    final class Grinder implements FluxRecipeManager {
        public static final Grinder INSTANCE = new Grinder();

        private Grinder() {
        }

        @Override
        public MachineType machine() {
            return MachineType.GRINDER;
        }
    }

    @ZenRegister
    @ZenCodeType.Name("mods.aeroflux.Sawmill")
    final class Sawmill implements FluxRecipeManager {
        public static final Sawmill INSTANCE = new Sawmill();

        private Sawmill() {
        }

        @Override
        public MachineType machine() {
            return MachineType.SAWMILL;
        }
    }

    @ZenRegister
    @ZenCodeType.Name("mods.aeroflux.AlloySmelter")
    final class AlloySmelter implements FluxRecipeManager {
        public static final AlloySmelter INSTANCE = new AlloySmelter();

        private AlloySmelter() {
        }

        @Override
        public MachineType machine() {
            return MachineType.ALLOY_SMELTER;
        }
    }

    @ZenRegister
    @ZenCodeType.Name("mods.aeroflux.Infuser")
    final class Infuser implements FluxRecipeManager {
        public static final Infuser INSTANCE = new Infuser();

        private Infuser() {
        }

        @Override
        public MachineType machine() {
            return MachineType.INFUSER;
        }
    }

    @ZenRegister
    @ZenCodeType.Name("mods.aeroflux.PlantGrower")
    final class PlantGrower implements FluxRecipeManager {
        public static final PlantGrower INSTANCE = new PlantGrower();

        private PlantGrower() {
        }

        @Override
        public MachineType machine() {
            return MachineType.PLANT_GROWER;
        }
    }

    @ZenRegister
    @ZenCodeType.Name("mods.aeroflux.Purifier")
    final class Purifier implements FluxRecipeManager {
        public static final Purifier INSTANCE = new Purifier();

        private Purifier() {
        }

        @Override
        public MachineType machine() {
            return MachineType.PURIFIER;
        }
    }

    /** The manager for a machine, for code that has a machine and wants its manager. */
    static FluxRecipeManager of(MachineType machine) {
        return switch (machine) {
            case GRINDER -> Grinder.INSTANCE;
            case SAWMILL -> Sawmill.INSTANCE;
            case ALLOY_SMELTER -> AlloySmelter.INSTANCE;
            case INFUSER -> Infuser.INSTANCE;
            case PLANT_GROWER -> PlantGrower.INSTANCE;
            case PURIFIER -> Purifier.INSTANCE;
            default -> throw new IllegalArgumentException(machine + " has no recipes of its own");
        };
    }
}
