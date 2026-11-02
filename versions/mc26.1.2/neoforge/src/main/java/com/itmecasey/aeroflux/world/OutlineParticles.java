package com.itmecasey.aeroflux.world;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.core.configurator.ConfigArea;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Draws a box or a set of blocks in colored dust for one player for a few seconds: the Flux
 * Configurator's area before it is pasted, and the network the Conduit Tool inspects. The server
 * sends the particles straight to that player, so nobody else sees them and no client code is
 * needed. Dust fades quickly, so each outline is sent again every half second while it lasts.
 */
@EventBusSubscriber(modid = AeroFlux.MODID)
public final class OutlineParticles {
    /** How long an outline stays up, in ticks. */
    public static final int DURATION = 100;
    /** How often it is sent again, in ticks. */
    private static final int INTERVAL = 10;
    /** The most points one outline may have, so a huge network cannot flood a player's connection. */
    public static final int MAX_POINTS = 4_000;
    /** Points between two corners of one block, for outlining single blocks. */
    private static final double BLOCK_SPACING = 0.5;

    /** The Configurator's color, the blue-white of its gem. */
    public static final int CONFIGURATOR_COLOR = 0x8FD4FF;
    /** The Conduit Tool's inspect color. */
    public static final int INSPECT_COLOR = 0xFFB347;

    private static final List<Outline> ACTIVE = new ArrayList<>();

    private OutlineParticles() {
    }

    private static final class Outline {
        final ServerPlayer player;
        final ServerLevel level;
        final DustParticleOptions dust;
        final List<ConfigArea.Point> points;
        int ticksLeft = DURATION;

        Outline(ServerPlayer player, ServerLevel level, int color, List<ConfigArea.Point> points) {
            this.player = player;
            this.level = level;
            this.dust = new DustParticleOptions(color, 1.0F);
            this.points = points;
        }
    }

    /** Shows the outer edges of an area. A player has one outline at a time; a new one replaces the old. */
    public static void showArea(ServerPlayer player, ServerLevel level, ConfigArea area, int color) {
        show(player, level, color, area.outline(1.0));
    }

    /** Outlines every block in a list, such as the bundles of a network. */
    public static void showBlocks(ServerPlayer player, ServerLevel level, List<BlockPos> blocks, int color) {
        show(player, level, color, blockOutlines(blocks));
    }

    /**
     * The points that outline each of the blocks, without repeats: two blocks side by side share
     * the points of their common edges. Stops at {@link #MAX_POINTS}.
     */
    public static List<ConfigArea.Point> blockOutlines(List<BlockPos> blocks) {
        Set<ConfigArea.Point> points = new LinkedHashSet<>();
        for (BlockPos pos : blocks) {
            points.addAll(ConfigArea.outline(pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0, BLOCK_SPACING));
            if (points.size() >= MAX_POINTS) {
                break;
            }
        }
        List<ConfigArea.Point> list = new ArrayList<>(points);
        return list.size() > MAX_POINTS ? list.subList(0, MAX_POINTS) : list;
    }

    /** Stops whatever outline the player has. */
    public static void clear(ServerPlayer player) {
        ACTIVE.removeIf(outline -> outline.player == player);
    }

    private static void show(ServerPlayer player, ServerLevel level, int color, List<ConfigArea.Point> points) {
        clear(player);
        Outline outline = new Outline(player, level, color, points.size() > MAX_POINTS ? points.subList(0, MAX_POINTS) : points);
        ACTIVE.add(outline);
        send(outline);
    }

    private static void send(Outline outline) {
        for (ConfigArea.Point point : outline.points) {
            // Forced, so the whole outline shows even where it is far from the player.
            outline.level.sendParticles(outline.player, outline.dust, true, true,
                    point.x(), point.y(), point.z(), 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        Iterator<Outline> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Outline outline = it.next();
            outline.ticksLeft--;
            if (outline.ticksLeft <= 0 || outline.player.isRemoved() || outline.player.level() != outline.level) {
                it.remove();
            } else if (outline.ticksLeft % INTERVAL == 0) {
                send(outline);
            }
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }
}
