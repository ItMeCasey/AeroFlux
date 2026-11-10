package com.itmecasey.aeroflux.compat.create;

import com.itmecasey.aeroflux.AeroFluxConfig;
import com.itmecasey.aeroflux.StorageSizes;
import com.itmecasey.aeroflux.converter.ConverterMenu;
import com.itmecasey.aeroflux.converter.ConverterView;
import com.itmecasey.aeroflux.core.FillLevel;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.converter.ConverterType;
import com.itmecasey.aeroflux.core.converter.StressMath;
import com.itmecasey.aeroflux.fluid.FluxFluidTank;
import com.itmecasey.aeroflux.fluid.InputOnlyFluidHandler;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The Stress converter's block entity: a Create generator fed by a Flux tank.
 *
 * <p>While its tank holds Flux it turns its shaft at its tier's speed and adds its stress
 * capacity to the network (see {@link StressMath}; the capacity is set per tier in the config).
 * Each tick it burns Flux in step with how much of the network's capacity is in use, carrying
 * fractions of a mB over to the next tick, so an idle shaft costs nothing. When the tank runs dry
 * the shaft stops. Flux only goes in, from pipes or buckets, on any side.
 */
public class StressConverterBlockEntity extends GeneratingKineticBlockEntity implements MenuProvider, ConverterView {
    private static final String TAG_FLUX = "flux";
    private static final String TAG_RUNNING = "running";
    private static final String TAG_CARRY = "carry";

    private final Tier tier;
    private final FluidTank tank;
    private final IFluidHandler inputView;
    private final LazyOptional<IFluidHandler> fluidCap;
    /** Whether the shaft turns: the tank had Flux the last time it was looked at. */
    private boolean running;
    /** The part of a mB burned but not yet taken from the tank. */
    private double carry;
    /** How much Flux was taken from the tank last tick, for the screen. */
    private int lastBurn;

    public StressConverterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.tier = ((StressConverterBlock) state.getBlock()).tier();
        this.tank = new FluxFluidTank(StorageSizes.converterTank(tier), stack -> ModFluids.isFlux(stack.getFluid())) {
            /** Never less than what the tank holds, as on the other converters. */
            @Override
            public int getCapacity() {
                return Math.max(super.getCapacity(), getFluidAmount());
            }

            @Override
            protected void onContentsChanged() {
                setChanged();
            }
        };
        this.inputView = new InputOnlyFluidHandler(tank);
        this.fluidCap = LazyOptional.of(() -> inputView);
    }

    // --- Create ------------------------------------------------------------------------------

    @Override
    public void initialize() {
        super.initialize();
        // As Create's own motors do: take over as the source unless something faster already drives the shaft.
        if (!hasSource() || getGeneratedSpeed() > getTheoreticalSpeed()) {
            updateGeneratedRotation();
        }
    }

    @Override
    public float getGeneratedSpeed() {
        if (!running) {
            return 0;
        }
        return convertToDirection(StressMath.rpm(tier), getBlockState().getValue(StressConverterBlock.FACING));
    }

    /** Create counts a generator's capacity per RPM; the network multiplies it back by the speed. */
    @Override
    public float calculateAddedStressCapacity() {
        float perRpm = (float) AeroFluxConfig.converterStress(tier) / StressMath.rpm(tier);
        lastCapacityProvided = perRpm;
        return perRpm;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        lastBurn = burn();
        boolean nowRunning = !tank.isEmpty();
        if (nowRunning != running) {
            running = nowRunning;
            updateGeneratedRotation();
            setChanged();
        }
        BlockState state = getBlockState();
        if (state.getValue(StressConverterBlock.LIT) != running) {
            level.setBlock(worldPosition, state.setValue(StressConverterBlock.LIT, running), Block.UPDATE_ALL);
        }
    }

    /** Takes this tick's Flux from the tank and returns how many whole mB that was. */
    private int burn() {
        if (!running || tank.isEmpty()) {
            return 0;
        }
        carry += StressMath.burn(tier, stress, capacity);
        int whole = (int) Math.min(carry, tank.getFluidAmount());
        if (whole <= 0) {
            return 0;
        }
        carry -= whole;
        tank.drain(whole, IFluidHandler.FluidAction.EXECUTE);
        return whole;
    }

    // --- Parts -------------------------------------------------------------------------------

    public FluidTank tank() {
        return tank;
    }

    /** What pipes and buckets see: a tank that only fills. */
    public IFluidHandler fluidHandler() {
        return inputView;
    }

    @Override
    public <T> @Nonnull LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidCap.invalidate();
    }

    /** Whether the shaft turns. */
    public boolean running() {
        return running;
    }

    /** The stress capacity of the whole network the shaft drives, in SU, as Create last reported it. */
    public float networkCapacity() {
        return capacity;
    }

    public int comparatorSignal() {
        return FillLevel.comparatorSignal(tank.getFluidAmount(), tank.getCapacity());
    }

    @Override
    public ConverterType type() {
        return ConverterType.STRESS;
    }

    @Override
    public Tier tier() {
        return tier;
    }

    @Override
    public Direction facing() {
        return getBlockState().getValue(StressConverterBlock.FACING);
    }

    @Override
    public int fluxAmount() {
        return tank.getFluidAmount();
    }

    @Override
    public @Nullable FluxType storedFlux() {
        return tank.isEmpty() ? null : ModFluids.fluxTypeOf(tank.getFluid().getFluid());
    }

    /** The stress the network draws right now, in SU. */
    @Override
    public int stored() {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, stress));
    }

    /** This converter's own stress capacity, in SU. */
    @Override
    public int bufferCapacity() {
        return AeroFluxConfig.converterStress(tier);
    }

    /** The capacity it supplies right now: all of it while the shaft turns, nothing when it stands. */
    @Override
    public int output() {
        return running ? bufferCapacity() : 0;
    }

    @Override
    public int lastBurn() {
        return lastBurn;
    }

    /** Only the front face has a shaft. */
    @Override
    public int outputMask() {
        return 1 << facing().get3DDataValue();
    }

    /** The shaft cannot move, so there is nothing to switch. */
    @Override
    public void toggleOutput(Face face) {
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ConverterMenu(containerId, playerInventory, this);
    }

    public void writeOpenData(FriendlyByteBuf buf) {
        ConverterMenu.writeOpenData(buf, worldPosition, ConverterType.STRESS, tier, facing());
    }

    // --- Saving ------------------------------------------------------------------------------

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.put(TAG_FLUX, tank.writeToNBT(new CompoundTag()));
        tag.putBoolean(TAG_RUNNING, running);
        tag.putDouble(TAG_CARRY, carry);
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        tank.readFromNBT(tag.getCompound(TAG_FLUX));
        running = tag.getBoolean(TAG_RUNNING);
        carry = tag.getDouble(TAG_CARRY);
    }
}
