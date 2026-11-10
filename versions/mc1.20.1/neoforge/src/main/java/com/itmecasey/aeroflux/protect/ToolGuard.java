package com.itmecasey.aeroflux.protect;

import com.itmecasey.aeroflux.core.protect.ProtectionAction;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import javax.annotation.Nullable;

/**
 * The protection check of one tool use, for actions that reach many blocks (a run of bundles, an
 * area of machines). It answers for each position, counts the ones refused, and tells the player
 * once at the end how many were skipped.
 */
public final class ToolGuard implements Predicate<BlockPos> {
    @Nullable
    private final Player player;
    private final ProtectionAction action;
    private int denied;

    public ToolGuard(@Nullable Player player, ProtectionAction action) {
        this.player = player;
        this.action = action;
    }

    @Override
    public boolean test(BlockPos pos) {
        if (Protection.mayPlayerAct(player, pos, action)) {
            return true;
        }
        denied++;
        return false;
    }

    /** How many positions were refused so far. */
    public int denied() {
        return denied;
    }

    /** Tells the player how many blocks a claim kept the tool from changing, if any. */
    public void report() {
        if (denied > 0 && player != null) {
            player.sendSystemMessage(Component.translatable("message.aeroflux.protected_skipped", denied));
        }
    }

    /** Tells the player that a claim stopped a single-block action. */
    public static void refuse(@Nullable Player player) {
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.aeroflux.protected"), true);
        }
    }
}
