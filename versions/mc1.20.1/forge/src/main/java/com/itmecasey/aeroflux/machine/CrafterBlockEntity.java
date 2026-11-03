package com.itmecasey.aeroflux.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The Crafter, an auto crafting table. Its nine input slots are the 3 by 3 grid, read the way
 * a crafting table reads it. It never takes the last item out of a slot, so the pattern a player
 * lays out stays put: a slot needs two items before the Crafter will use one. Pipes may only top up
 * slots that already hold the same item, and the Crafter spreads matching items evenly over the
 * slots that share them, so a pipe that fills the first slot still feeds the whole pattern.
 */
public class CrafterBlockEntity extends FluxMachineBlockEntity {
    /** One craft takes a second at copper; tier and Speed upgrades shorten it. */
    private static final int CRAFT_TICKS = 20;
    private static final int GRID = 3;

    public CrafterBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    protected boolean isInputValid(int slot, ItemStack stack) {
        return !stack.isEmpty();
    }

    /** Pipes may only add to a slot that already holds the same item, so they never change the pattern. */
    @Override
    public boolean acceptsFromOutside(int slot, ItemStack stack) {
        ItemStack held = stackIn(slot);
        return !held.isEmpty() && ItemStack.isSameItemSameTags(held, stack);
    }

    /**
     * The crafting grid of 1.20.1 tells a menu when it changes. The crafter has no menu behind the
     * grid it fills, so this one listens to nothing.
     */
    private static final AbstractContainerMenu NO_MENU = new AbstractContainerMenu(null, -1) {
        @Override
        public ItemStack quickMoveStack(Player player, int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return false;
        }
    };

    @Nullable
    @Override
    protected Job findJob() {
        if (!(level instanceof ServerLevel server)) {
            return null;
        }
        spreadEvenly();
        List<ItemStack> grid = inputStacks();
        boolean any = false;
        List<ItemStack> pattern = new ArrayList<>();
        for (ItemStack stack : grid) {
            if (!stack.isEmpty() && stack.getCount() < 2) {
                // The last item of a slot is the pattern; it is never crafted away.
                return null;
            }
            any |= !stack.isEmpty();
            pattern.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        }
        if (!any) {
            return null;
        }
        CraftingContainer input = new TransientCraftingContainer(NO_MENU, GRID, GRID);
        for (int slot = 0; slot < pattern.size(); slot++) {
            input.setItem(slot, pattern.get(slot));
        }
        Optional<CraftingRecipe> found = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, server);
        if (found.isEmpty()) {
            return null;
        }
        ItemStack result = found.get().assemble(input, server.registryAccess());
        if (result.isEmpty()) {
            return null;
        }
        List<ItemStack> products = new ArrayList<>();
        products.add(result);
        NonNullList<ItemStack> leftovers = found.get().getRemainingItems(input);
        for (ItemStack leftover : leftovers) {
            if (!leftover.isEmpty()) {
                products.add(leftover);
            }
        }
        return new CraftJob(found.get().getId(), products);
    }

    /**
     * Shares each item out evenly over every grid slot that holds it, so a pipe that tops up only
     * the first matching slot still keeps every slot of the pattern above one.
     */
    private void spreadEvenly() {
        int size = machine().inputSlots();
        boolean[] done = new boolean[size];
        for (int first = 0; first < size; first++) {
            ItemStack kind = stackIn(first);
            if (done[first] || kind.isEmpty()) {
                continue;
            }
            List<Integer> slots = new ArrayList<>();
            int total = 0;
            for (int slot = first; slot < size; slot++) {
                ItemStack stack = stackIn(slot);
                if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, kind)) {
                    slots.add(slot);
                    total += stack.getCount();
                    done[slot] = true;
                }
            }
            int each = total / slots.size();
            int extra = total % slots.size();
            for (int i = 0; i < slots.size(); i++) {
                int wanted = Math.min(kind.getMaxStackSize(), each + (i < extra ? 1 : 0));
                if (stackIn(slots.get(i)).getCount() != wanted) {
                    setStackIn(slots.get(i), kind.copyWithCount(wanted));
                }
            }
        }
    }

    /** One craft: one item from every filled slot, the result and any leftovers (empty buckets) out. */
    private final class CraftJob implements Job {
        private final ResourceLocation recipe;
        private final List<ItemStack> products;

        CraftJob(ResourceLocation recipe, List<ItemStack> products) {
            this.recipe = recipe;
            this.products = products;
        }

        @Override
        public Object key() {
            return recipe;
        }

        @Override
        public int baseTicks() {
            return CRAFT_TICKS;
        }

        @Override
        public boolean outputFits() {
            return canFitOutputs(products);
        }

        @Override
        public void complete() {
            for (int slot = 0; slot < machine().inputSlots(); slot++) {
                if (!stackIn(slot).isEmpty()) {
                    consumeInput(slot, 1);
                }
            }
            putOutputs(products);
        }
    }
}
