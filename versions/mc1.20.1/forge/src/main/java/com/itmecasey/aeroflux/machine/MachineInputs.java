package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import com.itmecasey.aeroflux.registry.ModRecipes;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
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
            case FURNACE -> smelts(level, stack);
            case ENCHANTER -> switch (slot) {
                case 0 -> !stack.is(Items.ENCHANTED_BOOK) && !stack.is(Items.BOOK) && (stack.isEnchantable() || stack.isEnchanted());
                case 1 -> stack.is(Items.ENCHANTED_BOOK);
                default -> stack.is(Items.LAPIS_LAZULI);
            };
            case DISENCHANTER -> slot == 0
                    ? stack.isEnchanted() && !stack.is(Items.ENCHANTED_BOOK)
                    : stack.is(Items.BOOK);
            // The grid and the filter take anything; the Crafter keeps pipes to its pattern on its own.
            case CRAFTER, MAGNET -> true;
            case BLENDER -> stack.is(Items.BUCKET);
            case SKY_CONTROLLER -> false;
            // Imported sieve recipes are only known to the server, so any block may go in.
            case SIFTER -> stack.getItem() instanceof BlockItem || usedByARecipe(machine, level, stack);
            case BREEDER -> isBreedingFood(stack);
            case REPAIRER -> slot == 0 ? stack.isDamageableItem() : stack.is(Items.EXPERIENCE_BOTTLE);
            case SPAWNER -> slot == 0 ? stack.getItem() instanceof SpawnEggItem : stack.isDamageableItem();
            default -> usedByARecipe(machine, level, stack);
        };
    }

    /**
     * Food some animal breeds on that the shared tag does not list: seeds for chickens, wheat for
     * cows and sheep, carrots for pigs, and so on. A slot that takes only food keeps the breeder
     * from swallowing a stack meant for something else.
     */
    private static boolean isBreedingFood(ItemStack stack) {
        return stack.is(Items.WHEAT) || stack.is(Items.CARROT) || stack.is(Items.POTATO) || stack.is(Items.BEETROOT)
                || stack.is(Items.WHEAT_SEEDS) || stack.is(Items.MELON_SEEDS) || stack.is(Items.PUMPKIN_SEEDS)
                || stack.is(Items.BEETROOT_SEEDS) || stack.is(Items.TORCHFLOWER_SEEDS) || stack.is(Items.PITCHER_POD)
                || stack.is(Items.GOLDEN_CARROT) || stack.is(Items.GOLDEN_APPLE) || stack.is(Items.HAY_BLOCK)
                || stack.is(Items.DANDELION) || stack.is(Items.SEAGRASS) || stack.is(Items.KELP)
                || stack.is(Items.SWEET_BERRIES) || stack.is(Items.GLOW_BERRIES) || stack.is(Items.CACTUS)
                || stack.is(Items.SLIME_BALL) || stack.is(Items.TROPICAL_FISH_BUCKET) || stack.is(Items.COD)
                || stack.is(Items.SALMON) || stack.is(Items.BAMBOO) || stack.is(Items.WARPED_FUNGUS)
                || stack.is(Items.CRIMSON_FUNGUS) || stack.is(Items.SPIDER_EYE) || stack.is(Items.TORCHFLOWER);
    }

    private static boolean smelts(Level level, ItemStack stack) {
        return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SimpleContainer(stack), level).isPresent();
    }

    private static boolean usedByARecipe(MachineType machine, Level level, ItemStack stack) {
        for (FluxRecipe recipe : MachineRecipes.of(level, machine)) {
            if (recipe.usesItem(stack)) {
                return true;
            }
        }
        return false;
    }
}
