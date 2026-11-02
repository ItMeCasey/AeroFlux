package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * Which items each machine's input slots accept. It needs only the level (for recipes), not a
 * block entity, so the menu can ask the same question on the client as the machine does on the
 * server and the two always agree. The client has no recipe manager of its own in 26.1: it asks
 * the smelting property set the game syncs for the furnace, and the machine recipes that
 * {@link MachineRecipes} receives for the rest.
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
            case FURNACE -> smelts(level, stack);
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

    private static boolean smelts(Level level, ItemStack stack) {
        if (level instanceof ServerLevel server) {
            return server.recipeAccess().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), server).isPresent();
        }
        return level.recipeAccess().propertySet(RecipePropertySet.FURNACE_INPUT).test(stack);
    }

    private static boolean usedByARecipe(MachineType machine, Level level, ItemStack stack) {
        for (RecipeHolder<FluxRecipe> holder : MachineRecipes.of(level, machine)) {
            if (holder.value().usesItem(stack)) {
                return true;
            }
        }
        return false;
    }
}
