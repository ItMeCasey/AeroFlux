package com.itmecasey.aeroflux.machine.recipe;

import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * What a machine's input slots hold, handed to a recipe to look at. Recipes in 1.20.1 look at a
 * {@link Container}, so this is a read-only one: it shows the stacks and refuses every change.
 *
 * @param stacks the input stacks in slot order; these are the machine's real stacks, so they are never modified here
 */
public record MachineInput(List<ItemStack> stacks) implements Container {
    /** The number of input slots. */
    public int size() {
        return stacks.size();
    }

    @Override
    public int getContainerSize() {
        return stacks.size();
    }

    @Override
    public boolean isEmpty() {
        return stacks.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int index) {
        return stacks.get(index);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        // Read-only: the machine changes its own slots.
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return false;
    }

    @Override
    public void clearContent() {
    }
}
