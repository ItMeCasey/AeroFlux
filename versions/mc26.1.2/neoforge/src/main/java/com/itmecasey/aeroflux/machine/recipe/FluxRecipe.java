package com.itmecasey.aeroflux.machine.recipe;

import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.registry.ModRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A recipe of one of the data-driven machines: some ingredients (each with a count), some
 * products (each with a chance), a base time, and optionally extra Flux and a required flux type.
 * Grinder, Sawmill, Alloy Smelter, Infuser and Plant Grower all use this one shape, each with a
 * recipe type of its own so a datapack adds to exactly one machine.
 *
 * <p>Ingredients are matched against the input slots in any order, so a recipe does not care
 * which slot an item sits in.
 */
public class FluxRecipe implements Recipe<MachineInput> {
    /** An ingredient and how many of it one job uses. */
    public record Input(Ingredient ingredient, int count) {
        public static final Codec<Input> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Ingredient.CODEC.fieldOf("ingredient").forGetter(Input::ingredient),
                Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(Input::count)
        ).apply(instance, Input::new));
    }

    /**
     * A product and the chance it drops. The product is kept as a template, the way the game keeps
     * recipe results since 26.1, so a recipe can be built before item components exist (data generation).
     */
    public record Output(ItemStackTemplate template, float chance) {
        public static final Codec<Output> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ItemStackTemplate.CODEC.fieldOf("item").forGetter(Output::template),
                Codec.floatRange(0.0F, 1.0F).optionalFieldOf("chance", 1.0F).forGetter(Output::chance)
        ).apply(instance, Output::new));

        /** A new stack of the product. */
        public ItemStack stack() {
            return template.create();
        }
    }

    private final MachineType machine;
    private final List<Input> inputs;
    private final List<Output> outputs;
    private final int time;
    private final int flux;
    private final @Nullable FluxType fluxType;
    private final String mesh;
    private final int water;

    /**
     * @param machine  which machine this recipe is for; it picks the recipe type
     * @param inputs   the ingredients
     * @param outputs  the products
     * @param time     base time in ticks, before tier speed
     * @param flux     extra Flux in mB taken when the job finishes
     * @param fluxType the flux type the job needs, or {@code null} for either
     */
    public FluxRecipe(MachineType machine, List<Input> inputs, List<Output> outputs, int time, int flux,
                      @Nullable FluxType fluxType) {
        this(machine, inputs, outputs, time, flux, fluxType, "", 0);
    }

    /**
     * @param mesh  the sieve mesh the recipe needs (Sifter only), such as "flint"; empty for any
     * @param water mB of water one run takes from the machine's water tank (Purifier and Sifter)
     */
    public FluxRecipe(MachineType machine, List<Input> inputs, List<Output> outputs, int time, int flux,
                      @Nullable FluxType fluxType, String mesh, int water) {
        this.mesh = mesh;
        this.water = water;
        this.machine = machine;
        this.inputs = List.copyOf(inputs);
        this.outputs = List.copyOf(outputs);
        this.time = time;
        this.flux = flux;
        this.fluxType = fluxType;
    }

    public MachineType machine() {
        return machine;
    }

    public List<Input> inputs() {
        return inputs;
    }

    public List<Output> outputs() {
        return outputs;
    }

    /** Base time in ticks, before tier speed. */
    public int time() {
        return time;
    }

    /** Extra Flux taken when the job finishes, in mB. */
    public int flux() {
        return flux;
    }

    public @Nullable FluxType fluxType() {
        return fluxType;
    }

    /** The sieve mesh a Sifter needs for this recipe; empty for any. */
    public String mesh() {
        return mesh;
    }

    /** mB of water one run uses; 0 for none. */
    public int water() {
        return water;
    }

    /**
     * Which input slot each ingredient is taken from, or {@code null} if the slots do not hold
     * everything the recipe needs. Each slot serves at most one ingredient.
     */
    public int @Nullable [] assign(MachineInput input) {
        int[] chosen = new int[inputs.size()];
        return assign(input, 0, chosen, new boolean[input.size()]) ? chosen : null;
    }

    private boolean assign(MachineInput input, int index, int[] chosen, boolean[] used) {
        if (index == inputs.size()) {
            // Nothing may be left over: a stack no ingredient wants means this is a different recipe.
            for (int slot = 0; slot < input.size(); slot++) {
                if (!used[slot] && !input.getItem(slot).isEmpty()) {
                    return false;
                }
            }
            return true;
        }
        Input wanted = inputs.get(index);
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (!used[slot] && stack.getCount() >= wanted.count() && wanted.ingredient().test(stack)) {
                used[slot] = true;
                chosen[index] = slot;
                if (assign(input, index + 1, chosen, used)) {
                    return true;
                }
                used[slot] = false;
            }
        }
        return false;
    }

    /** The products that always drop, plus the ones that might, at full count, for judging whether the outputs have room. */
    public List<ItemStack> possibleProducts() {
        List<ItemStack> products = new ArrayList<>();
        for (Output output : outputs) {
            products.add(output.stack());
        }
        return products;
    }

    /** Whether any ingredient of this recipe accepts the stack. */
    public boolean usesItem(ItemStack stack) {
        for (Input input : inputs) {
            if (input.ingredient().test(stack)) {
                return true;
            }
        }
        return false;
    }

    // --- Recipe ------------------------------------------------------------------------------

    @Override
    public boolean matches(MachineInput input, Level level) {
        return assign(input) != null;
    }

    @Override
    public ItemStack assemble(MachineInput input) {
        return outputs.isEmpty() ? ItemStack.EMPTY : outputs.get(0).stack();
    }

    /** Machine recipes are never placed in a grid and have no place in the crafting recipe book. */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public RecipeSerializer<? extends Recipe<MachineInput>> getSerializer() {
        return ModRecipes.serializer(machine).get();
    }

    @Override
    public RecipeType<? extends Recipe<MachineInput>> getType() {
        return ModRecipes.type(machine).get();
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    /** The game wants a category for every recipe; this one is never shown because the recipe has no display. */
    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }
}
