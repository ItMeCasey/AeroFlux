package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.machine.EnchantRules;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Flux Disenchanter: moves the enchantments of an item onto a plain book, for Moon Flux. The
 * item survives (damage and all) and keeps its curses, the way a grindstone leaves them, and keeps
 * anything the config blocks. Both the stripped item and the new enchanted book come out. When the
 * config switches it on, the machine also gives back experience for what it strips, as orbs on top.
 */
public class DisenchanterBlockEntity extends FluxMachineBlockEntity {
    private static final int ITEM = 0;
    private static final int BOOK = 1;

    public DisenchanterBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Nullable
    @Override
    protected Job findJob() {
        ItemStack target = inventory().getStackInSlot(ITEM);
        ItemStack book = inventory().getStackInSlot(BOOK);
        if (!target.isEnchanted() || target.is(Items.ENCHANTED_BOOK) || !book.is(Items.BOOK)) {
            return null;
        }

        ItemEnchantments.Mutable moved = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        int total = 0;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : target.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet()) {
            if (moves(entry.getKey())) {
                moved.set(entry.getKey(), entry.getIntValue());
                total += entry.getIntValue();
            }
        }
        if (total == 0) {
            return null;
        }

        ItemStack stripped = target.copyWithCount(1);
        EnchantmentHelper.updateEnchantments(stripped, enchantments -> enchantments.removeIf(DisenchanterBlockEntity::moves));
        ItemStack newBook = new ItemStack(Items.ENCHANTED_BOOK);
        newBook.set(DataComponents.STORED_ENCHANTMENTS, moved.toImmutable());
        return new DisenchantJob(stripped, newBook, total, ItemStack.hashItemAndComponents(target));
    }

    /** Whether an enchantment comes off onto the book: not a curse, and not one the config blocks. */
    private static boolean moves(Holder<Enchantment> enchantment) {
        return !enchantment.is(EnchantmentTags.CURSE) && EnchantSettings.listsAllow(enchantment);
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
            int xp = EnchantRules.disenchantXp(totalLevels);
            if (xp > 0 && level instanceof ServerLevel server) {
                ExperienceOrb.award(server, Vec3.atCenterOf(worldPosition.above()), xp);
            }
        }
    }
}
