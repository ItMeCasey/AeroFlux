package com.itmecasey.aeroflux.client;

import com.itmecasey.aeroflux.AeroFlux;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The chunk loader's "Show Area": for a few seconds, glowing particles trace the borders of every
 * chunk the loader keeps loaded, at the player's eye level, so the area can be seen in the world.
 * It is only a picture on the player's own screen; nothing is sent to the server.
 */
@Mod.EventBusSubscriber(modid = AeroFlux.MODID, value = Dist.CLIENT)
public final class ChunkAreaPreview {
    /** How long the borders stay up, in ticks. */
    private static final int DURATION = 200;
    /** Particles are laid down this often, in ticks; each lasts about as long. */
    private static final int INTERVAL = 10;
    /** Blocks between two particles along a border. */
    private static final int SPACING = 2;

    private static ResourceKey<Level> dimension;
    private static ChunkPos center;
    private static int radius;
    private static int ticksLeft;

    private ChunkAreaPreview() {
    }

    /**
     * Starts showing the square of chunks {@code radius} chunks out from {@code center}.
     *
     * @param radius 0 for the center chunk alone, 5 for an 11 by 11 square
     */
    public static void show(ChunkPos center, int radius) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        ChunkAreaPreview.dimension = level.dimension();
        ChunkAreaPreview.center = center;
        ChunkAreaPreview.radius = radius;
        ChunkAreaPreview.ticksLeft = DURATION;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        if (ticksLeft <= 0) {
            return;
        }
        ticksLeft--;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null || level.dimension() != dimension) {
            ticksLeft = 0;
            return;
        }
        if (ticksLeft % INTERVAL != 0) {
            return;
        }
        // Every chunk border inside the area, drawn as lines at the player's eye level.
        int minX = (center.x() - radius) * 16;
        int minZ = (center.z() - radius) * 16;
        int size = (2 * radius + 1) * 16;
        double y = player.getEyeY() - 0.5;
        for (int line = 0; line <= 2 * radius + 1; line++) {
            int offset = line * 16;
            for (int along = 0; along <= size; along += SPACING) {
                level.addParticle(ParticleTypes.END_ROD, minX + offset, y, minZ + along, 0.0, 0.0, 0.0);
                level.addParticle(ParticleTypes.END_ROD, minX + along, y, minZ + offset, 0.0, 0.0, 0.0);
            }
        }
    }
}
