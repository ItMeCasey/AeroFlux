package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.machine.ExtraTank;
import com.itmecasey.aeroflux.core.machine.OutputChance;
import com.itmecasey.aeroflux.core.machine.SieveMesh;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import com.itmecasey.aeroflux.registry.ModRecipes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The Flux Sifter, a powered sieve whose tier is its mesh (see {@link SieveMesh}). Like the sieves
 * of Ex Deorum and Ex Nihilo, one block of input rolls every sieve recipe for that block at once,
 * each product on its own chance. A recipe only counts when the sifter's mesh is good enough for
 * it. Its recipes are its own {@code aeroflux:sifting} ones plus those of Ex Deorum and Ex Nihilo
 * Sequentia when they are installed (see {@link SieveImports}). Waterlogged recipes take water
 * from its tank. The fluxium sifter runs every mesh, twice as fast and with better odds.
 */
public class SifterBlockEntity extends FluxMachineBlockEntity {
    /** One block takes five seconds at copper; tier speed and Speed upgrades shorten it. */
    public static final int SIFT_TICKS = 100;

    public SifterBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    protected boolean isInputValid(int slot, ItemStack stack) {
        return level != null && MachineInputs.accepts(machine(), level, slot, stack);
    }

    /** Every sifting recipe for this block that this sifter's mesh runs. */
    private List<FluxRecipe> recipesFor(ServerLevel server, ItemStack input) {
        List<FluxRecipe> found = new ArrayList<>();
        List<RecipeHolder<FluxRecipe>> all = new ArrayList<>(
                ModRecipes.all(server, ModRecipes.type(MachineType.SIFTER).get()));
        all.addAll(SieveImports.of(server));
        for (RecipeHolder<FluxRecipe> holder : all) {
            FluxRecipe recipe = holder.value();
            if (!recipe.inputs().isEmpty() && recipe.inputs().getFirst().ingredient().test(input)
                    && SieveMesh.runs(tier(), recipe.mesh())) {
                found.add(recipe);
            }
        }
        return found;
    }

    @Override
    protected @Nullable Job findJob() {
        ItemStack input = stackIn(0);
        if (input.isEmpty() || !(level instanceof ServerLevel server)) {
            return null;
        }
        List<FluxRecipe> recipes = recipesFor(server, input);
        int water = 0;
        for (FluxRecipe recipe : recipes) {
            water = Math.max(water, recipe.water());
        }
        int waterTank = extraTankIndex(ExtraTank.WATER);
        if (recipes.isEmpty() || (water > 0 && extraAmount(waterTank) < water)) {
            return null;
        }
        int neededWater = water;
        return new Job() {
            @Override
            public Object key() {
                return input.getItem();
            }

            @Override
            public int baseTicks() {
                return tier() == Tier.FLUXIUM ? SIFT_TICKS / 2 : SIFT_TICKS;
            }

            /** Sieve products are all chance rolls, so one free slot is enough to go on. */
            @Override
            public boolean outputFits() {
                return canFitOutputs(List.of(recipes.getFirst().outputs().getFirst().stack().copyWithCount(1)));
            }

            @Override
            public void complete() {
                consumeInput(0, 1);
                if (neededWater > 0) {
                    drainExtra(waterTank, neededWater, false);
                }
                RandomSource rolls = level.getRandom();
                List<ItemStack> products = new ArrayList<>();
                for (FluxRecipe recipe : recipes) {
                    for (FluxRecipe.Output output : recipe.outputs()) {
                        if (OutputChance.drops((float) SieveMesh.chance(tier(), output.chance()), rolls.nextDouble())) {
                            products.add(output.stack());
                        }
                    }
                }
                putOutputs(products);
            }
        };
    }
}
