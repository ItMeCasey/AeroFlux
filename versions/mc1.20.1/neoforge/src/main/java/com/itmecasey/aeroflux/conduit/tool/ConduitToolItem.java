package com.itmecasey.aeroflux.conduit.tool;

import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.core.conduit.ConduitLayout;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.conduit.ToolMode;
import com.itmecasey.aeroflux.world.OutlineParticles;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Conduit Tool. Sneak-use in the air cycles its mode; using it on a conduit bundle does what
 * the current mode says, to the pipe nearest the crosshair (the same targeting as the wrench).
 * In paste mode a sneak-use marks the ends of a run to paste along instead, and in inspect mode
 * the whole network is outlined for the player for a few seconds.
 * The mode and anything the tool remembers live on the stack, see {@link ToolStackData}; the
 * actions themselves are in {@link ConduitToolActions}.
 */
public class ConduitToolItem extends Item {
    public ConduitToolItem(Properties properties) {
        super(properties);
    }

    /** Sneak-use in the air moves to the next mode. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive()) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            ToolMode next = ToolStackData.mode(stack).next();
            if (next == ToolMode.REPLACE_TIER) {
                // Retired: a higher-tier pipe used on the bundle upgrades it in place instead.
                next = next.next();
            }
            ToolStackData.setMode(stack, next);
            player.displayClientMessage(Component.translatable("message.aeroflux.tool_mode", modeName(next)), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (ToolStackData.mode(context.getItemInHand()) == ToolMode.PASTE && context.isSecondaryUseActive()) {
            return pasteRun(context, level, pos);
        }
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
            case PASTE -> ConduitToolActions.paste(tool, entity, ConduitToolActions.supplyOf(player));
            case INSPECT -> ConduitToolActions.inspect(serverLevel, pos, pipe);
        };

        if (mode == ToolMode.INSPECT && player instanceof ServerPlayer serverPlayer) {
            List<BlockPos> members = ConduitToolActions.networkMembers(serverLevel, pos, pipe);
            if (!members.isEmpty()) {
                OutlineParticles.showBlocks(serverPlayer, serverLevel, members, OutlineParticles.INSPECT_COLOR);
            }
        }
        if (player != null) {
            if (!isCreative(player)) {
                for (ItemStack back : result.items()) {
                    giveOrDrop(player, back);
                }
            }
            // The inspection is a longer message that should stay readable, so it goes to chat.
            player.displayClientMessage(result.message(), mode != ToolMode.INSPECT);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Sneak-use in paste mode: marks the start of a run, then pastes along it. A click on a bundle
     * means that bundle; a click on any other block means the spot in front of the clicked face,
     * where a new bundle would go.
     */
    private static InteractionResult pasteRun(UseOnContext context, Level level, BlockPos clicked) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = level.getBlockEntity(clicked) instanceof ConduitBundleBlockEntity
                ? clicked
                : clicked.relative(context.getClickedFace());
        Player player = context.getPlayer();
        ConduitToolActions.Result result = ConduitToolActions.pasteRun(serverLevel, context.getItemInHand(), pos,
                ConduitToolActions.supplyOf(player));
        if (player != null) {
            player.displayClientMessage(result.message(), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.aeroflux.tool_mode", modeName(ToolStackData.mode(stack))));
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
