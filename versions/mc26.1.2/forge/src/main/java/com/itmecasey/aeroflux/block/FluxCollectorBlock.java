package com.itmecasey.aeroflux.block;

import com.itmecasey.aeroflux.block.entity.FluxCollectorBlockEntity;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A collector. It faces the sky and turns sunlight into Aero Flux by day and moonlight into
 * Moon Flux by night. One class serves all six tiers; the tier decides speed and buffer size.
 */
public class FluxCollectorBlock extends BaseEntityBlock {
    public static final MapCodec<FluxCollectorBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.xmap(name -> Tier.valueOf(name.toUpperCase(Locale.ROOT)), Tier::id)
                    .fieldOf("tier").forGetter(FluxCollectorBlock::tier),
            propertiesCodec()
    ).apply(instance, FluxCollectorBlock::new));

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
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** A comparator reads how full the buffer is. */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof FluxCollectorBlockEntity entity ? entity.comparatorSignal() : 0;
    }

    /** Only an empty hand opens the screen; with anything else in hand the item gets its own use, such as placing a block. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        return stack.isEmpty() ? super.useItemOn(stack, state, level, pos, player, hand, hit) : InteractionResult.PASS;
    }

    /** An empty hand opens the collector's screen with its sky panel. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof FluxCollectorBlockEntity collector) {
            serverPlayer.openMenu(collector, collector::writeOpenData);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ModShapes.COLLECTOR;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluxCollectorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // Only the server makes fluid; the client never needs to tick this block.
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.COLLECTOR.get(), FluxCollectorBlockEntity::serverTick);
    }
}
