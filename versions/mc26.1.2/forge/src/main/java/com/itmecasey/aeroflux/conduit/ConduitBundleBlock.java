package com.itmecasey.aeroflux.conduit;

import com.itmecasey.aeroflux.conduit.transfer.TransferEngine;
import com.itmecasey.aeroflux.core.conduit.BundleState;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.itmecasey.aeroflux.registry.ModItems;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The one block that carries every kind of pipe. A single pipe is just a bundle holding one
 * pipe. What is inside, and how it connects, lives in the block entity; the block only decides
 * how it is drawn, how big it is, what it drops and what redstone signal it gives off.
 */
public class ConduitBundleBlock extends BaseEntityBlock {
    public static final MapCodec<ConduitBundleBlock> CODEC = simpleCodec(ConduitBundleBlock::new);

    public ConduitBundleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // Block entity blocks are invisible unless they say otherwise; this one uses its dynamic model.
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConduitBundleBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity bundle
                ? BundleShapes.of(bundle.bundle())
                : Shapes.block();
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   @Nullable Orientation orientation, boolean movedByPiston) {
        if (level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity bundle) {
            bundle.refreshConnections();
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity bundle) {
            bundle.refreshConnections();
        }
    }

    /** Only the server moves things, so only the server ticks. */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.CONDUIT_BUNDLE.get(), ConduitBundleBlockEntity::serverTick);
    }

    // Redstone pipes can power the blocks they export into. Whether a given face does is a
    // question for the block entity; the block only passes the question on.

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return outputToward(level, pos, direction);
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return outputToward(level, pos, direction);
    }

    /**
     * The signal given to a block that asks from {@code direction}. The direction points from the
     * asking block toward this one, so the face that touches the asker is the opposite side.
     */
    private static int outputToward(BlockGetter level, BlockPos pos, Direction direction) {
        if (TransferEngine.isSamplingRedstone()) {
            return 0;
        }
        return level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity bundle
                ? bundle.redstoneOutput(direction.getOpposite())
                : 0;
    }

    /** A bundle gives back one pipe item of the right tier for every pipe it held, plus its upgrades. */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new ArrayList<>();
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof ConduitBundleBlockEntity entity) {
            BundleState bundle = entity.bundle();
            for (PipeType type : bundle.set().types()) {
                int lane = type.lane();
                drops.add(new ItemStack(ModItems.pipe(type, bundle.tier(lane)).get()));
                if (bundle.speedUpgrades(lane) > 0) {
                    drops.add(new ItemStack(ModItems.SPEED_UPGRADE.get(), bundle.speedUpgrades(lane)));
                }
                if (bundle.capacityUpgrades(lane) > 0) {
                    drops.add(new ItemStack(ModItems.CAPACITY_UPGRADE.get(), bundle.capacityUpgrades(lane)));
                }
            }
        }
        return drops;
    }
}
