package com.itmecasey.aeroflux.machine.configurator;

import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.core.configurator.ConfiguratorPresets;
import com.itmecasey.aeroflux.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/**
 * A button on the Configurator's preset screen, sent to the server: pick, rename or clear a
 * preset, or undo the last paste. The server finds the Configurator in the named hand again and
 * checks everything itself, so a modified client can do no more than the screen could.
 *
 * <p>The channel is optional, like the storage sizes one, so a client without AeroFlux can still
 * join a server that has it.
 *
 * @param offhand whether the Configurator is in the off hand
 * @param action  one of the {@code SELECT}, {@code RENAME}, {@code CLEAR} and {@code UNDO} numbers
 * @param slot    the preset slot it is about
 * @param name    the new name, for a rename
 */
public record ConfiguratorPayload(boolean offhand, int action, int slot, String name) {
    public static final int SELECT = 0;
    public static final int RENAME = 1;
    public static final int CLEAR = 2;
    public static final int UNDO = 3;

    /** Built by {@link #register()}, while mods load. */
    private static SimpleChannel channel;

    /** Builds the channel. Forge wants channels made while mods load, so the mod constructor calls this. */
    public static void register() {
        channel = ChannelBuilder.named(Ids.mod("configurator"))
                .networkProtocolVersion(1)
                .optional()
                .simpleChannel()
                .messageBuilder(ConfiguratorPayload.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder((payload, buf) -> {
                    buf.writeBoolean(payload.offhand());
                    buf.writeVarInt(payload.action());
                    buf.writeVarInt(payload.slot());
                    buf.writeUtf(payload.name(), ConfiguratorPresets.NAME_MAX * 4);
                })
                .decoder(buf -> new ConfiguratorPayload(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                        buf.readUtf(ConfiguratorPresets.NAME_MAX * 4)))
                .consumerMainThread((payload, context) -> {
                    handle(payload, context.getSender());
                    context.setPacketHandled(true);
                })
                .add();
    }

    /** Sends a button press from the client to the server. */
    public void send() {
        channel.send(this, PacketDistributor.SERVER.noArg());
    }

    private static void handle(ConfiguratorPayload payload, ServerPlayer player) {
        if (player == null) {
            return;
        }
        ItemStack stack = player.getItemInHand(payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!stack.is(ModItems.FLUX_CONFIGURATOR.get())) {
            return;
        }
        ConfiguratorActions.Result result = switch (payload.action()) {
            case SELECT -> ConfiguratorActions.selectPreset(stack, payload.slot());
            case RENAME -> ConfiguratorActions.renamePreset(stack, payload.slot(), payload.name());
            case CLEAR -> ConfiguratorActions.clearPreset(stack, payload.slot());
            case UNDO -> ConfiguratorActions.undo((ServerLevel) player.level(), player.getUUID());
            default -> null;
        };
        if (result != null) {
            player.displayClientMessage(result.message(), true);
        }
    }
}
