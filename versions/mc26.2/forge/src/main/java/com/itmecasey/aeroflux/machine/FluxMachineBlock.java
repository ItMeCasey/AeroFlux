package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.block.FluxParticles;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.profile.ProfileTypes;
import com.itmecasey.aeroflux.profile.TickProfiler;
import com.itmecasey.aeroflux.fluid.Buckets;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The block of every Flux machine: one class for all eight machines and all six tiers. The
 * machine type decides what the block entity does and the tier decides how fast and how big;
 * the block itself only faces a direction, opens the screen and hands out its contents when it
 * breaks.
 */
public class FluxMachineBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** True while the machine is working; the model then shows its animated front. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** How much light a working machine gives off, the same as a lit furnace. */
    public static final int LIT_LIGHT = 13;

    public static final MapCodec<FluxMachineBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.xmap(name -> Tier.valueOf(name.toUpperCase(Locale.ROOT)), Tier::id)
                    .fieldOf("tier").forGetter(FluxMachineBlock::tier),
            Codec.STRING.xmap(id -> MachineType.valueOf(id.toUpperCase(Locale.ROOT)), MachineType::id)
                    .fieldOf("machine").forGetter(FluxMachineBlock::machine),
            propertiesCodec()
    ).apply(instance, FluxMachineBlock::new));

    /** The machines' client tick; see {@link #setClientTicker}. Stays null on a server. */
    @Nullable
    private static BlockEntityTicker<FluxMachineBlockEntity> clientTicker;

    private final Tier tier;
    private final MachineType machine;

    public FluxMachineBlock(Tier tier, MachineType machine, Properties properties) {
        super(properties);
        this.tier = tier;
        this.machine = machine;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    public Tier tier() {
        return tier;
    }

    public MachineType machine() {
        return machine;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** A comparator reads how full the item slots are. */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof FluxMachineBlockEntity entity ? entity.comparatorSignal() : 0;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    /** The front faces the player who placed it. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** Records who placed the machine, so the claim checks of the machines that reach into the world are made as that player. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, by, itemStack);
        if (!level.isClientSide() && by instanceof Player player && level.getBlockEntity(pos) instanceof FluxMachineBlockEntity machine) {
            machine.setOwner(player.getUUID());
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.getRotation(state.getValue(FACING)).rotate(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // Block entity blocks are invisible unless they say otherwise; these use their JSON models.
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return MachineFactory.create(pos, state);
    }

    /** Right-click with a bucket (or any fluid container) moves Flux in or out, whatever the face settings say. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (Buckets.interact(player, hand, level, pos, null)) {
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof FluxMachineBlockEntity entity) {
            serverPlayer.openMenu(entity, entity::writeOpenData);
        }
        return InteractionResult.SUCCESS;
    }

    /** Tells the machine about its redstone signal whenever a neighbor changes, which is when that signal can change. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
                                   @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbor, orientation, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FluxMachineBlockEntity entity) {
            entity.readSignal(level);
        }
    }

    /**
     * Sets what a machine does on the client each tick: start its running sound. The client setup
     * sets it, so the server never loads any sound classes.
     */
    public static void setClientTicker(BlockEntityTicker<FluxMachineBlockEntity> ticker) {
        clientTicker = ticker;
    }

    /** Only the server works; the client only plays the running sound. */
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return clientTicker == null ? null : createTickerHelper(type, ModBlockEntities.MACHINE.get(), clientTicker);
        }
        return createTickerHelper(type, ModBlockEntities.MACHINE.get(), TickProfiler.<FluxMachineBlockEntity>wrap(
                blockState -> ProfileTypes.machine(machine.id()), FluxMachineBlockEntity::serverTick));
    }

    /** A working machine gives off a mote now and then, in the color of the flux it runs on. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) {
            FluxType flux = machine.acceptedFlux().contains(FluxType.AERO) ? FluxType.AERO : FluxType.MOON;
            FluxParticles.machine(level, pos, random, flux);
        }
    }
}
