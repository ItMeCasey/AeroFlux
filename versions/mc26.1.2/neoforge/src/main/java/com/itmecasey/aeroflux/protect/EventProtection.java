package com.itmecasey.aeroflux.protect;

import com.itmecasey.aeroflux.core.protect.Verdict;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * Asks the question the way the game itself does when a player does the thing: by posting the
 * standard event and seeing whether anything cancels it. Claim mods, permission mods and protection
 * plugins all listen to these, so this one provider covers every mod that follows the convention,
 * with no code for any of them in particular.
 *
 * <p>The events are posted only to ask; nothing is broken or placed. A handler that does more than
 * veto (rare) would see a stand-in player with an empty hand, the same as a deployer.
 */
final class EventProtection implements ProtectionProvider {
    @Override
    public Verdict check(Request request) {
        Event event = switch (request.action()) {
            case BREAK_BLOCK -> new BreakBlockEvent(request.level(), request.pos(),
                    request.level().getBlockState(request.pos()), request.actor());
            case PLACE_BLOCK -> new BlockEvent.EntityPlaceEvent(
                    BlockSnapshot.create(request.level().dimension(), request.level(), request.pos()),
                    request.level().getBlockState(request.pos().below()), request.actor());
            case USE_ENTITY -> request.target() != null
                    ? new PlayerInteractEvent.EntityInteract(request.actor(), InteractionHand.MAIN_HAND, request.target())
                    : useBlock(request);
            case USE_BLOCK -> useBlock(request);
        };
        NeoForge.EVENT_BUS.post(event);
        return event instanceof ICancellableEvent cancellable && cancellable.isCanceled() ? Verdict.DENY : Verdict.ALLOW;
    }

    private static PlayerInteractEvent.RightClickBlock useBlock(Request request) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(request.pos()), Direction.UP, request.pos(), false);
        return new PlayerInteractEvent.RightClickBlock(request.actor(), InteractionHand.MAIN_HAND, request.pos(), hit);
    }
}
