package com.itmecasey.aeroflux.compat.rei;

import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.compat.ViewerRecipes;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import com.itmecasey.aeroflux.registry.ModBlocks;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.forge.REIPluginClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * REI support, the same pages as the JEI plugin: one per recipe machine, with every tier of the
 * machine as its workstation. The displays are built on the client from the recipes the server
 * sent, so they are never sent anywhere themselves. REI finds this class by its annotation and
 * only when REI is installed.
 */
@REIPluginClient
public class AeroFluxReiPlugin implements REIClientPlugin {
    @Override
    public void registerCategories(CategoryRegistry registry) {
        for (MachineType machine : MachineType.values()) {
            if (machine.hasOwnRecipes()) {
                registry.add(new Category(machine));
                registry.configure(id(machine), config -> {
                    for (Tier tier : Tier.values()) {
                        config.addWorkstations(EntryStacks.of(ModBlocks.machine(machine, tier).get()));
                    }
                });
            }
        }
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        for (MachineType machine : MachineType.values()) {
            if (machine.hasOwnRecipes()) {
                for (FluxRecipe recipe : ViewerRecipes.machine(minecraft.level, machine)) {
                    registry.add(new MachineDisplay(recipe, Optional.of(recipe.getId())));
                }
            }
        }
    }

    private static CategoryIdentifier<MachineDisplay> id(MachineType machine) {
        return CategoryIdentifier.of(Ids.mod(machine.id()));
    }

    /** One machine recipe as REI sees it. */
    static final class MachineDisplay extends BasicDisplay {
        private final FluxRecipe recipe;

        MachineDisplay(FluxRecipe recipe, Optional<ResourceLocation> location) {
            super(inputs(recipe), outputs(recipe), location);
            this.recipe = recipe;
        }

        FluxRecipe recipe() {
            return recipe;
        }

        private static List<EntryIngredient> inputs(FluxRecipe recipe) {
            List<EntryIngredient> inputs = new ArrayList<>();
            for (FluxRecipe.Input input : recipe.inputs()) {
                List<ItemStack> stacks = Arrays.stream(input.ingredient().getItems())
                        .map(stack -> stack.copyWithCount(input.count())).toList();
                inputs.add(EntryIngredients.ofItemStacks(stacks));
            }
            return inputs;
        }

        private static List<EntryIngredient> outputs(FluxRecipe recipe) {
            List<EntryIngredient> outputs = new ArrayList<>();
            for (FluxRecipe.Output output : recipe.outputs()) {
                outputs.add(EntryIngredients.of(output.stack()));
            }
            return outputs;
        }

        @Override
        public CategoryIdentifier<?> getCategoryIdentifier() {
            return id(recipe.machine());
        }

    }

    /** The page type for one machine: inputs, an arrow, outputs, and the time and Flux below. */
    private static final class Category implements DisplayCategory<MachineDisplay> {
        private final MachineType machine;

        Category(MachineType machine) {
            this.machine = machine;
        }

        @Override
        public CategoryIdentifier<? extends MachineDisplay> getCategoryIdentifier() {
            return id(machine);
        }

        @Override
        public Component getTitle() {
            return ModBlocks.machine(machine, Tier.COPPER).get().getName();
        }

        @Override
        public Renderer getIcon() {
            return EntryStacks.of(ModBlocks.machine(machine, Tier.COPPER).get());
        }

        @Override
        public int getDisplayHeight() {
            return 62;
        }

        @Override
        public List<Widget> setupDisplay(MachineDisplay display, Rectangle bounds) {
            List<Widget> widgets = new ArrayList<>();
            widgets.add(Widgets.createRecipeBase(bounds));
            int x = bounds.x + 6;
            int y = bounds.y + 8;
            List<EntryIngredient> inputs = display.getInputEntries();
            for (int i = 0; i < inputs.size(); i++) {
                widgets.add(Widgets.createSlot(new Point(x + i * 18, y)).entries(inputs.get(i)).markInput());
            }
            widgets.add(Widgets.createArrow(new Point(x + 60, y)));
            List<EntryIngredient> outputs = display.getOutputEntries();
            for (int i = 0; i < outputs.size(); i++) {
                widgets.add(Widgets.createSlot(new Point(x + 92 + i * 18, y)).entries(outputs.get(i)).markOutput());
            }
            FluxRecipe recipe = display.recipe();
            Component cost = recipe.flux() > 0
                    ? Component.translatable("jei.aeroflux.time_and_flux", recipe.time(), recipe.flux())
                    : Component.translatable("jei.aeroflux.time", recipe.time());
            widgets.add(Widgets.createLabel(new Point(x, y + 24), cost).leftAligned().noShadow().color(0xFF606060));
            display.getDisplayLocation().map(ViewerRecipes::importedLine).ifPresent(imported ->
                    widgets.add(Widgets.createLabel(new Point(x, y + 34), imported).leftAligned().noShadow().color(0xFF808080)));
            return widgets;
        }
    }
}
