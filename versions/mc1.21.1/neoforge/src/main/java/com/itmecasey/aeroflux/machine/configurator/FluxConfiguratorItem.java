package com.itmecasey.aeroflux.machine.configurator;

import com.itmecasey.aeroflux.AeroFluxConfig;
import com.itmecasey.aeroflux.core.configurator.ConfigArea;
import com.itmecasey.aeroflux.core.configurator.ConfiguratorMode;
import com.itmecasey.aeroflux.core.configurator.ConfiguratorPresets;
import com.itmecasey.aeroflux.machine.FluxMachineBlockEntity;
import com.itmecasey.aeroflux.world.OutlineParticles;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The Flux Configurator: copies one machine's settings and pastes them onto others.
 *
 * <ul>
 *   <li>Sneak-use on a machine copies it into the selected preset.</li>
 *   <li>In Paste mode, use on a machine pastes the selected preset onto it.</li>
 *   <li>In Area mode, two uses mark the corners of a box and show its outline and machine count;
 *       a third use, on a block or in the air, pastes onto every machine inside.</li>
 *   <li>Sneak-use in the air switches the mode; use in the air opens the preset screen, where
 *       presets are picked, renamed and cleared and the last paste can be undone.</li>
 * </ul>
 *
 * <p>The item acts before the machine gets the click (see {@link #onItemUseFirst}), so using it on
 * a machine never opens the machine's screen.
 */
public class FluxConfiguratorItem extends Item {
    /** Opens the preset screen; the client setup hands one over, and on a dedicated server it does nothing. */
    private static Consumer<InteractionHand> opener = hand -> {
    };

    public FluxConfiguratorItem(Properties properties) {
        super(properties);
    }

    public static void setOpener(Consumer<InteractionHand> open) {
        opener = open;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            if (!level.isClientSide) {
                ConfiguratorMode next = ConfiguratorStackData.mode(stack).next();
                ConfiguratorStackData.setMode(stack, next);
                if (player instanceof ServerPlayer serverPlayer) {
                    OutlineParticles.clear(serverPlayer);
                }
                player.displayClientMessage(Component.translatable("message.aeroflux.configurator_mode", modeName(next)), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // A marked area waiting for its confirming use is pasted; otherwise the preset screen opens.
        if (ConfiguratorStackData.mode(stack) == ConfiguratorMode.AREA && ConfiguratorStackData.secondCorner(stack) != null) {
            if (level instanceof ServerLevel serverLevel) {
                tell(player, ConfiguratorActions.areaPaste(stack, serverLevel, player.getUUID(), AeroFluxConfig.configuratorAreaLimit()));
                stopOutline(player);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (level.isClientSide) {
            opener.accept(hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        boolean machine = level.getBlockEntity(pos) instanceof FluxMachineBlockEntity;
        ConfiguratorMode mode = ConfiguratorStackData.mode(stack);
        if (!machine && mode != ConfiguratorMode.AREA) {
            // Not a machine: let the block and the item's air use behave as usual.
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        ConfiguratorActions.Result result;
        if (context.isSecondaryUseActive() && machine) {
            result = ConfiguratorActions.copy(stack, (FluxMachineBlockEntity) level.getBlockEntity(pos));
        } else if (mode == ConfiguratorMode.AREA) {
            boolean confirming = ConfiguratorStackData.secondCorner(stack) != null;
            result = ConfiguratorActions.areaUse(stack, serverLevel, pos,
                    player == null ? null : player.getUUID(), AeroFluxConfig.configuratorAreaLimit());
            ConfigArea pending = ConfiguratorActions.pendingArea(stack, serverLevel);
            if (player instanceof ServerPlayer serverPlayer) {
                if (pending != null) {
                    OutlineParticles.showArea(serverPlayer, serverLevel, pending, OutlineParticles.CONFIGURATOR_COLOR);
                } else if (confirming) {
                    OutlineParticles.clear(serverPlayer);
                }
            }
        } else {
            result = ConfiguratorActions.paste(stack, serverLevel, pos, player == null ? null : player.getUUID());
        }

        if (result.success()) {
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4F, 1.6F);
        }
        tell(player, result);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.aeroflux.configurator_mode", modeName(ConfiguratorStackData.mode(stack)))
                .withStyle(ChatFormatting.GRAY));
        ConfiguratorPresets presets = ConfiguratorStackData.presets(stack);
        ConfiguratorPresets.Preset current = presets.current();
        Component holds = current == null
                ? Component.translatable("gui.aeroflux.configurator.empty")
                : ConfiguratorActions.kindName(current.settings().kind());
        lines.add(Component.translatable("tooltip.aeroflux.configurator_preset",
                ConfiguratorActions.presetLabel(presets, presets.selected()), holds).withStyle(ChatFormatting.GRAY));
    }

    /** The translated name of a mode. */
    public static Component modeName(ConfiguratorMode mode) {
        return Component.translatable("configurator_mode.aeroflux." + mode.id());
    }

    private static void tell(@Nullable Player player, ConfiguratorActions.Result result) {
        if (player != null) {
            player.displayClientMessage(result.message(), true);
        }
    }

    private static void stopOutline(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            OutlineParticles.clear(serverPlayer);
        }
    }
}
