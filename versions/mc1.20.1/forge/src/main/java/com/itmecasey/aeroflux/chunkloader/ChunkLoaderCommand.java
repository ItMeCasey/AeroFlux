package com.itmecasey.aeroflux.chunkloader;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.core.chunkloader.ChunkLoaderMath;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * {@code /aeroflux chunkloaders}, for server admins:
 * <ul>
 *   <li>{@code list} shows every loader in every dimension, with its tier, area, owner and whether it runs;</li>
 *   <li>{@code list <dimension>} shows one dimension;</li>
 *   <li>{@code remove <pos> [<dimension>]} lets the loader's chunks go and breaks it. A position the
 *       registry knows but whose block is gone (a world edit, say) is cleaned out of the registry.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AeroFlux.MODID)
public final class ChunkLoaderCommand {
    private ChunkLoaderCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(AeroFlux.MODID)
                .then(Commands.literal("chunkloaders")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("list")
                                .executes(context -> list(context.getSource(), allLevels(context)))
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .executes(context -> list(context.getSource(),
                                                List.of(DimensionArgument.getDimension(context, "dimension"))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(context -> remove(context.getSource(), context.getSource().getLevel(),
                                                BlockPosArgument.getBlockPos(context, "pos")))
                                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                                .executes(context -> remove(context.getSource(),
                                                        DimensionArgument.getDimension(context, "dimension"),
                                                        BlockPosArgument.getBlockPos(context, "pos"))))))));
    }

    private static List<ServerLevel> allLevels(CommandContext<CommandSourceStack> context) {
        List<ServerLevel> levels = new ArrayList<>();
        context.getSource().getServer().getAllLevels().forEach(levels::add);
        return levels;
    }

    private static int list(CommandSourceStack source, List<ServerLevel> levels) {
        int total = 0;
        for (ServerLevel level : levels) {
            for (ChunkLoaderRegistry.Entry entry : ChunkLoaderRegistry.of(level).entries()) {
                total++;
                BlockPos pos = entry.pos();
                int side = ChunkLoaderMath.side(entry.tier());
                source.sendSuccess(() -> Component.translatable("commands.aeroflux.chunkloaders.entry",
                        level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ(),
                        Component.translatable("tier.aeroflux." + entry.tier().id()), side, side,
                        ownerName(source, entry.owner()),
                        Component.translatable(entry.active()
                                ? "commands.aeroflux.chunkloaders.running" : "commands.aeroflux.chunkloaders.idle")), false);
            }
        }
        int count = total;
        source.sendSuccess(() -> Component.translatable("commands.aeroflux.chunkloaders.total", count), false);
        return count;
    }

    private static int remove(CommandSourceStack source, ServerLevel level, BlockPos pos) {
        ChunkLoaderRegistry registry = ChunkLoaderRegistry.of(level);
        Optional<ChunkLoaderRegistry.Entry> entry = registry.get(pos);
        if (entry.isEmpty()) {
            source.sendFailure(Component.translatable("commands.aeroflux.chunkloaders.none", pos.getX(), pos.getY(), pos.getZ()));
            return 0;
        }
        // Release first, so the chunks go even if the block is already gone.
        ChunkLoaderTickets.release(level, pos, entry.get().tier());
        registry.remove(pos);
        if (level.getBlockState(pos).getBlock() instanceof FluxChunkLoaderBlock) {
            // Breaking it drops the block like a player mining it would.
            level.destroyBlock(pos, true);
        }
        source.sendSuccess(() -> Component.translatable("commands.aeroflux.chunkloaders.removed",
                pos.getX(), pos.getY(), pos.getZ(), level.dimension().location().toString()), true);
        return 1;
    }

    /** A player's name if the server knows it, otherwise the raw id; "nobody" for loaders no player placed. */
    private static Component ownerName(CommandSourceStack source, Optional<UUID> owner) {
        if (owner.isEmpty()) {
            return Component.translatable("commands.aeroflux.chunkloaders.no_owner");
        }
        Optional<GameProfile> known = source.getServer().getProfileCache() == null ? Optional.empty()
                : source.getServer().getProfileCache().get(owner.get());
        return Component.literal(known.map(GameProfile::getName).orElse(owner.get().toString()));
    }
}
