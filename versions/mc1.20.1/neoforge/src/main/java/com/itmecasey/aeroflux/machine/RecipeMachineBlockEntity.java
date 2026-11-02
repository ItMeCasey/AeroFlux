package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.machine.OutputChance;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import com.itmecasey.aeroflux.machine.recipe.MachineInput;
import com.itmecasey.aeroflux.registry.ModRecipes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The machines that run on {@link FluxRecipe}s of their own type: Grinder, Sawmill, Alloy
 * Smelter, Infuser and Plant Grower. They differ only in slots, flux and the recipes in their
 * datapack folder, all of which come from the machine type, so one class serves them all.
 */
public class RecipeMachineBlockEntity extends FluxMachineBlockEntity {
    @Nullable
    private FluxRecipe lastRecipe;

    public RecipeMachineBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Nullable
    @Override
    protected Job findJob() {
        if (level == null) {
            return null;
        }
        MachineInput input = new MachineInput(inputStacks());
        if (input.stacks().stream().allMatch(ItemStack::isEmpty)) {
            lastRecipe = null;
            return null;
        }
        FluxRecipe recipe = lastRecipe;
        if (recipe == null || !recipe.matches(input, level)) {
            Optional<FluxRecipe> found =
                    level.getRecipeManager().getRecipeFor(ModRecipes.type(machine()).get(), input, level);
            recipe = found.orElse(null);
            lastRecipe = recipe;
        }
        return recipe == null ? null : new RecipeJob(recipe);
    }

    @Override
    protected boolean isInputValid(int slot, ItemStack stack) {
        return level != null && MachineInputs.accepts(machine(), level, slot, stack);
    }

    /** One run of a recipe. */
    private final class RecipeJob implements Job {
        private final FluxRecipe recipe;

        RecipeJob(FluxRecipe recipe) {
            this.recipe = recipe;
        }

        @Override
        public Object key() {
            return recipe.getId();
        }

        @Override
        public int baseTicks() {
            return recipe.time();
        }

        @Override
        public boolean outputFits() {
            // Judged on the full set of products, so a lucky roll can never find the outputs full.
            return canFitOutputs(recipe.possibleProducts());
        }

        @Override
        public int extraFlux() {
            return recipe.flux();
        }

        @Nullable
        @Override
        public FluxType requiredFlux() {
            return recipe.fluxType();
        }

        @Override
        public void complete() {
            int[] slots = recipe.assign(new MachineInput(inputStacks()));
            if (slots == null || level == null) {
                return;
            }
            for (int i = 0; i < slots.length; i++) {
                consumeInput(slots[i], recipe.inputs().get(i).count());
            }
            List<ItemStack> products = new ArrayList<>();
            for (FluxRecipe.Output output : recipe.outputs()) {
                if (OutputChance.drops(output.chance(), level.random.nextDouble())) {
                    products.add(output.stack().copy());
                }
            }
            putOutputs(products);
        }
    }

    /** The id of the recipe being worked on, for tests and tooltips; {@code null} when idle. */
    @Nullable
    public ResourceLocation activeRecipe() {
        return lastRecipe == null ? null : lastRecipe.getId();
    }
}
