package com.itmecasey.aeroflux.lamp;

import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.profile.ProfileTypes;
import com.itmecasey.aeroflux.profile.TickProfiler;
import com.itmecasey.aeroflux.fluid.Buckets;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A Sun or Moon Lamp: a caged crystal on a post, with no screen. It takes a little Flux from a pipe,
 * a tank next to it or a bucket, and while it has some it shines at full brightness, keeps monsters
 * from spawning around it and helps its plants grow (see {@link FluxLampBlockEntity}). Out of Flux it
 * goes dark.
 */
public class FluxLampBlock extends BaseEntityBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private final LampKind kind;
    private final Tier tier;

    public FluxLampBlock(LampKind kind, Tier tier, Properties properties) {
        super(properties);
        this.kind = kind;
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    public LampKind kind() {
        return kind;
    }

    public Tier tier() {
        return tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluxLampBlockEntity(pos, state);
    }

    /** A bucket (or any fluid container) fills the lamp. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (Buckets.interact(player, hand, level, pos, null)) {
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.LAMP.get(),
                TickProfiler.<FluxLampBlockEntity>wrap(ProfileTypes.LAMP, FluxLampBlockEntity::serverTick));
    }
}
