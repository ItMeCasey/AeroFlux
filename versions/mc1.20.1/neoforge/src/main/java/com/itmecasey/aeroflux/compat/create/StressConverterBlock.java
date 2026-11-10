package com.itmecasey.aeroflux.compat.create;

import com.itmecasey.aeroflux.core.Tier;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.network.NetworkHooks;

/**
 * The Flux Stress converter: burns Aero or Moon Flux into rotation for Create. It is a Create
 * kinetic block with one shaft, out of its front, so it can face any of the six directions; it
 * lights up while it turns, opens the converter screen on use and takes Flux from a bucket.
 */
public class StressConverterBlock extends DirectionalKineticBlock implements IBE<StressConverterBlockEntity> {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private final Tier tier;

    public StressConverterBlock(Tier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        registerDefaultState(defaultBlockState().setValue(LIT, false));
    }

    public Tier tier() {
        return tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }

    /** The only shaft is the one out of the front. */
    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public Class<StressConverterBlockEntity> getBlockEntityClass() {
        return StressConverterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends StressConverterBlockEntity> getBlockEntityType() {
        return CreateContent.STRESS_CONVERTER.get();
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** A comparator reads how full the Flux tank is, as on the other converters. */
    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof StressConverterBlockEntity converter ? converter.comparatorSignal() : 0;
    }

    /** A bucket of Flux fills the converter; any other use opens the screen. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, null)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof StressConverterBlockEntity converter) {
            NetworkHooks.openScreen(serverPlayer, converter, converter::writeOpenData);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
