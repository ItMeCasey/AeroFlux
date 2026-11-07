package com.itmecasey.aeroflux.machine.recipe;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

/** What a machine's input slots hold, handed to a recipe to look at. */
public record MachineInput(List<ItemStack> stacks) implements RecipeInput {
    @Override
    public ItemStack getItem(int index) {
        return stacks.get(index);
    }

    @Override
    public int size() {
        return stacks.size();
    }
}
