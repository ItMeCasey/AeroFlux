package com.itmecasey.aeroflux.block;

import com.itmecasey.aeroflux.block.entity.FluxTankBlockEntity;
import com.itmecasey.aeroflux.core.Tier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidUtil;

/**
 * A storage tank for Aero Flux and Moon Flux. One class serves all six tiers; the tier decides
 * the capacity. Right-click with a bucket (or any fluid container) to fill or empty it, or with an
 * empty hand to open its screen. A broken tank keeps its flux, and a comparator reads how full it is.
 */
public class FluxTankBlock extends BaseEntityBlock {
    public static final MapCodec<FluxTankBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.xmap(name -> Tier.valueOf(name.toUpperCase(Locale.ROOT)), Tier::id)
                    .fieldOf("tier").forGetter(FluxTankBlock::tier),
            propertiesCodec()
    ).apply(instance, FluxTankBlock::new));

    private final Tier tier;

    public FluxTankBlock(Tier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    /** The tier of this tank. */
    public Tier tier() {
        return tier;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // Block entity blocks are invisible unless they say otherwise; this one uses its JSON model.
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ModShapes.TANK;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluxTankBlockEntity(pos, state);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** A comparator reads how full the tank is. */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof FluxTankBlockEntity tank ? tank.comparatorSignal() : 0;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        // Lets buckets and other fluid containers move fluid in and out of the tank.
        if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        // Only an empty hand opens the screen; anything else skips it and gets its own use, so
        // another tank can still be stacked on top by clicking this one.
        return stack.isEmpty() ? super.useItemOn(stack, state, level, pos, player, hand, hit)
                : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    /** An empty hand opens the tank's screen; anything a bucket does is handled above first. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof FluxTankBlockEntity tank) {
            serverPlayer.openMenu(tank, tank::writeOpenData);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
