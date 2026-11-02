package com.itmecasey.aeroflux.conduit;

import com.itmecasey.aeroflux.conduit.tool.ConduitToolActions;
import com.itmecasey.aeroflux.conduit.tool.ConduitToolItem;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.PipeTiers;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An item that places one kind of pipe at one tier. Used on an existing bundle it adds the pipe
 * to that bundle if the pipe's lane is free, or, if the bundle already holds this kind of pipe at
 * a lower tier, raises that pipe to this tier in place. Otherwise it places a new bundle holding
 * just this pipe, next to whatever was clicked.
 */
public class PipeItem extends Item {
    private final PipeType type;
    private final Tier tier;

    public PipeItem(PipeType type, Tier tier, Properties properties) {
        super(properties);
        this.type = type;
        this.tier = tier;
    }

    /** The kind of pipe this item places. */
    public PipeType type() {
        return type;
    }

    /** The tier of the pipe this item places; always copper for the untiered ME pipes. */
    public Tier tier() {
        return tier;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        Player player = context.getPlayer();

        // The server builds the bundle and sends it over. A guess made here on the client would
        // change the client's copy without fresh model data, and the stale look could stay drawn.
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(clickedPos) instanceof ConduitBundleBlockEntity bundle) {
            if (bundle.addPipe(type, tier)) {
                return placed(context, level, clickedPos, player);
            }
            if (isUpgradeFor(bundle)) {
                return upgrade(context, bundle, player);
            }
            // The lane is taken by a pipe this one does not upgrade; fall through and place a
            // separate bundle next to it, the way a run of pipe is laid.
        }

        BlockPlaceContext placeContext = new BlockPlaceContext(context);
        if (!placeContext.canPlace()) {
            return InteractionResult.FAIL;
        }
        BlockPos target = placeContext.getClickedPos();
        BlockState state = ModBlocks.CONDUIT_BUNDLE.get().defaultBlockState();
        if (!level.setBlock(target, state, Block.UPDATE_ALL)
                || !(level.getBlockEntity(target) instanceof ConduitBundleBlockEntity placedBundle)) {
            return InteractionResult.FAIL;
        }
        placedBundle.addPipe(type, tier);
        return placed(context, level, target, player);
    }

    /** Whether this pipe would raise the tier of the same kind of pipe already in the bundle. */
    private boolean isUpgradeFor(ConduitBundleBlockEntity bundle) {
        return PipeTiers.isTiered(type) && bundle.bundle().set().contains(type)
                && tier.ordinal() > bundle.bundle().tier(type.lane()).ordinal();
    }

    /** Raises the bundle's pipe to this item's tier, handing back the old pipe and any upgrades that no longer fit. */
    private InteractionResult upgrade(UseOnContext context, ConduitBundleBlockEntity bundle, Player player) {
        boolean creative = player != null && player.hasInfiniteMaterials();
        ConduitToolActions.Result result = ConduitToolActions.upgradeTier(bundle, type, context.getItemInHand(), !creative);
        if (player != null) {
            if (!creative) {
                for (ItemStack back : result.items()) {
                    ConduitToolItem.giveOrDrop(player, back);
                }
            }
            player.sendOverlayMessage(result.message());
        }
        context.getLevel().playSound(null, bundle.getBlockPos(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.6F, 1.2F);
        return InteractionResult.SUCCESS;
    }

    private InteractionResult placed(UseOnContext context, Level level, BlockPos pos, Player player) {
        level.playSound(player, pos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (player == null || !player.hasInfiniteMaterials()) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}
