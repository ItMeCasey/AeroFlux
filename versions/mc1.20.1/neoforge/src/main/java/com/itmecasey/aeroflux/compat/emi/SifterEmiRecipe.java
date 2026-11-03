package com.itmecasey.aeroflux.compat.emi;

import com.itmecasey.aeroflux.compat.ViewerRecipes;
import com.itmecasey.aeroflux.machine.SieveRoll;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * One Flux Sifter page in EMI: the block, the mesh (a catalyst, as it is never used up), and the
 * drops in rows of five, each with how many a run gives in its tooltip.
 */
final class SifterEmiRecipe extends BasicEmiRecipe {
    private static final int SLOT = 18;
    private static final int OUTPUT_X = 66;

    private final ViewerRecipes.SievePage page;

    SifterEmiRecipe(EmiRecipeCategory category, ViewerRecipes.SievePage page) {
        super(category, page.id(), OUTPUT_X + ViewerRecipes.SIEVE_COLUMNS * SLOT, 3 * SLOT);
        this.page = page;
        inputs.add(EmiIngredient.of(page.material()));
        catalysts.add(EmiIngredient.of(page.mesh()));
        for (SieveRoll drop : page.drops()) {
            outputs.add(EmiStack.of(drop.product()));
        }
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(inputs.get(0), 0, SLOT);
        widgets.addSlot(catalysts.get(0), SLOT, SLOT).catalyst(true);
        widgets.addTexture(EmiTexture.EMPTY_ARROW, 40, SLOT + 1);
        List<SieveRoll> drops = page.drops();
        for (int i = 0; i < drops.size(); i++) {
            int x = OUTPUT_X + (i % ViewerRecipes.SIEVE_COLUMNS) * SLOT;
            int y = (i / ViewerRecipes.SIEVE_COLUMNS) * SLOT;
            widgets.addSlot(outputs.get(i), x, y).appendTooltip(ViewerRecipes.amountLine(drops.get(i).amount())).recipeContext(this);
        }
        if (page.water() > 0) {
            widgets.addText(Component.translatable("jei.aeroflux.sieve.water", page.water()), 0, 2 * SLOT + 4, 0xFF3F76E4, false);
        }
    }
}
