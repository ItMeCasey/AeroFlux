package com.itmecasey.aeroflux.compat.emi;

import com.itmecasey.aeroflux.compat.ViewerRecipes;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.network.chat.Component;

/**
 * One machine recipe in EMI, laid out like the JEI page: the inputs, an arrow that fills as long
 * as the copper machine takes, the outputs (EMI shows their chance itself), the time and Flux
 * below, and for an imported recipe the mod it came from.
 */
final class MachineEmiRecipe extends BasicEmiRecipe {
    private static final int WIDTH = 150;
    private static final int SLOT = 18;
    private static final int OUTPUT_X = 92;
    /** One game tick in milliseconds, for the arrow. */
    private static final int TICK_MS = 50;

    private final FluxRecipe recipe;
    private final Component cost;
    private final Component imported;

    MachineEmiRecipe(EmiRecipeCategory category, FluxRecipe recipe) {
        super(category, recipe.getId(), WIDTH, 0);
        this.recipe = recipe;
        for (FluxRecipe.Input input : recipe.inputs()) {
            inputs.add(EmiIngredient.of(input.ingredient(), input.count()));
        }
        for (FluxRecipe.Output output : recipe.outputs()) {
            outputs.add(EmiStack.of(output.stack()).setChance(output.chance()));
        }
        this.cost = recipe.flux() > 0
                ? Component.translatable("jei.aeroflux.time_and_flux", recipe.time(), recipe.flux())
                : Component.translatable("jei.aeroflux.time", recipe.time());
        this.imported = ViewerRecipes.importedLine(recipe.getId());
        this.height = imported == null ? 40 : 50;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        for (int i = 0; i < inputs.size(); i++) {
            widgets.addSlot(inputs.get(i), 1 + i * SLOT, 5);
        }
        widgets.addFillingArrow(62, 6, recipe.time() * TICK_MS);
        for (int i = 0; i < outputs.size(); i++) {
            widgets.addSlot(outputs.get(i), OUTPUT_X + i * SLOT, 5).recipeContext(this);
        }
        widgets.addText(cost, 0, 28, 0xFF606060, false);
        if (imported != null) {
            widgets.addText(imported, 0, 39, 0xFF808080, false);
        }
    }
}
