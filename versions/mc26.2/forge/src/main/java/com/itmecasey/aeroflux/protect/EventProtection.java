package com.itmecasey.aeroflux.protect;

import com.itmecasey.aeroflux.core.protect.Verdict;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;

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
        boolean cancelled = switch (request.action()) {
            case BREAK_BLOCK -> breakBlock(request);
            case PLACE_BLOCK -> BlockEvent.EntityPlaceEvent.BUS.post(new BlockEvent.EntityPlaceEvent(
                    BlockSnapshot.create(request.level().dimension(), request.level(), request.pos()),
                    request.level().getBlockState(request.pos().below()), request.actor()));
            case USE_ENTITY -> request.target() != null
                    ? PlayerInteractEvent.EntityInteractSpecific.BUS.post(new PlayerInteractEvent.EntityInteractSpecific(
                            request.actor(), InteractionHand.MAIN_HAND, request.target(), Vec3.ZERO))
                    : useBlock(request);
            case USE_BLOCK -> useBlock(request);
        };
        return cancelled ? Verdict.DENY : Verdict.ALLOW;
    }

    /** A break is vetoed by cancelling the event or by setting its result to deny; claim mods do either. */
    private static boolean breakBlock(Request request) {
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(request.level(), request.pos(),
                request.level().getBlockState(request.pos()), request.actor(), Result.DEFAULT);
        boolean cancelled = BlockEvent.BreakEvent.BUS.post(event);
        return cancelled || event.getResult().isDenied();
    }

    private static boolean useBlock(Request request) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(request.pos()), Direction.UP, request.pos(), false);
        return PlayerInteractEvent.RightClickBlock.BUS.post(
                new PlayerInteractEvent.RightClickBlock(request.actor(), InteractionHand.MAIN_HAND, request.pos(), hit));
    }
}
