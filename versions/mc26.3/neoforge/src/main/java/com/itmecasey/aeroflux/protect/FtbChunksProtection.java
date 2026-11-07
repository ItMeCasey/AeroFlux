package com.itmecasey.aeroflux.protect;

import com.itmecasey.aeroflux.core.protect.Verdict;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import net.minecraft.world.InteractionHand;

/**
 * FTB Chunks, asked directly with the kind of protection it names for each action, so a team's own
 * settings (who may edit, who may interact, whether fake players are allowed at all) decide. Loaded
 * only when the mod is installed (see {@link Protection}); every other class stays free of its types.
 */
final class FtbChunksProtection implements ProtectionProvider {
    @Override
    public Verdict check(Request request) {
        if (!FTBChunksAPI.api().isManagerLoaded()) {
            return Verdict.ABSTAIN;
        }
        dev.ftb.mods.ftbchunks.api.Protection kind = switch (request.action()) {
            case BREAK_BLOCK, PLACE_BLOCK -> dev.ftb.mods.ftbchunks.api.Protection.EDIT_BLOCK;
            case USE_BLOCK -> dev.ftb.mods.ftbchunks.api.Protection.INTERACT_BLOCK;
            case USE_ENTITY -> dev.ftb.mods.ftbchunks.api.Protection.INTERACT_ENTITY;
        };
        boolean prevented = FTBChunksAPI.api().getManager()
                .shouldPreventInteraction(request.actor(), InteractionHand.MAIN_HAND, request.pos(), kind, request.target());
        return prevented ? Verdict.DENY : Verdict.ALLOW;
    }
}
