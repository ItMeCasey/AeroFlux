package com.itmecasey.aeroflux.compat.rei;

import com.mojang.serialization.MapCodec;

import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.compat.ViewerRecipes;
import com.itmecasey.aeroflux.machine.SieveRoll;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import com.itmecasey.aeroflux.registry.ModBlocks;
import java.util.ArrayList;
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
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.forge.REIPluginClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
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
        registry.add(new SifterCategory());
        registry.configure(SIFTER, config -> {
            for (Tier tier : Tier.values()) {
                config.addWorkstations(EntryStacks.of(ModBlocks.machine(MachineType.SIFTER, tier).get()));
            }
        });
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
                for (ViewerRecipes.Entry entry : ViewerRecipes.machine(minecraft.level, machine)) {
                    registry.add(new MachineDisplay(entry.recipe(), Optional.of(entry.id())));
                }
            }
        }
        for (ViewerRecipes.SievePage page : ViewerRecipes.sifter(minecraft.level)) {
            registry.add(new SifterDisplay(page));
        }
    }

    private static final CategoryIdentifier<SifterDisplay> SIFTER = CategoryIdentifier.of(Ids.mod("sifter"));

    private static CategoryIdentifier<MachineDisplay> id(MachineType machine) {
        return CategoryIdentifier.of(Ids.mod(machine.id()));
    }

    /** One machine recipe as REI sees it. */
    static final class MachineDisplay extends BasicDisplay {
        /**
         * These displays are made on the client and never travel, so the serializer only exists
         * because REI asks every display for one; it refuses to encode anything.
         */
        private static final DisplaySerializer<MachineDisplay> SERIALIZER = DisplaySerializer.of(
                MapCodec.unit(() -> {
                    throw new UnsupportedOperationException("AeroFlux REI displays are client-side only");
                }),
                StreamCodec.of((buf, display) -> {
                    throw new UnsupportedOperationException("AeroFlux REI displays are client-side only");
                }, buf -> {
                    throw new UnsupportedOperationException("AeroFlux REI displays are client-side only");
                }),
                false);

        /** The same refusing serializer for the Sifter's displays, which never travel either. */
        @SuppressWarnings({"unchecked", "rawtypes"})
        static final DisplaySerializer<SifterDisplay> SERIALIZER_FOR_SIFTER = (DisplaySerializer) SERIALIZER;

        private final FluxRecipe recipe;

        MachineDisplay(FluxRecipe recipe, Optional<Identifier> location) {
            super(inputs(recipe), outputs(recipe), location);
            this.recipe = recipe;
        }

        FluxRecipe recipe() {
            return recipe;
        }

        private static List<EntryIngredient> inputs(FluxRecipe recipe) {
            List<EntryIngredient> inputs = new ArrayList<>();
            for (FluxRecipe.Input input : recipe.inputs()) {
                List<ItemStack> stacks = input.ingredient().getValues().stream()
                        .map(holder -> new ItemStack(holder, input.count())).toList();
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

        @Override
        public DisplaySerializer<? extends MachineDisplay> getSerializer() {
            return SERIALIZER;
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
            return 52;
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

    /**
     * One Sifter page: the block and the mesh go in, the drops come out. The mesh is listed as an
     * input so looking up a mesh finds its pages, even though the Sifter never uses it up.
     */
    static final class SifterDisplay extends BasicDisplay {
        private final ViewerRecipes.SievePage page;

        SifterDisplay(ViewerRecipes.SievePage page) {
            super(List.of(EntryIngredients.ofIngredient(page.material()), EntryIngredients.ofIngredient(page.mesh())),
                    page.drops().stream().map(drop -> EntryIngredients.of(drop.product().create())).toList(),
                    Optional.of(page.id()));
            this.page = page;
        }

        ViewerRecipes.SievePage page() {
            return page;
        }

        @Override
        public CategoryIdentifier<?> getCategoryIdentifier() {
            return SIFTER;
        }

        @Override
        public DisplaySerializer<? extends SifterDisplay> getSerializer() {
            return MachineDisplay.SERIALIZER_FOR_SIFTER;
        }
    }

    /** The Sifter's page type: block and mesh, an arrow, and the drops in rows of five with their amounts. */
    private static final class SifterCategory implements DisplayCategory<SifterDisplay> {
        @Override
        public CategoryIdentifier<? extends SifterDisplay> getCategoryIdentifier() {
            return SIFTER;
        }

        @Override
        public Component getTitle() {
            return ModBlocks.machine(MachineType.SIFTER, Tier.COPPER).get().getName();
        }

        @Override
        public Renderer getIcon() {
            return EntryStacks.of(ModBlocks.machine(MachineType.SIFTER, Tier.COPPER).get());
        }

        @Override
        public int getDisplayHeight() {
            return 66;
        }

        @Override
        public int getDisplayWidth(SifterDisplay display) {
            return 76 + ViewerRecipes.SIEVE_COLUMNS * 18;
        }

        @Override
        public List<Widget> setupDisplay(SifterDisplay display, Rectangle bounds) {
            List<Widget> widgets = new ArrayList<>();
            widgets.add(Widgets.createRecipeBase(bounds));
            int x = bounds.x + 6;
            int y = bounds.y + 6;
            List<EntryIngredient> inputs = display.getInputEntries();
            widgets.add(Widgets.createSlot(new Point(x, y + 18)).entries(inputs.get(0)).markInput());
            widgets.add(Widgets.createSlot(new Point(x + 18, y + 18)).entries(inputs.get(1)).markInput());
            widgets.add(Widgets.createArrow(new Point(x + 38, y + 18)));
            ViewerRecipes.SievePage page = display.page();
            for (int i = 0; i < page.drops().size(); i++) {
                SieveRoll drop = page.drops().get(i);
                Point at = new Point(x + 66 + (i % ViewerRecipes.SIEVE_COLUMNS) * 18, y + (i / ViewerRecipes.SIEVE_COLUMNS) * 18);
                Component amount = ViewerRecipes.amountLine(drop.amount());
                widgets.add(Widgets.createSlot(at).entries(display.getOutputEntries().get(i).map(stack -> stack.copy().tooltip(amount)))
                        .markOutput());
            }
            if (page.water() > 0) {
                widgets.add(Widgets.createLabel(new Point(x, y + 40), Component.translatable("jei.aeroflux.sieve.water", page.water()))
                        .leftAligned().noShadow().color(0xFF3F76E4));
            }
            return widgets;
        }
    }
}
