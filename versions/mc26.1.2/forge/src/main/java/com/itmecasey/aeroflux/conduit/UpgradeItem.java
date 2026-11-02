package com.itmecasey.aeroflux.conduit;

import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.conduit.PipeUpgrades;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A pipe upgrade. Used on a conduit bundle it goes into the pipe nearest the crosshair, if that
 * pipe still has a free upgrade slot, and is used up.
 */
public class UpgradeItem extends Item {
    private final PipeUpgrades.Kind kind;

    public UpgradeItem(PipeUpgrades.Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    /** The kind of upgrade this item installs. */
    public PipeUpgrades.Kind kind() {
        return kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity entity)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        // Where the crosshair hit, in pixels inside the block, the same targeting the wrench uses.
        Vec3 local = context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ()).scale(16.0);
        PipeType pipe = entity.bundle().nearestPipe(local.x, local.y, local.z);
        if (pipe == null) {
            return InteractionResult.PASS;
        }

        Player player = context.getPlayer();
        boolean installed = install(entity, pipe, kind);
        if (installed && (player == null || !player.hasInfiniteMaterials())) {
            context.getItemInHand().shrink(1);
        }
        if (player != null) {
            player.sendOverlayMessage(installed ? installedMessage(entity, pipe) : fullMessage(pipe));
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Puts one upgrade into a pipe if it has a free slot and saves the change. This is the game's
     * upgrade logic, kept apart from the click handling so it can be exercised directly.
     *
     * @return false if the pipe has no free slot
     */
    public static boolean install(ConduitBundleBlockEntity entity, PipeType pipe, PipeUpgrades.Kind kind) {
        if (!entity.bundle().addUpgrade(pipe.lane(), kind)) {
            return false;
        }
        entity.changed();
        return true;
    }

    private Component installedMessage(ConduitBundleBlockEntity entity, PipeType pipe) {
        int lane = pipe.lane();
        return Component.translatable("message.aeroflux.upgrade_installed",
                Component.translatable("upgrade.aeroflux." + kind.name().toLowerCase(Locale.ROOT)),
                Component.translatable("pipe.aeroflux." + pipe.id()),
                entity.bundle().upgradeCount(lane),
                PipeUpgrades.slots(entity.bundle().tier(lane)));
    }

    private static Component fullMessage(PipeType pipe) {
        return Component.translatable("message.aeroflux.upgrade_full", Component.translatable("pipe.aeroflux." + pipe.id()));
    }
}
