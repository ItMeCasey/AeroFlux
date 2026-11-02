package com.itmecasey.aeroflux.conduit.tool;

import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.core.conduit.ConduitLayout;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.conduit.ToolMode;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Conduit Tool. Sneak-use in the air cycles its mode; using it on a conduit bundle does what
 * the current mode says, to the pipe nearest the crosshair (the same targeting as the wrench).
 * The mode and anything the tool remembers live on the stack, see {@link ToolStackData}; the
 * actions themselves are in {@link ConduitToolActions}.
 */
public class ConduitToolItem extends Item {
    public ConduitToolItem(Properties properties) {
        super(properties);
    }

    /** Sneak-use in the air moves to the next mode. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            ToolMode next = ToolStackData.mode(stack).next();
            if (next == ToolMode.REPLACE_TIER) {
                // Retired: a higher-tier pipe used on the bundle upgrades it in place instead.
                next = next.next();
            }
            ToolStackData.setMode(stack, next);
            player.sendOverlayMessage(Component.translatable("message.aeroflux.tool_mode", modeName(next)));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity entity)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        // Where the crosshair hit, in pixels inside the block.
        Vec3 local = context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ()).scale(16.0);
        PipeType pipe = ConduitLayout.of(entity.bundle()).nearestPipe(local.x, local.y, local.z);
        if (pipe == null) {
            return InteractionResult.PASS;
        }

        Player player = context.getPlayer();
        ItemStack tool = context.getItemInHand();
        ToolMode mode = ToolStackData.mode(tool);
        boolean sneaking = context.isSecondaryUseActive();
        ConduitToolActions.Result result = switch (mode) {
            case EDIT -> ConduitToolActions.edit(entity, pipe, Face.byIndex(context.getClickedFace().get3DDataValue()));
            case REMOVE -> ConduitToolActions.remove(level, pos, entity, pipe, sneaking);
            case REMOVE_RUN -> ConduitToolActions.removeRun(serverLevel, tool, pos, pipe);
            // Tiers are raised by using a higher-tier pipe on the bundle now; the tool skips this mode.
            case REPLACE_TIER -> ConduitToolActions.inspect(serverLevel, pos, pipe);
            case COPY -> ConduitToolActions.copy(tool, entity);
            case PASTE -> ConduitToolActions.paste(tool, entity);
            case INSPECT -> ConduitToolActions.inspect(serverLevel, pos, pipe);
        };

        if (player != null) {
            if (!isCreative(player)) {
                for (ItemStack back : result.items()) {
                    giveOrDrop(player, back);
                }
            }
            // The inspection is a longer message that should stay readable, so it goes to chat.
            if (mode == ToolMode.INSPECT) {
                player.sendSystemMessage(result.message());
            } else {
                player.sendOverlayMessage(result.message());
            }
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Shows the current mode. Vanilla marked this hook deprecated in favor of tooltip data
     * components, but the mode lives in {@link ToolStackData}, and the hook is still called for
     * every item.
     */
    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.translatable("tooltip.aeroflux.tool_mode", modeName(ToolStackData.mode(stack))));
    }

    /** The translated name of a mode. */
    public static Component modeName(ToolMode mode) {
        return Component.translatable("tool_mode.aeroflux." + mode.name().toLowerCase(Locale.ROOT));
    }

    private static boolean isCreative(Player player) {
        return player != null && player.getAbilities().instabuild;
    }

    /** Puts the stack in the inventory, dropping whatever does not fit at the player's feet. */
    public static void giveOrDrop(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false);
        }
    }
}
