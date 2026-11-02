package com.itmecasey.aeroflux.machine;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The Flux Repairer: it pours Bottles o' Enchanting into a damaged tool or armor piece, at the same
 * rate Mending repairs (two points of durability per point of experience, seven points to a
 * bottle on average). The item keeps its enchantments. Once whole it moves to the output slot.
 */
public class RepairerBlockEntity extends FluxMachineBlockEntity {
    private static final int REPAIR_TICKS = 20;
    /** The experience in an average Bottle o' Enchanting. */
    public static final int XP_PER_BOTTLE = 7;
    /** Durability each experience point repairs, the same as Mending. */
    public static final int DURABILITY_PER_XP = 2;

    private static final int ITEM_SLOT = 0;
    private static final int BOTTLE_SLOT = 1;

    public RepairerBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    protected boolean isInputValid(int slot, ItemStack stack) {
        return slot == ITEM_SLOT ? stack.isDamageableItem() : stack.is(Items.EXPERIENCE_BOTTLE);
    }

    @Override
    protected @Nullable Job findJob() {
        ItemStack item = stackIn(ITEM_SLOT);
        if (item.isEmpty()) {
            return null;
        }
        if (!item.isDamaged()) {
            return finishJob(item);
        }
        if (!stackIn(BOTTLE_SLOT).is(Items.EXPERIENCE_BOTTLE)) {
            return null;
        }
        return new Job() {
            @Override
            public Object key() {
                return "repair";
            }

            @Override
            public int baseTicks() {
                return REPAIR_TICKS;
            }

            @Override
            public boolean outputFits() {
                return true;
            }

            @Override
            public void complete() {
                ItemStack repaired = stackIn(ITEM_SLOT);
                if (repaired.isEmpty() || !repaired.isDamaged()) {
                    return;
                }
                consumeInput(BOTTLE_SLOT, 1);
                repaired.setDamageValue(Math.max(0, repaired.getDamageValue() - XP_PER_BOTTLE * DURABILITY_PER_XP));
                setStackIn(ITEM_SLOT, repaired);
            }
        };
    }

    /** Moves a whole item to the output slot. */
    private Job finishJob(ItemStack item) {
        return new Job() {
            @Override
            public Object key() {
                return "done";
            }

            @Override
            public int baseTicks() {
                return 1;
            }

            @Override
            public boolean outputFits() {
                return canFitOutputs(List.of(item));
            }

            @Override
            public void complete() {
                ItemStack whole = stackIn(ITEM_SLOT);
                if (!whole.isEmpty() && canFitOutputs(List.of(whole))) {
                    setStackIn(ITEM_SLOT, ItemStack.EMPTY);
                    putOutputs(List.of(whole));
                }
            }
        };
    }
}
