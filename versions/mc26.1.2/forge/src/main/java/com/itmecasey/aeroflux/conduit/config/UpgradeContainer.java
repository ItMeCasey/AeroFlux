package com.itmecasey.aeroflux.conduit.config;

import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.conduit.UpgradeItem;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.conduit.PipeUpgrades;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A view of the upgrades in one pipe, shaped like a container so ordinary slots can show it.
 * Nothing is stored here: a bundle only counts its upgrades, so every read works out the stacks
 * from those counts (see {@link PipeConfigActions#upgradeKindAt}) and every write goes through
 * {@link PipeConfigActions}, which saves and syncs the bundle. This is the server's side; the
 * client's slots hold plain copies that the menu keeps in step.
 */
final class UpgradeContainer implements Container {
    private final ConduitBundleBlockEntity entity;
    private final PipeType type;
    private final int size;

    UpgradeContainer(ConduitBundleBlockEntity entity, PipeType type, int size) {
        this.entity = entity;
        this.type = type;
        this.size = size;
    }

    @Override
    public int getContainerSize() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return entity.bundle().upgradeCount(type.lane()) == 0;
    }

    @Override
    public ItemStack getItem(int slot) {
        PipeUpgrades.Kind kind = PipeConfigActions.upgradeKindAt(entity.bundle(), type, slot);
        return kind == null ? ItemStack.EMPTY : PipeConfigActions.upgradeStack(kind);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        // Every slot holds one upgrade, so any amount above zero takes the whole stack.
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }
        PipeUpgrades.Kind kind = PipeConfigActions.removeUpgradeAt(entity, type, slot);
        return kind == null ? ItemStack.EMPTY : PipeConfigActions.upgradeStack(kind);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return removeItem(slot, 1);
    }

    /**
     * Called when a player puts a stack into a slot, or empties it. An empty stack takes the
     * upgrade out, an upgrade item adds one (replacing the one that was there, for a swap);
     * anything else is ignored, because the slot would not have accepted it.
     */
    @Override
    public void setItem(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            PipeConfigActions.removeUpgradeAt(entity, type, slot);
        } else if (stack.getItem() instanceof UpgradeItem upgrade) {
            if (!PipeConfigActions.replaceUpgradeAt(entity, type, slot, upgrade.kind())) {
                PipeConfigActions.addUpgrade(entity, type, upgrade.kind());
            }
        }
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public void setChanged() {
        // The bundle is saved by the actions as they change it.
    }

    @Override
    public boolean stillValid(Player player) {
        return !entity.isRemoved();
    }

    @Override
    public void clearContent() {
        // The upgrades belong to the pipe; closing the screen must never clear them.
    }
}
