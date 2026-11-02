package com.itmecasey.aeroflux.block;

import com.itmecasey.aeroflux.block.entity.FluxCollectorBlockEntity;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

/**
 * A collector. It faces the sky and turns sunlight into Aero Flux by day and moonlight into
 * Moon Flux by night. One class serves all six tiers; the tier decides speed and buffer size.
 */
// Vanilla deprecates these hooks to stop other code calling them; overriding them is the intended way.
@SuppressWarnings("deprecation")
public class FluxCollectorBlock extends BaseEntityBlock {
    private final Tier tier;

    public FluxCollectorBlock(Tier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    /** The tier of this collector. */
    public Tier tier() {
        return tier;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // Block entity blocks are invisible unless they say otherwise; this one uses its JSON model.
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ModShapes.COLLECTOR;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluxCollectorBlockEntity(pos, state);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** A comparator reads how full the buffer is. */
    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof FluxCollectorBlockEntity entity ? entity.comparatorSignal() : 0;
    }

    /** While the sky feeds it, motes drift up off the dish: gold for Aero Flux, pale blue for Moon Flux. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof FluxCollectorBlockEntity collector && collector.collecting() != null) {
            FluxParticles.collector(level, pos, random, collector.collecting());
        }
    }

    /**
     * An empty hand opens the collector's screen with its sky panel. With anything else in hand the
     * item gets its own use, such as placing a block.
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof FluxCollectorBlockEntity collector) {
            NetworkHooks.openScreen(serverPlayer, collector, collector::writeOpenData);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // Only the server makes fluid; the client never needs to tick this block.
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.COLLECTOR.get(), FluxCollectorBlockEntity::serverTick);
    }
}
