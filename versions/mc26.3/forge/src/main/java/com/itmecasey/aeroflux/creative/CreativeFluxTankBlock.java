package com.itmecasey.aeroflux.creative;

import com.itmecasey.aeroflux.block.ModShapes;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.profile.ProfileTypes;
import com.itmecasey.aeroflux.profile.TickProfiler;
import com.itmecasey.aeroflux.fluid.Buckets;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
 * The Creative Flux Tank, for testing builds in creative: a tank of Aero, Moon or Twilight Flux
 * that never runs dry and pushes its flux into everything beside it. An empty hand switches the
 * flux; an empty bucket fills from it and a full one empties into it, the flux simply vanishing.
 * It is only in the creative tab and has no recipe.
 */
public class CreativeFluxTankBlock extends BaseEntityBlock {
    public CreativeFluxTankBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ModShapes.TANK;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreativeFluxTankBlockEntity(pos, state);
    }

    /** Buckets fill from it or empty into it; any other item keeps its own use, such as placing a block. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (Buckets.interact(player, hand, level, pos, hit.getDirection())) {
            return InteractionResult.SUCCESS;
        }
        return stack.isEmpty() ? super.useItemOn(stack, state, level, pos, player, hand, hit) : InteractionResult.PASS;
    }

    /** An empty hand switches to the next flux and says which one it gives now. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CreativeFluxTankBlockEntity tank) {
            FluxType flux = tank.cycleFlux();
            player.sendOverlayMessage(Component.translatable("message.aeroflux.creative_tank.flux",
                    Component.translatable("fluid_type.aeroflux." + flux.id() + "_flux")));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.CREATIVE_TANK.get(),
                        TickProfiler.<CreativeFluxTankBlockEntity>wrap(ProfileTypes.CREATIVE_TANK, CreativeFluxTankBlockEntity::serverTick));
    }
}
