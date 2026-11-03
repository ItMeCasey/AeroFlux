package com.itmecasey.aeroflux.compat.jei;

import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.compat.ViewerRecipes;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import com.itmecasey.aeroflux.registry.ModBlocks;
import java.util.Arrays;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * One JEI page type for one recipe machine (Grinder, Sawmill, Alloy Smelter, Infuser, Plant
 * Grower, Purifier): the inputs, an arrow that runs as long as the copper machine takes, the
 * outputs with their chance, and the Flux the job costs on top of the running cost. A recipe
 * imported from another mod says which one below that.
 */
final class MachineRecipeCategory implements IRecipeCategory<RecipeHolder<FluxRecipe>> {
    private static final int WIDTH = 150;
    private static final int HEIGHT = 54;
    private static final int SLOT = 18;
    private static final int OUTPUT_X = 92;

    private final MachineType machine;
    private final RecipeType<RecipeHolder<FluxRecipe>> type;
    private final IDrawable icon;

    MachineRecipeCategory(MachineType machine, IGuiHelper gui) {
        this.machine = machine;
        this.type = typeFor(machine);
        this.icon = gui.createDrawableItemLike(ModBlocks.machine(machine, Tier.COPPER).get());
    }

    /** The JEI recipe type for a machine's recipes. */
    static RecipeType<RecipeHolder<FluxRecipe>> typeFor(MachineType machine) {
        return RecipeType.createRecipeHolderType(Ids.mod(machine.id()));
    }

    @Override
    public RecipeType<RecipeHolder<FluxRecipe>> getRecipeType() {
        return type;
    }

    @Override
    public Component getTitle() {
        return ModBlocks.machine(machine, Tier.COPPER).get().getName();
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
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<FluxRecipe> holder, IFocusGroup focuses) {
        FluxRecipe recipe = holder.value();
        List<FluxRecipe.Input> inputs = recipe.inputs();
        for (int i = 0; i < inputs.size(); i++) {
            FluxRecipe.Input input = inputs.get(i);
            builder.addInputSlot(1 + i * SLOT, 5)
                    .setStandardSlotBackground()
                    .addItemStacks(withCount(input.ingredient(), input.count()));
        }
        List<FluxRecipe.Output> outputs = recipe.outputs();
        for (int i = 0; i < outputs.size(); i++) {
            FluxRecipe.Output output = outputs.get(i);
            builder.addOutputSlot(OUTPUT_X + i * SLOT, 5)
                    .setStandardSlotBackground()
                    .addItemStack(output.stack())
                    .addRichTooltipCallback((view, tooltip) -> {
                        if (output.chance() < 1.0F) {
                            tooltip.add(Component.translatable("jei.aeroflux.chance", Math.round(output.chance() * 100))
                                    .withStyle(ChatFormatting.GOLD));
                        }
                    });
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<FluxRecipe> holder, IFocusGroup focuses) {
        FluxRecipe recipe = holder.value();
        builder.addAnimatedRecipeArrowWidget(recipe.time()).setPosition(62, 5);
        Component cost = recipe.flux() > 0
                ? Component.translatable("jei.aeroflux.time_and_flux", recipe.time(), recipe.flux())
                : Component.translatable("jei.aeroflux.time", recipe.time());
        builder.addText(cost, WIDTH, 10).setPosition(0, 30).setColor(0xFF606060);
        Component imported = ViewerRecipes.importedLine(holder.id());
        if (imported != null) {
            builder.addText(imported, WIDTH, 10).setPosition(0, 42).setColor(0xFF808080);
        }
    }

    @Override
    public ResourceLocation getRegistryName(RecipeHolder<FluxRecipe> holder) {
        return holder.id();
    }

    /** The items an ingredient accepts, each in the amount the recipe needs. */
    private static List<ItemStack> withCount(Ingredient ingredient, int count) {
        return Arrays.stream(ingredient.getItems()).map(stack -> stack.copyWithCount(count)).toList();
    }
}
