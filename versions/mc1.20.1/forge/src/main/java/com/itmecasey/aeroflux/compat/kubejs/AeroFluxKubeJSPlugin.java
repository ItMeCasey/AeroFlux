package com.itmecasey.aeroflux.compat.kubejs;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.core.machine.MachineType;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.recipe.RecipeKey;
import dev.latvian.mods.kubejs.recipe.component.ItemComponents;
import dev.latvian.mods.kubejs.recipe.component.NumberComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.StringComponent;
import dev.latvian.mods.kubejs.recipe.schema.RecipeNamespace;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchema;
import dev.latvian.mods.kubejs.recipe.schema.RegisterRecipeSchemasEvent;

/**
 * KubeJS support: a recipe schema for each recipe machine, so packs can write
 * {@code event.recipes.aeroflux.grinder([{ingredient: '#forge:ores/iron'}], [{item: 'minecraft:raw_iron', count: 2}, {item: 'minecraft:gravel', chance: 0.1}], 200)}.
 * The keys follow the recipe files exactly: {@code inputs}, {@code outputs}, {@code time}, and the
 * optional {@code flux} and {@code flux_type}. KubeJS finds this class through
 * {@code kubejs.plugins.txt} and only when KubeJS is installed.
 */
public class AeroFluxKubeJSPlugin extends KubeJSPlugin {
    /** One input: an ingredient and how many of it a run uses (1 when left out). */
    private static final RecipeComponent<?> INPUT = RecipeComponent.builder(
            ItemComponents.INPUT.key("ingredient"),
            NumberComponent.INT.key("count").optional(1)).inputRole();

    /**
     * One output, written flat the way 1.20.1 recipe files keep it: the item id, a count (1 when
     * left out) and the chance of getting it (always, when left out).
     */
    private static final RecipeComponent<?> OUTPUT = RecipeComponent.builder(
            StringComponent.ID.key("item"),
            NumberComponent.INT.key("count").optional(1),
            NumberComponent.FLOAT.key("chance").optional(1.0F)).outputRole();

    private static final RecipeKey<?> INPUTS = INPUT.asArray().key("inputs");
    private static final RecipeKey<?> OUTPUTS = OUTPUT.asArray().key("outputs");
    private static final RecipeKey<?> TIME = NumberComponent.INT.key("time").optional(200);
    private static final RecipeKey<?> FLUX = NumberComponent.INT.key("flux").optional(0);
    private static final RecipeKey<?> FLUX_TYPE = StringComponent.ANY.key("flux_type").defaultOptional();

    @Override
    public void registerRecipeSchemas(RegisterRecipeSchemasEvent event) {
        RecipeNamespace namespace = event.namespace(AeroFlux.MODID);
        RecipeSchema schema = new RecipeSchema(INPUTS, OUTPUTS, TIME, FLUX, FLUX_TYPE);
        for (MachineType machine : MachineType.values()) {
            if (machine.hasOwnRecipes()) {
                namespace.register(machine.id(), schema);
            }
        }
    }
}
