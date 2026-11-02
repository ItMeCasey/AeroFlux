package com.itmecasey.aeroflux.chunkloader;

import com.itmecasey.aeroflux.block.entity.FluxDimensions;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

/**
 * The Flux Chunk Loader block, one class for all six tiers; the tier sets the area. Right-click
 * opens its screen, right-click with a Flux bucket fills it. The player who places it becomes
 * its owner, which is what the per-player cap counts.
 */
public class FluxChunkLoaderBlock extends BaseEntityBlock {
    public static final MapCodec<FluxChunkLoaderBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.xmap(name -> Tier.valueOf(name.toUpperCase(Locale.ROOT)), Tier::id)
                    .fieldOf("tier").forGetter(FluxChunkLoaderBlock::tier),
            propertiesCodec()
    ).apply(instance, FluxChunkLoaderBlock::new));

    private final Tier tier;

    public FluxChunkLoaderBlock(Tier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public Tier tier() {
        return tier;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** A comparator reads how full the Flux tank is. */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof FluxChunkLoaderBlockEntity entity ? entity.comparatorSignal() : 0;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluxChunkLoaderBlockEntity(pos, state);
    }

    /** Records the owner, and tells a player straight away when this dimension does not allow loaders. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, by, itemStack);
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof FluxChunkLoaderBlockEntity loader)) {
            return;
        }
        loader.setOwner(by instanceof Player player ? player.getUUID() : null);
        if (by instanceof ServerPlayer player && !FluxDimensions.rulesFor(level).chunkLoaderAllowed()) {
            player.displayClientMessage(Component.translatable("message.aeroflux.chunk_loader_dimension"), true);
        }
    }

    /** A bucket (or any fluid container) fills the loader; it can never be emptied this way. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, null)) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof FluxChunkLoaderBlockEntity loader) {
            serverPlayer.openMenu(loader, loader::writeOpenData);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Breaking the loader lets its chunks go at once. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof FluxChunkLoaderBlockEntity loader) {
            loader.broken(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Only the server loads chunks, so only the server ticks. */
    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.CHUNK_LOADER.get(), FluxChunkLoaderBlockEntity::serverTick);
    }
}
