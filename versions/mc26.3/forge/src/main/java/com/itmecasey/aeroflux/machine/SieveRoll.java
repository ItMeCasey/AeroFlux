package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.machine.SieveMesh;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * One sieve drop, read from Ex Deorum or Ex Nihilo Sequentia: sifting {@code material} through
 * {@code mesh} gives {@code amount} of {@code product}, and the run needs {@code water} mB from
 * the sifter's tank (Ex Nihilo's waterlogged recipes).
 *
 * @param product the item, with a count that each rolled item is multiplied by (usually 1)
 */
public record SieveRoll(Ingredient material, Ingredient mesh, ItemStackTemplate product, SieveMesh.Amount amount, int water) {
    /** Whether this drop comes from sifting this block through this mesh. */
    public boolean runs(ItemStack materialStack, ItemStack meshStack) {
        return !materialStack.isEmpty() && !meshStack.isEmpty() && material.test(materialStack) && mesh.test(meshStack);
    }
}
