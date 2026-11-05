package com.itmecasey.aeroflux.compat.bei;

import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.compat.ViewerRecipes;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import com.itmecasey.aeroflux.machine.SieveRoll;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.bei.api.BeiPlugin;
import com.itmecasey.bei.api.CardPainter;
import com.itmecasey.bei.api.CustomRecipe;
import com.itmecasey.bei.api.IBeiPlugin;
import com.itmecasey.bei.api.ICategoryRegistration;
import com.itmecasey.bei.api.IRecipeRegistration;
import com.itmecasey.bei.api.RecipeCategory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Barely Enough Items support: a card category for each recipe machine and one for the Sifter,
 * each with the machine's own recipes and the ones imported from other mods (the lists come
 * from {@link ViewerRecipes}, as for JEI, REI and EMI). BEI finds this class by its annotation,
 * so it is only ever loaded when BEI is installed.
 */
@BeiPlugin
public class AeroFluxBeiPlugin implements IBeiPlugin {
    private static final int SLOT = 18;
    private static final int PAD = 8;
    private static final int ARROW = 24;
    private static final int HEIGHT = 40;
    private static final int SIEVE_X = 56;

    private final Map<MachineType, RecipeCategory> machines = new EnumMap<>(MachineType.class);
    private RecipeCategory sifter;

    /** What a machine card draws besides the slots: the cost line, the mod an import came from, the output chances. */
    private record Cost(int time, int flux, @Nullable Component imported, List<Float> chances) {
    }

    @Override
    public void registerCategories(ICategoryRegistration registration) {
        for (MachineType machine : MachineType.values()) {
            if (machine.hasOwnRecipes()) {
                RecipeCategory category = machineCategory(machine);
                machines.put(machine, category);
                registration.register(category);
            }
        }
        sifter = sifterCategory();
        registration.register(sifter);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        machines.forEach((machine, category) -> {
            for (RecipeHolder<FluxRecipe> holder : ViewerRecipes.machine(level, machine)) {
                registration.add(category, machineRecipe(holder));
            }
        });
        for (ViewerRecipes.SievePage page : ViewerRecipes.sifter(level)) {
            registration.add(sifter, sifterRecipe(page));
        }
    }

    // --- Machines -------------------------------------------------------------------------

    private static RecipeCategory machineCategory(MachineType machine) {
        int arrowX = PAD + machine.inputSlots() * SLOT + 4;
        int outputX = arrowX + ARROW + 6;
        RecipeCategory.Builder builder = RecipeCategory.builder(Ids.mod(machine.id()))
                .title(ModBlocks.machine(machine, Tier.COPPER).get().getName())
                .station(new ItemStack(ModBlocks.machine(machine, Tier.COPPER).get()))
                .size(outputX + machine.outputSlots() * SLOT + PAD, HEIGHT);
        for (int i = 0; i < machine.inputSlots(); i++) {
            builder.input(PAD + i * SLOT, 5);
        }
        for (int i = 0; i < machine.outputSlots(); i++) {
            builder.output(outputX + i * SLOT, 5);
        }
        return builder.painter(new MachinePainter(arrowX, outputX)).build();
    }

    private static CustomRecipe machineRecipe(RecipeHolder<FluxRecipe> holder) {
        FluxRecipe recipe = holder.value();
        CustomRecipe.Builder builder = CustomRecipe.builder().id(holder.id().toString());
        for (FluxRecipe.Input input : recipe.inputs()) {
            builder.input(Arrays.stream(input.ingredient().getItems()).map(stack -> stack.copyWithCount(input.count())).toList());
        }
        List<Float> chances = new ArrayList<>();
        for (FluxRecipe.Output output : recipe.outputs()) {
            builder.output(output.stack());
            chances.add(output.chance());
        }
        return builder.data(new Cost(recipe.time(), recipe.flux(), ViewerRecipes.importedLine(holder.id()), chances)).build();
    }

    private record MachinePainter(int arrowX, int outputX) implements CardPainter {
        @Override
        public void paint(GuiGraphics g, CustomRecipe recipe, int mouseX, int mouseY) {
            g.fill(arrowX, 12, arrowX + ARROW - 6, 14, 0xFF8B8B8B);
            g.fill(arrowX + ARROW - 6, 9, arrowX + ARROW - 4, 17, 0xFF8B8B8B);
            if (recipe.data() instanceof Cost cost) {
                Component line = cost.flux() > 0 ? Component.translatable("jei.aeroflux.time_and_flux", cost.time(), cost.flux())
                        : Component.translatable("jei.aeroflux.time", cost.time());
                g.drawString(Minecraft.getInstance().font, line, PAD, 28, 0xFFA0A0A0, false);
            }
        }

        @Override
        public @Nullable List<Component> tooltip(CustomRecipe recipe, int mouseX, int mouseY) {
            if (!(recipe.data() instanceof Cost cost)) {
                return null;
            }
            if (cost.imported() != null && mouseY >= 26) {
                return List.of(cost.imported());
            }
            for (int i = 0; i < cost.chances().size(); i++) {
                int x = outputX + i * SLOT;
                if (cost.chances().get(i) < 1.0F && mouseX >= x && mouseX < x + SLOT && mouseY >= 5 && mouseY < 5 + SLOT) {
                    return List.of(Component.translatable("jei.aeroflux.chance", Math.round(cost.chances().get(i) * 100)));
                }
            }
            return null;
        }
    }

    // --- Sifter ---------------------------------------------------------------------------

    private static RecipeCategory sifterCategory() {
        RecipeCategory.Builder builder = RecipeCategory.builder(Ids.mod("sifter"))
                .title(ModBlocks.machine(MachineType.SIFTER, Tier.COPPER).get().getName())
                .station(new ItemStack(ModBlocks.machine(MachineType.SIFTER, Tier.COPPER).get()))
                .size(SIEVE_X + ViewerRecipes.SIEVE_COLUMNS * SLOT + PAD, 3 * SLOT + 2)
                .input(PAD, SLOT)
                .input(PAD + SLOT, SLOT);
        for (int i = 0; i < ViewerRecipes.SIEVE_PAGE; i++) {
            builder.output(SIEVE_X + (i % ViewerRecipes.SIEVE_COLUMNS) * SLOT, (i / ViewerRecipes.SIEVE_COLUMNS) * SLOT);
        }
        return builder.painter(new SifterPainter()).build();
    }

    private static CustomRecipe sifterRecipe(ViewerRecipes.SievePage page) {
        CustomRecipe.Builder builder = CustomRecipe.builder().id(page.id().toString())
                .input(Arrays.asList(page.material().getItems()))
                .input(Arrays.asList(page.mesh().getItems()));
        for (SieveRoll drop : page.drops()) {
            builder.output(drop.product());
        }
        return builder.data(page).build();
    }

    private static final class SifterPainter implements CardPainter {
        @Override
        public void paint(GuiGraphics g, CustomRecipe recipe, int mouseX, int mouseY) {
            g.fill(PAD + 2 * SLOT + 2, SLOT + 8, SIEVE_X - 6, SLOT + 10, 0xFF8B8B8B);
        }

        @Override
        public @Nullable List<Component> tooltip(CustomRecipe recipe, int mouseX, int mouseY) {
            if (!(recipe.data() instanceof ViewerRecipes.SievePage page)) {
                return null;
            }
            int column = (mouseX - SIEVE_X) / SLOT;
            int row = mouseY / SLOT;
            int index = row * ViewerRecipes.SIEVE_COLUMNS + column;
            if (mouseX >= SIEVE_X && column < ViewerRecipes.SIEVE_COLUMNS && mouseY >= 0 && row < 3 && index < page.drops().size()) {
                return List.of(ViewerRecipes.amountLine(page.drops().get(index).amount()));
            }
            return null;
        }
    }
}
