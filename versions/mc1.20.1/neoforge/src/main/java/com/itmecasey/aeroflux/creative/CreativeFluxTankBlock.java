package com.itmecasey.aeroflux.creative;

import com.itmecasey.aeroflux.block.ModShapes;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.profile.ProfileTypes;
import com.itmecasey.aeroflux.profile.TickProfiler;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
import net.minecraftforge.fluids.FluidUtil;

/**
 * The Creative Flux Tank, for testing builds in creative: a tank of Aero, Moon or Twilight Flux
 * that never runs dry and pushes its flux into everything beside it. An empty hand switches the
 * flux; an empty bucket fills from it and a full one empties into it, the flux simply vanishing.
 * It is only in the creative tab and has no recipe.
 */
// Vanilla deprecates these hooks to stop other code calling them; overriding them is the intended way.
@SuppressWarnings("deprecation")
public class CreativeFluxTankBlock extends BaseEntityBlock {
    public CreativeFluxTankBlock(Properties properties) {
        super(properties);
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
        return new CreativeFluxTankBlockEntity(pos, state);
    }

    /**
     * Buckets fill from it or empty into it, and an empty hand switches to the next flux and says
     * which one it gives now. Any other item keeps its own use, such as placing a block.
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CreativeFluxTankBlockEntity tank) {
            FluxType flux = tank.cycleFlux();
            player.displayClientMessage(Component.translatable("message.aeroflux.creative_tank.flux",
                    Component.translatable("fluid_type.aeroflux." + flux.id() + "_flux")), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.CREATIVE_TANK.get(),
                        TickProfiler.<CreativeFluxTankBlockEntity>wrap(ProfileTypes.CREATIVE_TANK, CreativeFluxTankBlockEntity::serverTick));
    }
}
