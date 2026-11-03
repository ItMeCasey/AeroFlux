package com.itmecasey.aeroflux.compat.jei;

import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.compat.ViewerRecipes;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.registry.ModBlocks;
import java.util.Arrays;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * JEI support: a page type for each recipe machine with every tier of that machine as its
 * station, the Flux Furnace on JEI's own smelting pages, and an info page for the Enchanter and
 * Disenchanter, which follow rules rather than recipes. JEI finds this class by its annotation and
 * only when JEI is installed.
 */
@JeiPlugin
public class AeroFluxJeiPlugin implements IModPlugin {
    private static final Identifier UID = Ids.mod("jei");

    @Override
    public Identifier getPluginUid() {
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
        registration.addRecipes(SifterRecipeCategory.TYPE, ViewerRecipes.sifter(minecraft.level));
        for (MachineType machine : List.of(MachineType.ENCHANTER, MachineType.DISENCHANTER)) {
            registration.addItemStackInfo(Arrays.stream(tiers(machine)).map(ItemStack::new).toList(),
                    Component.translatable("jei.aeroflux.info." + machine.id()));
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (MachineType machine : MachineType.values()) {
            if (machine.hasOwnRecipes()) {
                registration.addCraftingStation(MachineRecipeCategory.typeFor(machine), tiers(machine));
            }
        }
        registration.addCraftingStation(SifterRecipeCategory.TYPE, tiers(MachineType.SIFTER));
        registration.addCraftingStation(RecipeTypes.SMELTING, tiers(MachineType.FURNACE));
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
