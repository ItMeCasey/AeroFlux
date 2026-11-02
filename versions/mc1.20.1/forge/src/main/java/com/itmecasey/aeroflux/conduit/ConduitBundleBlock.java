package com.itmecasey.aeroflux.conduit;

import com.itmecasey.aeroflux.conduit.config.PipeConfigActions;
import com.itmecasey.aeroflux.conduit.config.PipeConfigMenu;
import com.itmecasey.aeroflux.conduit.transfer.TransferEngine;
import com.itmecasey.aeroflux.core.conduit.BundleState;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.itmecasey.aeroflux.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The one block that carries every kind of pipe. A single pipe is just a bundle holding one
 * pipe. What is inside, and how it connects, lives in the block entity; the block only decides
 * how it is drawn, how big it is, what it drops and what redstone signal it gives off.
 */
// Vanilla deprecates these hooks to stop other code calling them; overriding them is the intended way.
@SuppressWarnings("deprecation")
public class ConduitBundleBlock extends BaseEntityBlock {
    public ConduitBundleBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // Block entity blocks are invisible unless they say otherwise; this one uses its dynamic model.
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConduitBundleBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity bundle
                ? BundleShapes.of(bundle.bundle())
                : Shapes.block();
    }

    /**
     * Right-clicking a pipe connector (the arm of one pipe at a face) opens that pipe's screen.
     * Players holding the wrench, the Conduit Tool, a pipe or an upgrade in either hand keep
     * the click those items already have, so this passes and lets the item act. A click that
     * is not on a connector passes too, so ordinary building next to a bundle still works.
     * Sneaking with something in hand never gets here: the game skips the block then.
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        // This version asks once per hand, so both hands are looked at here.
        if (!(level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity entity)
                || PipeConfigActions.keepsOwnClick(player.getMainHandItem())
                || PipeConfigActions.keepsOwnClick(player.getOffhandItem())) {
            return InteractionResult.PASS;
        }

        // Where the crosshair hit, in pixels inside the block, as the wrench works it out.
        Vec3 local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ()).scale(16.0);
        PipeConfigActions.Target target = PipeConfigActions.resolveTarget(entity.bundle(), local.x, local.y, local.z);
        if (target == null) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            PipeConfigMenu.open(serverPlayer, entity, target.pipe(), target.face());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                BlockPos neighborPos, boolean movedByPiston) {
        if (level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity bundle) {
            bundle.refreshConnections();
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity bundle) {
            bundle.refreshConnections();
        }
    }

    /** Only the server moves things, so only the server ticks. */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.CONDUIT_BUNDLE.get(), ConduitBundleBlockEntity::serverTick);
    }

    // Redstone pipes can power the blocks they export into. Whether a given face does is a
    // question for the block entity; the block only passes the question on.

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return outputToward(level, pos, direction);
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
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
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
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
