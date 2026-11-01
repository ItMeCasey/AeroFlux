package com.itmecasey.aeroflux.conduit;

import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An item that places one kind of pipe. Used on an existing bundle it adds the pipe to that
 * bundle if the pipe's lane is free; used anywhere else it places a new bundle holding just
 * this pipe.
 */
public class PipeItem extends Item {
    private final PipeType type;

    public PipeItem(PipeType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    /** The kind of pipe this item places. */
    public PipeType type() {
        return type;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        Player player = context.getPlayer();

        if (level.getBlockEntity(clickedPos) instanceof ConduitBundleBlockEntity bundle) {
            if (bundle.addPipe(type)) {
                return placed(context, level, clickedPos, player);
            }
            // The lane is taken; fall through and place a separate bundle next to it.
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
        placedBundle.addPipe(type);
        return placed(context, level, target, player);
    }

    private InteractionResult placed(UseOnContext context, Level level, BlockPos pos, Player player) {
        level.playSound(player, pos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (player == null || !player.hasInfiniteMaterials()) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}
