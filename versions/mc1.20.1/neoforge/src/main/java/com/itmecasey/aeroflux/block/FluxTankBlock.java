package com.itmecasey.aeroflux.block;

import com.itmecasey.aeroflux.block.entity.FluxTankBlockEntity;
import com.itmecasey.aeroflux.core.Tier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.network.NetworkHooks;

/**
 * A storage tank for Aero Flux and Moon Flux. One class serves all six tiers; the tier decides
 * the capacity. Right-click with a bucket (or any fluid container) to fill or empty it, or with an
 * empty hand to open its screen. A broken tank keeps its flux, and a comparator reads how full it is.
 */
// Vanilla deprecates these hooks to stop other code calling them; overriding them is the intended way.
@SuppressWarnings("deprecation")
public class FluxTankBlock extends BaseEntityBlock {
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
    public RenderShape getRenderShape(BlockState state) {
        // Block entity blocks are invisible unless they say otherwise; this one uses its JSON model.
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ModShapes.TANK;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluxTankBlockEntity(pos, state);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** A comparator reads how full the tank is. */
    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof FluxTankBlockEntity tank ? tank.comparatorSignal() : 0;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        // Lets buckets and other fluid containers move fluid in and out of the tank.
        if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        // Only an empty hand opens the screen; anything else gets its own use, so another tank
        // can still be stacked on top by clicking this one.
        if (!player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof FluxTankBlockEntity tank) {
            NetworkHooks.openScreen(serverPlayer, tank, tank::writeOpenData);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
