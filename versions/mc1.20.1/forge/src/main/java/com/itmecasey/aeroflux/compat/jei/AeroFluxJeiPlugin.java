package com.itmecasey.aeroflux.compat.jei;

import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.compat.ViewerRecipes;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import com.itmecasey.aeroflux.registry.ModBlocks;
import java.util.Arrays;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * JEI support: a page type for each recipe machine with every tier of that machine as its
 * station, listing the machine's own recipes and the ones imported from other mods, a page type
 * for the Sifter's sieve drops, the Furnace on JEI's own smelting pages, and an info page
 * for the Enchanter and Disenchanter, which follow rules rather than recipes. The lists come from
 * {@link ViewerRecipes}, which the REI and EMI plugins share. JEI finds this class by its
 * annotation and only when JEI is installed.
 */
@JeiPlugin
public class AeroFluxJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = Ids.mod("jei");

    private static IJeiRuntime runtime;

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    /** For the dev tours: opens JEI's recipe screen on the page of one machine. False before JEI has started. */
    public static boolean showRecipes(MachineType machine) {
        if (runtime == null) {
            return false;
        }
        runtime.getRecipesGui().showTypes(List.of(MachineRecipeCategory.typeFor(machine)));
        return true;
    }

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        for (MachineType machine : MachineType.values()) {
            if (machine.hasOwnRecipes()) {
                registration.addRecipeCategories(new MachineRecipeCategory(machine, registration.getJeiHelpers().getGuiHelper()));
            }
        }
        registration.addRecipeCategories(new SifterRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        for (MachineType machine : MachineType.values()) {
            if (machine.hasOwnRecipes()) {
                registration.addRecipes(MachineRecipeCategory.typeFor(machine), ViewerRecipes.machine(minecraft.level, machine));
            }
        }
        registration.addRecipes(SifterRecipeCategory.TYPE, ViewerRecipes.sifter());
        for (MachineType machine : List.of(MachineType.ENCHANTER, MachineType.DISENCHANTER)) {
            registration.addItemStackInfo(Arrays.stream(tiers(machine)).map(ItemStack::new).toList(),
                    Component.translatable("jei.aeroflux.info." + machine.id()));
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (MachineType machine : MachineType.values()) {
            if (machine.hasOwnRecipes()) {
                registration.addRecipeCatalysts(MachineRecipeCategory.typeFor(machine), tiers(machine));
            }
        }
        registration.addRecipeCatalysts(SifterRecipeCategory.TYPE, tiers(MachineType.SIFTER));
        registration.addRecipeCatalysts(RecipeTypes.SMELTING, tiers(MachineType.FURNACE));
    }

    /** Every tier of one machine, copper first. */
    private static ItemLike[] tiers(MachineType machine) {
        Tier[] tiers = Tier.values();
        ItemLike[] blocks = new ItemLike[tiers.length];
        for (int i = 0; i < tiers.length; i++) {
            blocks[i] = ModBlocks.machine(machine, tiers[i]).get();
        }
        return blocks;
    }
}
