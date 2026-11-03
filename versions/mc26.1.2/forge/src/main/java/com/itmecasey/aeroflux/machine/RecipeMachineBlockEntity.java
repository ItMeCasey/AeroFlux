package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.machine.ExtraTank;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.machine.MachineUpgrades;
import com.itmecasey.aeroflux.core.machine.OutputChance;
import com.itmecasey.aeroflux.core.machine.PlantSoilRules.Verdict;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import com.itmecasey.aeroflux.machine.recipe.MachineInput;
import com.itmecasey.aeroflux.registry.ModRecipes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
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
        if (!(level instanceof ServerLevel server)) {
            return null;
        }
        MachineInput input = new MachineInput(recipeInputs());
        if (input.stacks().stream().allMatch(ItemStack::isEmpty)) {
            lastRecipe = null;
            return null;
        }
        Verdict soil = soil();
        if (!soil.grows()) {
            // The seed has no soil it likes in the soil slot.
            return null;
        }
        RecipeHolder<FluxRecipe> recipe = lastRecipe;
        if (recipe == null || !recipe.value().matches(input, server)) {
            Optional<RecipeHolder<FluxRecipe>> found =
                    server.recipeAccess().getRecipeFor(ModRecipes.type(machine()).get(), input, server);
            // The machine's own recipes win; recipes imported from other mods fill the gaps.
            recipe = found.orElseGet(() -> RecipeImports.of(server, machine()).stream()
                    .filter(imported -> imported.value().matches(input, server)).findFirst().orElse(null));
            lastRecipe = recipe;
        }
        if (recipe == null && machine() == MachineType.PLANT_GROWER && PlantGrowth.grows(stackIn(0))) {
            // No recipe names this plant: grow it the way it grows in the world.
            return new GrowJob(stackIn(0).getItem(), soil);
        }
        // A recipe that needs water (the Purifier's) waits until the tank has enough.
        if (recipe == null || !hasWater(recipe.value().water())) {
            return null;
        }
        return new RecipeJob(recipe, soil);
    }

    /** A Plant Grower growing a plant no recipe names; see {@link PlantGrowth}. */
    private final class GrowJob implements Job {
        private final Item plant;
        private final Verdict soil;

        GrowJob(Item plant, Verdict soil) {
            this.plant = plant;
            this.soil = soil;
        }

        @Override
        public Object key() {
            return List.of(plant, soil);
        }

        @Override
        public int baseTicks() {
            return soiledTicks(PlantGrowth.ticks(stackIn(0)), soil);
        }

        /** What a harvest gives is only known once it is rolled, so a free spot for one item is enough to go on. */
        @Override
        public boolean outputFits() {
            return canFitOutputs(List.of(new ItemStack(plant)));
        }

        @Override
        public void complete() {
            if (!(level instanceof ServerLevel server)) {
                return;
            }
            ItemStack seed = stackIn(0).copyWithCount(1);
            consumeInput(0, 1);
            RandomSource rolls = seededRolls != null ? seededRolls : server.getRandom();
            List<ItemStack> products = new ArrayList<>(PlantGrowth.harvest(server, worldPosition, seed, silkTouch(), rolls));
            addBonusSeed(products, seed, soil, rolls);
            putOutputs(products);
        }
    }

    private int waterTank() {
        return extraTankIndex(ExtraTank.WATER);
    }

    /** Whether the water tank holds enough for a run (always true for a run that needs none). */
    private boolean hasWater(int needed) {
        if (needed <= 0) {
            return true;
        }
        int tank = waterTank();
        return tank >= 0 && extraAmount(tank) >= needed;
    }

    @Override
    protected boolean isInputValid(int slot, ItemStack stack) {
        return level != null && MachineInputs.accepts(machine(), level, slot, stack);
    }

    /** Whether a Silk Touch Upgrade makes this machine use its recipes' silk products. */
    private boolean silkTouch() {
        return machine() == MachineType.PLANT_GROWER && upgrades(MachineUpgrades.Special.SILK_TOUCH) > 0;
    }

    /**
     * How many more of the main product the upgrades add to one run: Fortune in the Grinder,
     * Purity in the Purifier. Other machines take neither, so they get none.
     */
    private int bonusItems(RandomSource rolls) {
        if (machine() == MachineType.GRINDER) {
            return MachineUpgrades.bonusItems(upgrades(MachineUpgrades.Special.FORTUNE), MachineUpgrades.FORTUNE_CHANCE, rolls::nextDouble);
        }
        if (machine() == MachineType.PURIFIER) {
            return MachineUpgrades.bonusItems(upgrades(MachineUpgrades.Special.PURITY), MachineUpgrades.PURITY_CHANCE, rolls::nextDouble);
        }
        return 0;
    }

    /** One run of a recipe. */
    private final class RecipeJob implements Job {
        private final RecipeHolder<FluxRecipe> holder;
        private final Verdict soil;

        RecipeJob(RecipeHolder<FluxRecipe> holder, Verdict soil) {
            this.holder = holder;
            this.soil = soil;
        }

        @Override
        public Object key() {
            return List.of(holder.id(), soil);
        }

        @Override
        public int baseTicks() {
            return soiledTicks(holder.value().time(), soil);
        }

        /**
         * Only the products a run always makes must have room. Chance byproducts (seeds, sawdust)
         * take whatever space is left, so a slot full of them never stops the machine while its
         * main product still fits.
         */
        @Override
        public boolean outputFits() {
            List<ItemStack> certain = new ArrayList<>();
            for (FluxRecipe.Output output : holder.value().outputs(silkTouch())) {
                if (output.chance() >= 1.0) {
                    certain.add(output.stack());
                }
            }
            return canFitOutputs(certain);
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
            ItemStack seed = stackIn(0).copyWithCount(1);
            int[] slots = recipe.assign(new MachineInput(recipeInputs()));
            if (slots == null || level == null) {
                return;
            }
            for (int i = 0; i < slots.length; i++) {
                consumeInput(slots[i], recipe.inputs().get(i).count());
            }
            if (recipe.water() > 0) {
                drainExtra(waterTank(), recipe.water(), false);
            }
            RandomSource rolls = seededRolls != null ? seededRolls : level.getRandom();
            List<ItemStack> products = new ArrayList<>();
            for (FluxRecipe.Output output : recipe.outputs(silkTouch())) {
                if (OutputChance.drops(output.chance(), rolls.nextDouble())) {
                    products.add(output.stack().copy());
                }
            }
            // The bonus goes on the main product, the first one a run always makes. Like a
            // byproduct, it is lost if there is no room left for it.
            int bonus = bonusItems(rolls);
            if (bonus > 0 && !recipe.outputs().isEmpty() && recipe.outputs().getFirst().chance() >= 1.0F) {
                products.add(recipe.outputs().getFirst().stack().copyWithCount(bonus));
            }
            if (machine() == MachineType.PLANT_GROWER) {
                addBonusSeed(products, seed, soil, rolls);
            }
            putOutputs(products);
        }
    }

    /**
     * What the recipes see of the inputs. The Plant Grower's soil slot is no ingredient, so its
     * recipes get the seed slot alone; every other machine shows all its input slots.
     */
    private List<ItemStack> recipeInputs() {
        return machine() == MachineType.PLANT_GROWER ? List.of(stackIn(0)) : inputStacks();
    }

    /** How the soil in the Plant Grower suits the seed; any other machine has no soil and always passes. */
    private Verdict soil() {
        return machine() == MachineType.PLANT_GROWER ? PlantSoils.judge(stackIn(0), stackIn(1)) : Verdict.PLAIN;
    }

    /** A soil's speed bonus shortens a run, by at most nine in ten and never to nothing. */
    private static int soiledTicks(int ticks, Verdict soil) {
        return Math.max(1, (int) Math.round(ticks / soil.speed()));
    }

    /** Rolls the soil's chance of one more seed and adds it to the harvest. */
    private static void addBonusSeed(List<ItemStack> products, ItemStack seed, Verdict soil, RandomSource rolls) {
        if (soil.extraSeedChance() > 0 && rolls.nextDouble() < soil.extraSeedChance()) {
            products.add(seed.copyWithCount(1));
        }
    }

    /** The id of the recipe being worked on, for tests and tooltips; {@code null} when idle. */
    @Nullable
    public Identifier activeRecipe() {
        return lastRecipe == null ? null : lastRecipe.id().identifier();
    }
}
