package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.machine.OutputChance;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import com.itmecasey.aeroflux.machine.recipe.MachineInput;
import com.itmecasey.aeroflux.registry.ModRecipes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The machines that run on {@link FluxRecipe}s of their own type: Grinder, Sawmill, Alloy
 * Smelter, Infuser and Plant Grower. They differ only in slots, flux and the recipes in their
 * datapack folder, all of which come from the machine type, so one class serves them all.
 */
public class RecipeMachineBlockEntity extends FluxMachineBlockEntity {
    @Nullable
    private RecipeHolder<FluxRecipe> lastRecipe;
    /** Rolls the chance products when set; otherwise the level's random does. Never saved. */
    @Nullable
    private RandomSource seededRolls;

    public RecipeMachineBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    /**
     * Makes this machine roll its chance products (the Sawmill's sawdust, the Grinder's flint)
     * from a random of its own, started from the given seed, so a test sees the same rolls on
     * every run. It is not saved: once the machine is reloaded it uses the level's random again.
     */
    public void seedRolls(long seed) {
        seededRolls = RandomSource.create(seed);
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
        RecipeHolder<FluxRecipe> recipe = lastRecipe;
        if (recipe == null || !recipe.value().matches(input, level)) {
            Optional<RecipeHolder<FluxRecipe>> found =
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
        private final RecipeHolder<FluxRecipe> holder;

        RecipeJob(RecipeHolder<FluxRecipe> holder) {
            this.holder = holder;
        }

        @Override
        public Object key() {
            return holder.id();
        }

        @Override
        public int baseTicks() {
            return holder.value().time();
        }

        @Override
        public boolean outputFits() {
            // Judged on the full set of products, so a lucky roll can never find the outputs full.
            return canFitOutputs(holder.value().possibleProducts());
        }

        @Override
        public int extraFlux() {
            return holder.value().flux();
        }

        @Nullable
        @Override
        public FluxType requiredFlux() {
            return holder.value().fluxType();
        }

        @Override
        public void complete() {
            FluxRecipe recipe = holder.value();
            int[] slots = recipe.assign(new MachineInput(inputStacks()));
            if (slots == null || level == null) {
                return;
            }
            for (int i = 0; i < slots.length; i++) {
                consumeInput(slots[i], recipe.inputs().get(i).count());
            }
            RandomSource rolls = seededRolls != null ? seededRolls : level.random;
            List<ItemStack> products = new ArrayList<>();
            for (FluxRecipe.Output output : recipe.outputs()) {
                if (OutputChance.drops(output.chance(), rolls.nextDouble())) {
                    products.add(output.stack().copy());
                }
            }
            putOutputs(products);
        }
    }

    /** The id of the recipe being worked on, for tests and tooltips; {@code null} when idle. */
    @Nullable
    public ResourceLocation activeRecipe() {
        return lastRecipe == null ? null : lastRecipe.id();
    }
}
