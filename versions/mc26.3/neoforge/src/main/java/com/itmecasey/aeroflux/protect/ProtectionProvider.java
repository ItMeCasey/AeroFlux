package com.itmecasey.aeroflux.protect;

import com.itmecasey.aeroflux.core.protect.ProtectionAction;
import com.itmecasey.aeroflux.core.protect.Verdict;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * One source of protection answers: the standard permission events, or a claim mod's own API.
 * {@link Protection} asks every provider and lets any denial win. Other mods (and the self-test)
 * can add their own with {@link Protection#addProvider}.
 */
public interface ProtectionProvider {
    /**
     * What is being asked.
     *
     * @param level  the level the place is in
     * @param actor  who is acting: the player using a tool, or a stand-in for the owner of a machine
     * @param pos    the block position acted on (an entity's position for entity actions)
     * @param target the entity acted on, or {@code null} for block actions
     * @param action what is about to happen
     */
    record Request(ServerLevel level, Player actor, BlockPos pos, @Nullable Entity target, ProtectionAction action) {
    }

    /** The provider's answer; {@link Verdict#ABSTAIN} when it has nothing to say about the place. */
    Verdict check(Request request);
}
