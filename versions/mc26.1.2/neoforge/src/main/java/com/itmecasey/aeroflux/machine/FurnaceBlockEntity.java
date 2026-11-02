package com.itmecasey.aeroflux.machine;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The Flux Furnace: smelts with the vanilla smelting recipes, paid for with Flux instead of fuel.
 * A recipe's cooking time is its base time, so datapacks and other mods' smelting recipes work
 * unchanged; the tier's speed does the rest.
 */
public class FurnaceBlockEntity extends FluxMachineBlockEntity {
    /** Remembers the last recipe found, so the common case (the same stack again) skips the search. */
    private final RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> recipeCheck =
            RecipeManager.createCheck(RecipeType.SMELTING);

    public FurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    protected @Nullable Job findJob() {
        ItemStack input = stackIn(0);
        if (input.isEmpty() || !(level instanceof ServerLevel server)) {
            return null;
        }
        Optional<RecipeHolder<SmeltingRecipe>> found = recipeCheck.getRecipeFor(new SingleRecipeInput(input), server);
        if (found.isEmpty()) {
            return null;
        }
        SmeltingRecipe recipe = found.get().value();
        ItemStack result = recipe.assemble(new SingleRecipeInput(input));
        return result.isEmpty() ? null : new SmeltJob(found.get().id(), recipe.cookingTime(), result);
    }

    @Override
    protected boolean isInputValid(int slot, ItemStack stack) {
        return level != null && MachineInputs.accepts(machine(), level, slot, stack);
    }

    /** One item being smelted. */
    private final class SmeltJob implements Job {
        private final ResourceKey<Recipe<?>> recipe;
        private final int cookingTime;
        private final ItemStack result;

        SmeltJob(ResourceKey<Recipe<?>> recipe, int cookingTime, ItemStack result) {
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
