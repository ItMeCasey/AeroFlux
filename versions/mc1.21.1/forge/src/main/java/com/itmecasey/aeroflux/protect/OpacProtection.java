package com.itmecasey.aeroflux.protect;

import com.itmecasey.aeroflux.core.protect.Verdict;
import xaero.pac.common.server.api.OpenPACServerAPI;

/**
 * Open Parties and Claims, asked directly: does the actor have access to the chunk, by the claim's
 * own party and ally rules? Works from the player's id alone, so an owner who is offline is still
 * judged by their party. Loaded only when the mod is installed (see {@link Protection}); every
 * other class stays free of its types.
 */
final class OpacProtection implements ProtectionProvider {
    @Override
    public Verdict check(Request request) {
        boolean access = OpenPACServerAPI.get(request.level().getServer()).getChunkProtection()
                .hasChunkAccess(request.actor().getUUID(), request.level().dimension().location(), request.pos().getX() >> 4, request.pos().getZ() >> 4);
        return access ? Verdict.ALLOW : Verdict.DENY;
    }
}
