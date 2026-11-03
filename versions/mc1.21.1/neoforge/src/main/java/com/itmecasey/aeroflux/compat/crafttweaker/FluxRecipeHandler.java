package com.itmecasey.aeroflux.compat.crafttweaker;

import com.blamejared.crafttweaker.api.ingredient.IIngredient;
import com.blamejared.crafttweaker.api.ingredient.IIngredientWithAmount;
import com.blamejared.crafttweaker.api.ingredient.type.IngredientWithAmount;
import com.blamejared.crafttweaker.api.item.IItemStack;
import com.blamejared.crafttweaker.api.recipe.component.BuiltinRecipeComponents;
import com.blamejared.crafttweaker.api.recipe.component.IDecomposedRecipe;
import com.blamejared.crafttweaker.api.recipe.component.IRecipeComponent;
import com.blamejared.crafttweaker.api.recipe.component.RecipeComponentEqualityCheckers;
import com.blamejared.crafttweaker.api.recipe.handler.IRecipeHandler;
import com.blamejared.crafttweaker.api.recipe.manager.base.IRecipeManager;
import com.blamejared.crafttweaker.api.util.random.Percentaged;
import com.google.gson.reflect.TypeToken;
import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

/**
 * Teaches CraftTweaker what a machine recipe is made of, which is what its recipe dump and its
 * replacer ({@code Replacer.create()...}) need: the inputs with their counts, the outputs with their
 * chances and the time. Flux, flux type, water and Silk Touch products ride along unchanged.
 * CraftTweaker finds this class by its annotation and only when it is installed.
 */
@IRecipeHandler.For(FluxRecipe.class)
public final class FluxRecipeHandler implements IRecipeHandler<FluxRecipe> {
    /** What the replacer has no business changing, carried through a decomposed recipe untouched. */
    record Extras(int flux, @Nullable FluxType fluxType, int water, List<FluxRecipe.Output> silkOutputs) {
    }

    /** Registered by {@link AeroFluxCraftTweakerPlugin}, as CraftTweaker wants every component it sees. */
    static final IRecipeComponent<Extras> EXTRAS = IRecipeComponent.simple(Ids.mod("metadata/machine_extras"),
            new TypeToken<>() {
            }, RecipeComponentEqualityCheckers::notComparable);

    @Override
    public String dumpToCommandString(IRecipeManager<? super FluxRecipe> manager, RegistryAccess registries,
                                      RecipeHolder<FluxRecipe> holder) {
        FluxRecipe recipe = holder.value();
        String inputs = inputs(recipe).stream().map(IIngredientWithAmount::getCommandString).collect(Collectors.joining(", "));
        String outputs = outputs(recipe.outputs()).stream().map(Percentaged::getCommandString).collect(Collectors.joining(", "));
        String fluxType = recipe.fluxType() == null ? "" : recipe.fluxType().name().toLowerCase(Locale.ROOT);
        return String.format(Locale.ROOT, "%s.addRecipe(\"%s\", [%s], [%s], %d, %d, \"%s\", %d);",
                manager.getCommandString(), holder.id(), inputs, outputs, recipe.time(), recipe.flux(), fluxType, recipe.water());
    }

    /** Machine recipes may share inputs (the first that matches runs), so two never conflict. */
    @Override
    public <U extends Recipe<?>> boolean doesConflict(IRecipeManager<? super FluxRecipe> manager, FluxRecipe first, U second) {
        return false;
    }

    @Override
    public Optional<IDecomposedRecipe> decompose(IRecipeManager<? super FluxRecipe> manager, RegistryAccess registries,
                                                 FluxRecipe recipe) {
        return Optional.of(IDecomposedRecipe.builder()
                .with(BuiltinRecipeComponents.Input.INGREDIENTS_WITH_AMOUNTS, inputs(recipe))
                .with(BuiltinRecipeComponents.Output.CHANCED_ITEMS, outputs(recipe.outputs()))
                .with(BuiltinRecipeComponents.Processing.TIME, recipe.time())
                .with(EXTRAS, new Extras(recipe.flux(), recipe.fluxType(), recipe.water(), recipe.silkOutputs()))
                .build());
    }

    @Override
    public Optional<FluxRecipe> recompose(IRecipeManager<? super FluxRecipe> manager, RegistryAccess registries,
                                          IDecomposedRecipe recipe) {
        if (!(manager instanceof FluxRecipeManager machineManager)) {
            return Optional.empty();
        }
        List<IIngredientWithAmount> inputs = recipe.getOrThrow(BuiltinRecipeComponents.Input.INGREDIENTS_WITH_AMOUNTS);
        List<Percentaged<IItemStack>> outputs = recipe.getOrThrow(BuiltinRecipeComponents.Output.CHANCED_ITEMS);
        int time = recipe.getOrThrowSingle(BuiltinRecipeComponents.Processing.TIME);
        Extras extras = recipe.getOrThrowSingle(EXTRAS);
        FluxRecipe rebuilt = FluxRecipeManager.build(machineManager.machine(), inputs, outputs, List.of(), time, extras.flux(),
                extras.fluxType(), extras.water());
        return Optional.of(new FluxRecipe(rebuilt.machine(), rebuilt.inputs(), rebuilt.outputs(), rebuilt.time(), rebuilt.flux(),
                rebuilt.fluxType(), rebuilt.water(), extras.silkOutputs()));
    }

    private static List<IIngredientWithAmount> inputs(FluxRecipe recipe) {
        List<IIngredientWithAmount> inputs = new ArrayList<>();
        for (FluxRecipe.Input input : recipe.inputs()) {
            inputs.add(new IngredientWithAmount(IIngredient.fromIngredient(input.ingredient()), input.count()));
        }
        return inputs;
    }

    private static List<Percentaged<IItemStack>> outputs(List<FluxRecipe.Output> outputs) {
        List<Percentaged<IItemStack>> result = new ArrayList<>();
        for (FluxRecipe.Output output : outputs) {
            result.add(IItemStack.of(output.stack().copy()).percent(output.chance() * 100.0));
        }
        return result;
    }
}
