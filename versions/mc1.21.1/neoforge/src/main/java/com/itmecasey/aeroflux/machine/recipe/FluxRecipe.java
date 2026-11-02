package com.itmecasey.aeroflux.machine.recipe;

import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.registry.ModRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

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
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(Input::ingredient),
                Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(Input::count)
        ).apply(instance, Input::new));
    }

    /** A product and the chance it drops. */
    public record Output(ItemStack stack, float chance) {
        public static final Codec<Output> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ItemStack.CODEC.fieldOf("item").forGetter(Output::stack),
                Codec.floatRange(0.0F, 1.0F).optionalFieldOf("chance", 1.0F).forGetter(Output::chance)
        ).apply(instance, Output::new));
    }

    private final MachineType machine;
    private final List<Input> inputs;
    private final List<Output> outputs;
    private final int time;
    private final int flux;
    @Nullable
    private final FluxType fluxType;

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

    @Nullable
    public FluxType fluxType() {
        return fluxType;
    }

    /**
     * Which input slot each ingredient is taken from, or {@code null} if the slots do not hold
     * everything the recipe needs. Each slot serves at most one ingredient.
     */
    @Nullable
    public int[] assign(MachineInput input) {
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
            products.add(output.stack().copy());
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
    public ItemStack assemble(MachineInput input, HolderLookup.Provider registries) {
        return outputs.isEmpty() ? ItemStack.EMPTY : outputs.get(0).stack().copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return outputs.isEmpty() ? ItemStack.EMPTY : outputs.get(0).stack();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> all = NonNullList.create();
        for (Input input : inputs) {
            all.add(input.ingredient());
        }
        return all;
    }

    /** Machine recipes have no place in the crafting recipe book. */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.serializer(machine).get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.type(machine).get();
    }
}
