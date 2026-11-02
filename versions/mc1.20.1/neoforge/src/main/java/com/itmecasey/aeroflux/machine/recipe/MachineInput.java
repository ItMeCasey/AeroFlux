package com.itmecasey.aeroflux.machine.recipe;

import java.util.List;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * What a machine's input slots hold, handed to a recipe to look at. Recipes of this version
 * match against a {@link net.minecraft.world.Container}, so this is a small container around the
 * real input stacks; recipes only read from it.
 */
public final class MachineInput extends SimpleContainer {
    private final List<ItemStack> stacks;

    public MachineInput(List<ItemStack> stacks) {
        super(stacks.toArray(new ItemStack[0]));
        this.stacks = List.copyOf(stacks);
    }

    /** The stacks, in slot order. */
    public List<ItemStack> stacks() {
        return stacks;
    }

    /** The number of input slots. */
    public int size() {
        return getContainerSize();
    }
}
