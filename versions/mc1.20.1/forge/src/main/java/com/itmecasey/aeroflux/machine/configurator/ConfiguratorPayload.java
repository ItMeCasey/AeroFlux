package com.itmecasey.aeroflux.machine.configurator;

import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.core.configurator.ConfiguratorPresets;
import com.itmecasey.aeroflux.registry.ModItems;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * A button on the Configurator's preset screen, sent to the server: pick, rename or clear a
 * preset, or undo the last paste. The server finds the Configurator in the named hand again and
 * checks everything itself, so a modified client can do no more than the screen could.
 *
 * <p>The channel is optional on both ends, like the storage size one, so a client without it can
 * still join a server that has it.
 */
public final class ConfiguratorPayload {
    public static final int SELECT = 0;
    public static final int RENAME = 1;
    public static final int CLEAR = 2;
    public static final int UNDO = 3;

    private static final String PROTOCOL = "1";

    /** Built by {@link #register()}, while mods load. */
    private static SimpleChannel channel;

    private ConfiguratorPayload() {
    }

    /**
     * The message.
     *
     * @param offhand whether the Configurator is in the off hand
     * @param action  one of the {@code SELECT}, {@code RENAME}, {@code CLEAR} and {@code UNDO} numbers
     * @param slot    the preset slot it is about
     * @param name    the new name, for a rename
     */
    private record Message(boolean offhand, int action, int slot, String name) {
    }

    /** Builds the channel. Forge wants channels made while mods load, so the mod constructor calls this. */
    public static void register() {
        channel = NetworkRegistry.newSimpleChannel(Ids.mod("configurator"), () -> PROTOCOL,
                NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL));
        channel.registerMessage(0, Message.class,
                (message, buf) -> {
                    buf.writeBoolean(message.offhand());
                    buf.writeVarInt(message.action());
                    buf.writeVarInt(message.slot());
                    buf.writeUtf(message.name(), ConfiguratorPresets.NAME_MAX * 4);
                },
                buf -> new Message(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                        buf.readUtf(ConfiguratorPresets.NAME_MAX * 4)),
                ConfiguratorPayload::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    /** Sends a button press of the preset screen to the server. */
    public static void send(boolean offhand, int action, int slot, String name) {
        channel.sendToServer(new Message(offhand, action, slot, name));
    }

    private static void handle(Message message, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context network = context.get();
        ServerPlayer player = network.getSender();
        network.enqueueWork(() -> apply(player, message));
        network.setPacketHandled(true);
    }

    private static void apply(ServerPlayer player, Message message) {
        if (player == null) {
            return;
        }
        ItemStack stack = player.getItemInHand(message.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!stack.is(ModItems.FLUX_CONFIGURATOR.get())) {
            return;
        }
        ConfiguratorActions.Result result = switch (message.action()) {
            case SELECT -> ConfiguratorActions.selectPreset(stack, message.slot());
            case RENAME -> ConfiguratorActions.renamePreset(stack, message.slot(), message.name());
            case CLEAR -> ConfiguratorActions.clearPreset(stack, message.slot());
            case UNDO -> ConfiguratorActions.undo((ServerLevel) player.level(), player.getUUID());
            default -> null;
        };
        if (result != null) {
            player.displayClientMessage(result.message(), true);
        }
    }
}
