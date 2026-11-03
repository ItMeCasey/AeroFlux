package com.itmecasey.aeroflux.profile;

import com.itmecasey.aeroflux.AeroFlux;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /aeroflux profile [seconds]}, for server admins: samples how much server time AeroFlux's
 * block entities take for a few seconds (default {@value #DEFAULT_SECONDS}) and prints the total, the
 * time per kind of block, the heaviest blocks with their positions, and how many are loaded.
 * {@code /aeroflux profile cancel} stops a window early. Nothing is measured outside a window.
 */
@EventBusSubscriber(modid = AeroFlux.MODID)
public final class ProfileCommand {
    private static final int DEFAULT_SECONDS = 5;
    private static final int MAX_SECONDS = 120;
    private static final int TICKS_PER_SECOND = 20;

    private ProfileCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(AeroFlux.MODID)
                .then(Commands.literal("profile")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> start(context.getSource(), DEFAULT_SECONDS))
                        .then(Commands.literal("cancel").executes(context -> cancel(context.getSource())))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, MAX_SECONDS))
                                .executes(context -> start(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "seconds"))))));
    }

    private static int start(CommandSourceStack source, int seconds) {
        boolean started = TickProfiler.start(seconds * TICKS_PER_SECOND, report -> {
            for (Component line : ProfileReporter.lines(report)) {
                source.sendSuccess(() -> line, false);
            }
        });
        if (!started) {
            source.sendFailure(Component.translatable("commands.aeroflux.profile.busy"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.aeroflux.profile.started", seconds), false);
        return 1;
    }

    private static int cancel(CommandSourceStack source) {
        if (!TickProfiler.cancel()) {
            source.sendFailure(Component.translatable("commands.aeroflux.profile.none"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.aeroflux.profile.cancelled"), false);
        return 1;
    }
}
