package com.itmecasey.aeroflux.world;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.AeroFluxConfig;
import com.itmecasey.aeroflux.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * Chopping a log by hand sometimes shakes loose a piece of Sawdust, so Flux Dust, which is made
 * from Sawdust, is within reach from the first tree. The chance is {@code sawdustFromLogsChance}
 * in the config; 0 turns it off. Creative players get nothing, and a break another mod cancelled
 * gives nothing either.
 */
@EventBusSubscriber(modid = AeroFlux.MODID)
public final class SawdustFromLogs {
    private SawdustFromLogs() {
    }

    // Last, so a claim mod or anything else that cancels the break has had its say.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void logBroken(BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getPlayer().isCreative()
                || !event.getState().is(BlockTags.LOGS)) {
            return;
        }
        if (level.getRandom().nextDouble() < AeroFluxConfig.sawdustFromLogsChance()) {
            Block.popResource(level, event.getPos(), new ItemStack(ModItems.SAWDUST.get()));
        }
    }
}
