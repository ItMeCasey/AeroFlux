package com.itmecasey.aeroflux.compat.jei;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.compat.ViewerRecipes;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.machine.SieveRoll;
import com.itmecasey.aeroflux.registry.ModBlocks;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * The Flux Sifter's JEI pages: the block, the mesh, and every drop of sifting the one through the
 * other, each with how many a run gives. The drops come from Ex Deorum and Ex Nihilo Sequentia
 * (see {@link ViewerRecipes#sifter}); the mesh is a catalyst, as it is never used up.
 */
final class SifterRecipeCategory implements IRecipeCategory<ViewerRecipes.SievePage> {
    static final RecipeType<ViewerRecipes.SievePage> TYPE = RecipeType.create(AeroFlux.MODID, "sifter",
            ViewerRecipes.SievePage.class);
    private static final int SLOT = 18;
    private static final int OUTPUT_X = 66;
    private static final int WIDTH = OUTPUT_X + ViewerRecipes.SIEVE_COLUMNS * SLOT;
    private static final int HEIGHT = 3 * SLOT;

    private final IDrawable icon;

    SifterRecipeCategory(IGuiHelper gui) {
        this.icon = gui.createDrawableItemLike(ModBlocks.machine(MachineType.SIFTER, Tier.COPPER).get());
    }

    @Override
    public RecipeType<ViewerRecipes.SievePage> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return ModBlocks.machine(MachineType.SIFTER, Tier.COPPER).get().getName();
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ViewerRecipes.SievePage page, IFocusGroup focuses) {
        builder.addInputSlot(1, SLOT).setStandardSlotBackground().addIngredients(page.material());
        builder.addSlot(RecipeIngredientRole.CATALYST, 1 + SLOT, SLOT).setStandardSlotBackground().addIngredients(page.mesh());
        List<SieveRoll> drops = page.drops();
        for (int i = 0; i < drops.size(); i++) {
            SieveRoll drop = drops.get(i);
            int x = OUTPUT_X + (i % ViewerRecipes.SIEVE_COLUMNS) * SLOT;
            int y = (i / ViewerRecipes.SIEVE_COLUMNS) * SLOT;
            builder.addOutputSlot(x + 1, y + 1)
                    .setStandardSlotBackground()
                    .addItemStack(drop.product())
                    .addRichTooltipCallback((view, tooltip) ->
                            tooltip.add(ViewerRecipes.amountLine(drop.amount()).copy().withStyle(ChatFormatting.GOLD)));
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, ViewerRecipes.SievePage page, IFocusGroup focuses) {
        builder.addRecipeArrowWidget().setPosition(40, SLOT);
        if (page.water() > 0) {
            builder.addText(Component.translatable("jei.aeroflux.sieve.water", page.water()), OUTPUT_X, 10)
                    .setPosition(0, 2 * SLOT + 4).setColor(0xFF3F76E4);
        }
    }
}
