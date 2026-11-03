package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.machine.MachineReach;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The Flux Magnet: every half second it pulls the dropped items in its range into its output
 * slots, where pipes or Auto Export take them on. The input slot is a filter that is never used
 * up: with an item in it, the magnet takes only that item (whitelist) or everything but it
 * (blacklist). With the slot empty it takes everything. Items still in their pickup delay, such
 * as a stack a player just threw, are left alone for that moment.
 */
public class MagnetBlockEntity extends FluxMachineBlockEntity {
    private static final String TAG_WHITELIST = "whitelist";
    private static final int PULL_TICKS = 10;
    private static final String KEY = "pull";

    private boolean whitelist = true;

    public MagnetBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    protected boolean isInputValid(int slot, ItemStack stack) {
        return true;
    }

    /** The filter is set by hand only. */
    @Override
    public boolean acceptsFromOutside(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public int machineMode() {
        return whitelist ? 1 : 0;
    }

    @Override
    public boolean machineButton(int buttonId) {
        if (buttonId != MachineConfigActions.MAGNET_MODE_BUTTON) {
            return false;
        }
        whitelist = !whitelist;
        setChanged();
        return true;
    }

    private boolean wanted(ItemStack stack) {
        ItemStack filter = stackIn(0);
        if (filter.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItem(filter, stack) == whitelist;
    }

    private List<ItemEntity> itemsInRange() {
        if (level == null) {
            return List.of();
        }
        AABB range = new AABB(worldPosition).inflate(MachineReach.magnetRadius(tier()));
        return level.getEntitiesOfClass(ItemEntity.class, range,
                item -> item.isAlive() && !item.hasPickUpDelay() && wanted(item.getItem()) && mayTouch(item));
    }

    @Override
    protected @Nullable Job findJob() {
        List<ItemEntity> items = itemsInRange();
        if (items.isEmpty()) {
            return null;
        }
        ItemStack first = items.get(0).getItem();
        return new Job() {
            @Override
            public Object key() {
                return KEY;
            }

            @Override
            public int baseTicks() {
                return PULL_TICKS;
            }

            @Override
            public boolean outputFits() {
                return canFitOutputs(List.of(first.copyWithCount(1)));
            }

            @Override
            public void complete() {
                for (ItemEntity item : itemsInRange()) {
                    pull(item);
                }
            }
        };
    }

    /** Takes as much of a dropped stack as the output slots hold; the rest stays on the ground. */
    private void pull(ItemEntity item) {
        ItemStack stack = item.getItem();
        for (int count = stack.getCount(); count > 0; count--) {
            ItemStack part = stack.copyWithCount(count);
            if (canFitOutputs(List.of(part))) {
                putOutputs(List.of(part));
                if (count == stack.getCount()) {
                    item.discard();
                } else {
                    item.setItem(stack.copyWithCount(stack.getCount() - count));
                }
                return;
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean(TAG_WHITELIST, whitelist);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        whitelist = input.getBooleanOr(TAG_WHITELIST, true);
    }
}
