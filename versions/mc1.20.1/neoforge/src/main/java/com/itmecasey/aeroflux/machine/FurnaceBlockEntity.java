package com.itmecasey.aeroflux.machine;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Flux Furnace: smelts with the vanilla smelting recipes, paid for with Flux instead of fuel.
 * A recipe's cooking time is its base time, so datapacks and other mods' smelting recipes work
 * unchanged; the tier's speed does the rest.
 */
public class FurnaceBlockEntity extends FluxMachineBlockEntity {
    /** Remembers the last recipe found, so the common case (the same stack again) skips the search. */
    private final RecipeManager.CachedCheck<Container, SmeltingRecipe> recipeCheck =
            RecipeManager.createCheck(RecipeType.SMELTING);

    public FurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Nullable
    @Override
    protected Job findJob() {
        ItemStack input = inventory().getStackInSlot(0);
        if (input.isEmpty() || level == null) {
            return null;
        }
        SimpleContainer recipeInput = new SimpleContainer(input);
        Optional<SmeltingRecipe> found = recipeCheck.getRecipeFor(recipeInput, level);
        if (found.isEmpty()) {
            return null;
        }
        ItemStack result = found.get().assemble(recipeInput, level.registryAccess());
        return result.isEmpty() ? null : new SmeltJob(found.get().getId(), found.get().getCookingTime(), result);
    }

    @Override
    protected boolean isInputValid(int slot, ItemStack stack) {
        return level != null && MachineInputs.accepts(machine(), level, slot, stack);
    }

    /** One item being smelted. */
    private final class SmeltJob implements Job {
        private final ResourceLocation recipe;
        private final int cookingTime;
        private final ItemStack result;

        SmeltJob(ResourceLocation recipe, int cookingTime, ItemStack result) {
            this.recipe = recipe;
            this.cookingTime = cookingTime;
            this.result = result;
        }

        @Override
        public Object key() {
            return recipe;
        }

        @Override
        public int baseTicks() {
            return cookingTime;
        }

        @Override
        public boolean outputFits() {
            return canFitOutputs(List.of(result));
        }

        @Override
        public void complete() {
            consumeInput(0, 1);
            putOutputs(List.of(result));
        }
    }
}
