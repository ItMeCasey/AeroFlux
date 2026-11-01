package com.itmecasey.aeroflux.conduit;

import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The one block that carries every kind of pipe. A single pipe is just a bundle holding one
 * pipe. What is inside, and how it connects, lives in the block entity; the block only decides
 * how it is drawn, how big it is and what it drops.
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

    /** A bundle gives back one pipe item for every pipe it held. */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new ArrayList<>();
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof ConduitBundleBlockEntity bundle) {
            for (PipeType type : bundle.bundle().set().types()) {
                drops.add(new ItemStack(ModItems.pipe(type).get()));
            }
        }
        return drops;
    }
}
