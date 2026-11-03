package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.machine.SieveMesh;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import com.itmecasey.aeroflux.registry.ModRecipes;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
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
            case SIFTER -> sifts(level, slot, stack);
            // Any crop or sapling grows, recipe or not (see PlantGrowth); the second slot holds its soil.
            case PLANT_GROWER -> slot == 0 ? PlantGrowth.grows(stack) || usedByARecipe(machine, level, stack) : isSoil(level, stack);
            case BREEDER -> isBreedingFood(stack);
            case REPAIRER -> slot == 0 ? stack.isDamageableItem() : stack.is(Items.EXPERIENCE_BOTTLE);
            case SPAWNER -> slot == 0 ? stack.getItem() instanceof SpawnEggItem : stack.isDamageableItem();
            default -> usedByARecipe(machine, level, stack);
        };
    }

    /**
     * The Sifter's two slots: the block to sift and the mesh. The server knows every imported sieve
     * recipe and answers from them. The client has none, so it goes by the name: both sieve mods
     * call their meshes {@code something_mesh}, and anything else is a block to sift. The server's
     * answer is the one that counts; the client only keeps its picture close to it.
     */
    private static boolean sifts(Level level, int slot, ItemStack stack) {
        if (level instanceof ServerLevel server) {
            return slot == SieveMesh.MESH_SLOT ? SieveImports.isMesh(server, stack) : SieveImports.isMaterial(server, stack);
        }
        boolean looksLikeMesh = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("_mesh");
        return slot == SieveMesh.MESH_SLOT ? looksLikeMesh : !looksLikeMesh;
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

    /**
     * Whether a stack may go in the Plant Grower's soil slot. The server knows the soil rules; the
     * client has only the tags, so it lets any block through and the server's answer counts.
     */
    private static boolean isSoil(Level level, ItemStack stack) {
        return level instanceof ServerLevel ? PlantSoils.isSoil(stack) : stack.getItem() instanceof BlockItem;
    }

    private static boolean smelts(Level level, ItemStack stack) {
        return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SimpleContainer(stack), level).isPresent();
    }

    /**
     * Whether a machine has a recipe for the stack: one of its own or one imported from another mod
     * (see {@link RecipeImports}). Only the server knows the import rules, so the client also lets
     * through what it cannot judge, and the server's answer, which comes right back, is the one
     * that counts.
     */
    private static boolean usedByARecipe(MachineType machine, Level level, ItemStack stack) {
        for (FluxRecipe recipe : MachineRecipes.of(level, machine)) {
            if (recipe.usesItem(stack)) {
                return true;
            }
        }
        if (level instanceof ServerLevel server) {
            return RecipeImports.uses(server, machine, stack);
        }
        return true;
    }
}
