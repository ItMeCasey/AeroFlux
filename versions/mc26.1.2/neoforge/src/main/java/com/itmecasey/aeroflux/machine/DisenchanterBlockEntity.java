package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.machine.EnchantRules;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The Flux Disenchanter: moves the enchantments of an item onto a plain book, for Moon Flux. The
 * item survives (damage and all) and keeps its curses, the way a grindstone leaves them. Both the
 * stripped item and the new enchanted book come out. Giving back XP is not built yet.
 */
public class DisenchanterBlockEntity extends FluxMachineBlockEntity {
    private static final int ITEM = 0;
    private static final int BOOK = 1;

    public DisenchanterBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    protected @Nullable Job findJob() {
        ItemStack target = stackIn(ITEM);
        ItemStack book = stackIn(BOOK);
        if (!target.isEnchanted() || target.is(Items.ENCHANTED_BOOK) || !book.is(Items.BOOK)) {
            return null;
        }

        ItemEnchantments.Mutable moved = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        int total = 0;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : target.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet()) {
            if (!entry.getKey().is(EnchantmentTags.CURSE)) {
                moved.set(entry.getKey(), entry.getIntValue());
                total += entry.getIntValue();
            }
        }
        if (total == 0) {
            return null;
        }

        ItemStack stripped = target.copyWithCount(1);
        EnchantmentHelper.updateEnchantments(stripped, enchantments -> enchantments.removeIf(holder -> !holder.is(EnchantmentTags.CURSE)));
        ItemStack newBook = new ItemStack(Items.ENCHANTED_BOOK);
        newBook.set(DataComponents.STORED_ENCHANTMENTS, moved.toImmutable());
        return new DisenchantJob(stripped, newBook, total, ItemStack.hashItemAndComponents(target));
    }

    @Override
    protected boolean isInputValid(int slot, ItemStack stack) {
        return level != null && MachineInputs.accepts(machine(), level, slot, stack);
    }

    /** One item being stripped. */
    private final class DisenchantJob implements Job {
        private final ItemStack stripped;
        private final ItemStack newBook;
        private final int totalLevels;
        private final int key;

        DisenchantJob(ItemStack stripped, ItemStack newBook, int totalLevels, int key) {
            this.stripped = stripped;
            this.newBook = newBook;
            this.totalLevels = totalLevels;
            this.key = key;
        }

        @Override
        public Object key() {
            return key;
        }

        @Override
        public int baseTicks() {
            return EnchantRules.baseTicks(totalLevels);
        }

        @Override
        public boolean outputFits() {
            return canFitOutputs(List.of(stripped, newBook));
        }

        @Override
        public int extraFlux() {
            return EnchantRules.disenchantFlux(totalLevels);
        }

        @Override
        public FluxType requiredFlux() {
            return FluxType.MOON;
        }

        @Override
        public void complete() {
            consumeInput(ITEM, 1);
            consumeInput(BOOK, 1);
            putOutputs(List.of(stripped, newBook));
        }
    }
}
