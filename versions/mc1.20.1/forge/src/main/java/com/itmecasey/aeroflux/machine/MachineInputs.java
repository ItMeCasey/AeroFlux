package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import com.itmecasey.aeroflux.registry.ModRecipes;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * Which items each machine's input slots accept. It needs only the level (for recipes), not a
 * block entity, so the menu can ask the same question on the client as the machine does on the
 * server and the two always agree.
 */
public final class MachineInputs {
    private MachineInputs() {
    }

    /** Whether a stack may go into an input slot of a machine. */
    public static boolean accepts(MachineType machine, Level level, int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return switch (machine) {
            case FURNACE -> level.getRecipeManager()
                    .getRecipeFor(RecipeType.SMELTING, new SimpleContainer(stack), level).isPresent();
            case ENCHANTER -> switch (slot) {
                case 0 -> !stack.is(Items.ENCHANTED_BOOK) && !stack.is(Items.BOOK) && (stack.isEnchantable() || stack.isEnchanted());
                case 1 -> stack.is(Items.ENCHANTED_BOOK);
                default -> stack.is(Items.LAPIS_LAZULI);
            };
            case DISENCHANTER -> slot == 0
                    ? stack.isEnchanted() && !stack.is(Items.ENCHANTED_BOOK)
                    : stack.is(Items.BOOK);
            default -> usedByARecipe(machine, level, stack);
        };
    }

    private static boolean usedByARecipe(MachineType machine, Level level, ItemStack stack) {
        for (FluxRecipe recipe : level.getRecipeManager().getAllRecipesFor(ModRecipes.type(machine).get())) {
            if (recipe.usesItem(stack)) {
                return true;
            }
        }
        return false;
    }
}
