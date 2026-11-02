package com.itmecasey.aeroflux.conduit;

import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.DistributionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeType;
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
 * The wrench. Used on a conduit bundle it cycles the mode of the pipe nearest the crosshair at
 * the face that was clicked; used while sneaking it cycles that pipe's distribution mode
 * instead. A pipe is only four pixels wide, so the nearest pipe is chosen instead of asking the
 * player to hit it exactly.
 */
public class FluxWrenchItem extends Item {
    public FluxWrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity entity)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // Where the crosshair hit, in pixels inside the block.
        Vec3 local = context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ()).scale(16.0);
        PipeType pipe = entity.bundle().nearestPipe(local.x, local.y, local.z);
        if (pipe == null) {
            return InteractionResult.PASS;
        }

        Player player = context.getPlayer();
        if (context.isSecondaryUseActive()) {
            cycleDistribution(entity, pipe, player);
            return InteractionResult.CONSUME;
        }

        Face face = Face.byIndex(context.getClickedFace().get3DDataValue());
        ConnectionMode next = entity.bundle().mode(pipe.lane(), face).next();
        entity.bundle().setMode(pipe.lane(), face, next);
        entity.refreshConnections();
        entity.changed();

        if (player != null) {
            player.displayClientMessage(Component.translatable("message.aeroflux.pipe_mode",
                    Component.translatable("pipe.aeroflux." + pipe.id()),
                    Component.translatable("face.aeroflux." + face.name().toLowerCase(Locale.ROOT)),
                    Component.translatable("connection_mode.aeroflux." + next.name().toLowerCase(Locale.ROOT))), true);
        }
        return InteractionResult.CONSUME;
    }

    /** Moves the pipe on to its next distribution mode, or says the pipe has none. */
    private static void cycleDistribution(ConduitBundleBlockEntity entity, PipeType pipe, Player player) {
        Component pipeName = Component.translatable("pipe.aeroflux." + pipe.id());
        if (!pipe.usesDistribution()) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.aeroflux.pipe_no_distribution", pipeName), true);
            }
            return;
        }
        DistributionMode next = entity.bundle().distribution(pipe.lane()).next();
        entity.bundle().setDistribution(pipe.lane(), next);
        entity.changed();
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.aeroflux.pipe_distribution", pipeName,
                    Component.translatable("distribution_mode.aeroflux." + next.name().toLowerCase(Locale.ROOT))), true);
        }
    }
}
