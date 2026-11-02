package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.machine.EnchantRules;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Flux Disenchanter: moves the enchantments of an item onto a plain book, for Moon Flux. The
 * item survives (damage and all) and keeps its curses, the way a grindstone leaves them, and keeps
 * anything the config blocks. Both the stripped item and the new enchanted book come out. When the
 * config switches it on, the machine also gives back experience for what it strips, as orbs on top.
 *
 * <p>In 1.20.1 the enchantments are NBT entries, handled as maps from enchantment to level (see
 * {@link EnchanterBlockEntity}); the book is a fresh enchanted book written through the same helper.
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

        Map<Enchantment, Integer> moved = new LinkedHashMap<>();
        Map<Enchantment, Integer> kept = new LinkedHashMap<>();
        int total = 0;
        for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.getEnchantments(target).entrySet()) {
            if (moves(entry.getKey())) {
                moved.put(entry.getKey(), entry.getValue());
                total += entry.getValue();
            } else {
                kept.put(entry.getKey(), entry.getValue());
            }
        }
        if (total == 0) {
            return null;
        }

        // The item keeps its damage, its curses and what the config blocks; only the rest moves to the book.
        ItemStack stripped = target.copy();
        stripped.setCount(1);
        EnchantmentHelper.setEnchantments(kept, stripped);
        ItemStack newBook = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantmentHelper.setEnchantments(moved, newBook);
        return new DisenchantJob(stripped, newBook, total, Objects.hash(target.getItem(), target.getTag()));
    }

    /** Whether an enchantment comes off onto the book: not a curse, and not one the config blocks. */
    private static boolean moves(Enchantment enchantment) {
        return !enchantment.isCurse() && EnchantSettings.listsAllow(enchantment);
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
