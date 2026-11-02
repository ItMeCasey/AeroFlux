package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.machine.EnchantRules;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Flux Enchanter: applies the enchantments stored in an enchanted book to an item, for
 * lapis and Moon Flux. The slots are the item, the book and the lapis. What the rules allow (no
 * curses, no enchantment the item cannot take, no clash with one it already has, never a lower
 * level than it has) and what it costs live in {@link EnchantRules}; this class applies them to
 * real stacks.
 *
 * <p>Enchantments in this version are plain item data: an item keeps its own in the
 * {@code Enchantments} list and a book keeps the ones it stores in {@code StoredEnchantments},
 * and {@link EnchantmentHelper} reads and writes either as a map.
 */
public class EnchanterBlockEntity extends FluxMachineBlockEntity {
    private static final int ITEM = 0;
    private static final int BOOK = 1;
    private static final int LAPIS = 2;

    public EnchanterBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Nullable
    @Override
    protected Job findJob() {
        ItemStack target = inventory().getStackInSlot(ITEM);
        ItemStack book = inventory().getStackInSlot(BOOK);
        ItemStack lapis = inventory().getStackInSlot(LAPIS);
        if (target.isEmpty() || !book.is(Items.ENCHANTED_BOOK) || !lapis.is(Items.LAPIS_LAZULI)) {
            return null;
        }

        // The item's enchantments after the book is applied, built up one enchantment at a time
        // so that each one is judged against what was applied before it.
        Map<Enchantment, Integer> enchantments = new LinkedHashMap<>(EnchantmentHelper.getEnchantments(target));
        int total = 0;
        for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.getEnchantments(book).entrySet()) {
            Enchantment enchantment = entry.getKey();
            if (!EnchantRules.enchanterAllows(enchantment.isCurse(), enchantment.isTreasureOnly())
                    || !target.canApplyAtEnchantingTable(enchantment)
                    || clashes(enchantments, enchantment)) {
                continue;
            }
            int level = EnchantRules.appliedLevel(entry.getValue(), enchantment.getMaxLevel());
            int current = enchantments.getOrDefault(enchantment, 0);
            if (current == 0 || EnchantRules.upgrades(current, level)) {
                enchantments.put(enchantment, level);
                total += level;
            }
        }
        if (total == 0 || lapis.getCount() < EnchantRules.lapisCost(total)) {
            return null;
        }

        ItemStack result = target.copy();
        result.setCount(1);
        EnchantmentHelper.setEnchantments(enchantments, result);
        return new EnchantJob(result, total, Objects.hash(target.getItem(), target.getTag(), book.getItem(), book.getTag()));
    }

    /** Whether the enchantment cannot sit next to one the item already has (Sharpness and Smite, say). */
    private static boolean clashes(Map<Enchantment, Integer> enchantments, Enchantment candidate) {
        for (Enchantment present : enchantments.keySet()) {
            if (present != candidate && !present.isCompatibleWith(candidate)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean isInputValid(int slot, ItemStack stack) {
        return level != null && MachineInputs.accepts(machine(), level, slot, stack);
    }

    /** One item being enchanted. */
    private final class EnchantJob implements Job {
        private final ItemStack result;
        private final int totalLevels;
        private final int key;

        EnchantJob(ItemStack result, int totalLevels, int key) {
            this.result = result;
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
            return canFitOutputs(List.of(result));
        }

        @Override
        public int extraFlux() {
            return EnchantRules.enchantFlux(totalLevels);
        }

        @Override
        public FluxType requiredFlux() {
            return FluxType.MOON;
        }

        @Override
        public void complete() {
            consumeInput(ITEM, 1);
            consumeInput(BOOK, 1);
            consumeInput(LAPIS, EnchantRules.lapisCost(totalLevels));
            putOutputs(List.of(result));
        }
    }
}
