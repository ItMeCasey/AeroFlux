package com.itmecasey.aeroflux.compat.kubejs;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.core.machine.MachineType;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.recipe.RecipeKey;
import dev.latvian.mods.kubejs.recipe.component.CustomObjectRecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.IngredientComponent;
import dev.latvian.mods.kubejs.recipe.component.ItemStackComponent;
import dev.latvian.mods.kubejs.recipe.component.NumberComponent;
import dev.latvian.mods.kubejs.recipe.component.StringComponent;
import dev.latvian.mods.kubejs.recipe.schema.RecipeNamespace;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchema;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaRegistry;
import java.util.List;

/**
 * KubeJS support: a recipe schema for each recipe machine, so packs can write
 * {@code event.recipes.aeroflux.grinder([{ingredient: '#c:ores/iron'}], [{item: '2x minecraft:raw_iron'}, {item: 'minecraft:gravel', chance: 0.1}], 200)}.
 * The keys follow the recipe files exactly: {@code inputs}, {@code outputs}, {@code time}, and the
 * optional {@code flux} and {@code flux_type}. KubeJS finds this class through
 * {@code kubejs.plugins.txt} and only when KubeJS is installed.
 */
public class AeroFluxKubeJSPlugin implements KubeJSPlugin {
    /** One input: an ingredient and how many of it a run uses (1 when left out). */
    private static final CustomObjectRecipeComponent INPUT = new CustomObjectRecipeComponent(List.of(
            new CustomObjectRecipeComponent.Key("ingredient", IngredientComponent.INGREDIENT),
            new CustomObjectRecipeComponent.Key("count", NumberComponent.INT, true)));

    /** One output: an item stack and the chance of getting it (always, when left out). */
    private static final CustomObjectRecipeComponent OUTPUT = new CustomObjectRecipeComponent(List.of(
            new CustomObjectRecipeComponent.Key("item", ItemStackComponent.ITEM_STACK),
            new CustomObjectRecipeComponent.Key("chance", NumberComponent.FLOAT, true)));

    private static final RecipeKey<?> INPUTS = INPUT.asList().inputKey("inputs");
    private static final RecipeKey<?> OUTPUTS = OUTPUT.asList().outputKey("outputs");
    private static final RecipeKey<?> TIME = NumberComponent.INT.otherKey("time").optional(200);
    private static final RecipeKey<?> FLUX = NumberComponent.INT.otherKey("flux").defaultOptional();
    private static final RecipeKey<?> FLUX_TYPE = StringComponent.STRING.otherKey("flux_type").defaultOptional();

    @Override
    public void registerRecipeSchemas(RecipeSchemaRegistry registry) {
        RecipeNamespace namespace = registry.namespace(AeroFlux.MODID);
        RecipeSchema schema = new RecipeSchema(INPUTS, OUTPUTS, TIME, FLUX, FLUX_TYPE);
        for (MachineType machine : MachineType.values()) {
            if (machine.hasOwnRecipes()) {
                namespace.register(machine.id(), schema);
            }
        }
    }
}
