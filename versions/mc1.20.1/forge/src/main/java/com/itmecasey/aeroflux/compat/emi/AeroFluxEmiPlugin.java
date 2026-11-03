package com.itmecasey.aeroflux.compat.emi;

import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.compat.ViewerRecipes;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import com.itmecasey.aeroflux.registry.ModBlocks;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * EMI support, the same pages as the JEI and REI plugins: a category for each recipe machine with
 * every tier of it as a workstation, listing its own recipes and the ones imported from other
 * mods, a category for the Flux Sifter's sieve drops, the Flux Furnace as a smelting workstation,
 * and info pages for the Enchanter and Disenchanter. The lists come from {@link ViewerRecipes}.
 * EMI finds this class by its annotation and only when EMI is installed.
 */
@EmiEntrypoint
public class AeroFluxEmiPlugin implements EmiPlugin {
    /** The category of each recipe machine, made once: EMI compares categories by identity. */
    static final Map<MachineType, EmiRecipeCategory> CATEGORIES = new EnumMap<>(MachineType.class);
    static final EmiRecipeCategory SIFTER = new MachineCategory(MachineType.SIFTER);

    static {
        for (MachineType machine : MachineType.values()) {
            if (machine.hasOwnRecipes()) {
                CATEGORIES.put(machine, new MachineCategory(machine));
            }
        }
    }

    @Override
    public void register(EmiRegistry registry) {
        CATEGORIES.forEach((machine, category) -> {
            registry.addCategory(category);
            addWorkstations(registry, category, machine);
        });
        registry.addCategory(SIFTER);
        addWorkstations(registry, SIFTER, MachineType.SIFTER);
        addWorkstations(registry, VanillaEmiRecipeCategories.SMELTING, MachineType.FURNACE);

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            CATEGORIES.forEach((machine, category) -> {
                for (FluxRecipe recipe : ViewerRecipes.machine(minecraft.level, machine)) {
                    registry.addRecipe(new MachineEmiRecipe(category, recipe));
                }
            });
            for (ViewerRecipes.SievePage page : ViewerRecipes.sifter()) {
                registry.addRecipe(new SifterEmiRecipe(SIFTER, page));
            }
        }

        for (MachineType machine : List.of(MachineType.ENCHANTER, MachineType.DISENCHANTER)) {
            List<EmiIngredient> blocks = List.of(EmiIngredient.of(tiers(machine)));
            registry.addRecipe(new EmiInfoRecipe(blocks, List.of(Component.translatable("jei.aeroflux.info." + machine.id())),
                    Ids.mod("info/" + machine.id())));
        }
    }

    private static void addWorkstations(EmiRegistry registry, EmiRecipeCategory category, MachineType machine) {
        for (EmiStack block : tiers(machine)) {
            registry.addWorkstation(category, block);
        }
    }

    /** Every tier of one machine, copper first. */
    private static List<EmiStack> tiers(MachineType machine) {
        return Arrays.stream(Tier.values()).map(tier -> EmiStack.of(ModBlocks.machine(machine, tier).get())).toList();
    }

    /** A machine's category, named after the machine and shown with its copper block. */
    private static final class MachineCategory extends EmiRecipeCategory {
        private final MachineType machine;

        MachineCategory(MachineType machine) {
            super(Ids.mod(machine.id()), EmiStack.of(ModBlocks.machine(machine, Tier.COPPER).get()));
            this.machine = machine;
        }

        @Override
        public Component getName() {
            return ModBlocks.machine(machine, Tier.COPPER).get().getName();
        }
    }
}
