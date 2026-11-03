package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.machine.EnchantRules;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.List;
import java.util.Objects;
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
import org.jetbrains.annotations.Nullable;

/**
 * The Enchanter: applies the enchantments stored in an enchanted book to an item, for
 * lapis and Moon Flux. The slots are the item, the book and the lapis. What the rules allow (by
 * default no curses, nothing the config blocks, no enchantment the item cannot take, no clash with
 * one it already has, never a lower level than it has) and what it costs live in
 * {@link EnchantRules}, set from the config; this class applies them to real stacks.
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

        ItemStack result = target.copyWithCount(1);
        int[] levels = {0};
        ItemEnchantments stored = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        EnchantmentHelper.updateEnchantments(result, enchantments -> {
            for (Object2IntMap.Entry<Holder<Enchantment>> entry : stored.entrySet()) {
                Holder<Enchantment> enchantment = entry.getKey();
                if (!EnchantRules.enchanterAllows(enchantment.is(EnchantmentTags.CURSE), enchantment.is(EnchantmentTags.TREASURE))
                        || !EnchantSettings.listsAllow(enchantment)
                        || !target.supportsEnchantment(enchantment)
                        || clashes(enchantments, enchantment)) {
                    continue;
                }
                int level = EnchantRules.appliedLevel(entry.getIntValue(), enchantment.value().getMaxLevel());
                int current = enchantments.getLevel(enchantment);
                if (current == 0 || EnchantRules.upgrades(current, level)) {
                    enchantments.set(enchantment, level);
                    levels[0] += level;
                }
            }
        });
        int total = levels[0];
        if (total == 0 || lapis.getCount() < EnchantRules.lapisCost(total)) {
            return null;
        }
        return new EnchantJob(result, total, Objects.hash(ItemStack.hashItemAndComponents(target), ItemStack.hashItemAndComponents(book)));
    }

    /** Whether the enchantment cannot sit next to one the item already has (Sharpness and Smite, say). */
    private static boolean clashes(ItemEnchantments.Mutable enchantments, Holder<Enchantment> candidate) {
        for (Holder<Enchantment> present : enchantments.keySet()) {
            if (!present.equals(candidate) && !Enchantment.areCompatible(present, candidate)) {
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
