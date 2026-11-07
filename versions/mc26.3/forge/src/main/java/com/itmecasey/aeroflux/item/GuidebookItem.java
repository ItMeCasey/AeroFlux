package com.itmecasey.aeroflux.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The AeroFlux Guide: using it opens the guide screen. The screen is client code, so the client
 * setup hands this class a way to open it ({@link #setOpener}); on a dedicated server there is
 * none and nothing happens.
 */
public class GuidebookItem extends Item {
    private static Runnable opener = () -> {
    };

    public GuidebookItem(Properties properties) {
        super(properties);
    }

    /** Set once by the client setup. */
    public static void setOpener(Runnable open) {
        opener = open;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            opener.run();
        }
        return InteractionResult.SUCCESS;
    }
}
