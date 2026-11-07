package com.itmecasey.aeroflux.machine.configurator;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.core.configurator.ConfiguratorPresets;
import com.itmecasey.aeroflux.registry.ModItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A button on the Configurator's preset screen, sent to the server: pick, rename or clear a
 * preset, or undo the last paste. The server finds the Configurator in the named hand again and
 * checks everything itself, so a modified client can do no more than the screen could.
 *
 * @param offhand whether the Configurator is in the off hand
 * @param action  one of the {@code SELECT}, {@code RENAME}, {@code CLEAR} and {@code UNDO} numbers
 * @param slot    the preset slot it is about
 * @param name    the new name, for a rename
 */
@EventBusSubscriber(modid = AeroFlux.MODID)
public record ConfiguratorPayload(boolean offhand, int action, int slot, String name) implements CustomPacketPayload {
    public static final int SELECT = 0;
    public static final int RENAME = 1;
    public static final int CLEAR = 2;
    public static final int UNDO = 3;

    public static final Type<ConfiguratorPayload> TYPE = new Type<>(Ids.mod("configurator"));
    public static final StreamCodec<FriendlyByteBuf, ConfiguratorPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.offhand());
                buf.writeVarInt(payload.action());
                buf.writeVarInt(payload.slot());
                buf.writeUtf(payload.name(), ConfiguratorPresets.NAME_MAX * 4);
            },
            buf -> new ConfiguratorPayload(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(ConfiguratorPresets.NAME_MAX * 4)));

    @Override
    public Type<ConfiguratorPayload> type() {
        return TYPE;
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToServer(TYPE, CODEC, ConfiguratorPayload::handle);
    }

    private static void handle(ConfiguratorPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
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
            player.sendOverlayMessage(result.message());
        }
    }
}
