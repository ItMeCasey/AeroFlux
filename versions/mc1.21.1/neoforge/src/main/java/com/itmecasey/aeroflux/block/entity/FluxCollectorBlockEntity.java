package com.itmecasey.aeroflux.block.entity;

import com.itmecasey.aeroflux.AeroFluxConfig;
import com.itmecasey.aeroflux.StorageSizes;
import com.itmecasey.aeroflux.block.FluxCollectorBlock;
import com.itmecasey.aeroflux.core.CollectionMath;
import com.itmecasey.aeroflux.core.DimensionRules;
import com.itmecasey.aeroflux.core.FillLevel;
import com.itmecasey.aeroflux.core.FluxAccumulator;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.fluid.FluxFluidTank;
import com.itmecasey.aeroflux.fluid.OutputOnlyFluidHandler;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.itmecasey.aeroflux.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * The working part of a {@link FluxCollectorBlock}.
 *
 * <p>Each tick on the server it works out how much flux the sky gives right now, adds it to an
 * internal buffer, and every few ticks pushes the buffer into neighboring tanks and machines.
 * The buffer holds one kind of flux at a time, so if night falls while Aero Flux is still in
 * it, Moon Flux waits until the buffer has been emptied.
 */
public class FluxCollectorBlockEntity extends BlockEntity {
    /** How often the open-sky check runs; scanning the column above is cheap but not free. */
    private static final int SKY_CHECK_INTERVAL = 20;
    /** How often the buffer is pushed to neighbors, in ticks. */
    private static final int PUSH_INTERVAL = 4;
    /** The most fluid moved to neighbors in one push, in mB. */
    private static final int PUSH_LIMIT = 4_000;

    private final Tier tier;
    private final FluidTank buffer;
    private final FluxAccumulator accumulator = new FluxAccumulator();
    private boolean skyVisible;

    public FluxCollectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COLLECTOR.get(), pos, state);
        this.tier = ((FluxCollectorBlock) state.getBlock()).tier();
        this.buffer = new FluxFluidTank(StorageSizes.collector(tier), stack -> ModFluids.isFlux(stack.getFluid())) {
            /**
             * Never less than what the tank holds. A tank can end up overfull (its block shrank in an
             * update); it still takes nothing more, and probes such as Jade, which refuse a fill above
             * 100%, see a full tank instead of crashing.
             */
            @Override
            public int getCapacity() {
                return Math.max(super.getCapacity(), getFluidAmount());
            }

            @Override
            protected void onContentsChanged() {
                setChanged();
            }
        };
    }

    /** The internal buffer; neighbors may only drain it, never fill it. */
    /** What pipes and buckets see: the buffer, drain only. */
    public IFluidHandler outputView() {
        return new OutputOnlyFluidHandler(buffer);
    }

    public FluidTank buffer() {
        return buffer;
    }

    /** The comparator signal: how full the buffer is, from 0 to 15. */
    public int comparatorSignal() {
        return FillLevel.comparatorSignal(buffer.getFluidAmount(), buffer.getCapacity());
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FluxCollectorBlockEntity collector) {
        collector.tick(level, pos);
    }

    private void tick(Level level, BlockPos pos) {
        long time = level.getGameTime();
        if (time % SKY_CHECK_INTERVAL == 0) {
            skyVisible = level.canSeeSky(pos.above());
        }

        produce(level);

        if (time % PUSH_INTERVAL == 0 && !buffer.isEmpty()) {
            pushToNeighbors(level, pos);
        }
    }

    /** Adds this tick's flux, if the sky is open and the time of day suits the flux in the buffer. */
    private void produce(Level level) {
        DimensionRules rules = FluxDimensions.rulesFor(level);
        if (rules.requiresSky() && !skyVisible) {
            return;
        }

        long dimensionTime = level.getDayTime();
        long overworldTime = level.getServer().overworld().getDayTime();
        boolean raining = level.isRaining();
        boolean thundering = level.isThundering();
        int moonPhase = level.getMoonPhase();

        for (FluxType type : FluxType.values()) {
            if (!type.collected()) {
                continue;
            }
            Fluid fluid = ModFluids.fluidOf(type);
            if (!buffer.isEmpty() && !buffer.getFluid().getFluid().isSame(fluid)) {
                continue;
            }
            double perTick = CollectionMath.outputPerTick(AeroFluxConfig.collectorRate(tier), type, rules,
                    dimensionTime, overworldTime, raining, thundering, moonPhase);
            if (perTick <= 0.0) {
                continue;
            }
            int whole = accumulator.add(perTick);
            if (whole > 0) {
                int stored = buffer.fill(new FluidStack(fluid, whole), IFluidHandler.FluidAction.EXECUTE);
                if (stored < whole) {
                    // The buffer is full, so the leftover is lost; do not stockpile a fraction.
                    accumulator.reset();
                }
            }
            return;
        }
    }

    private void pushToNeighbors(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            IFluidHandler neighbor = level.getCapability(Capabilities.FluidHandler.BLOCK,
                    pos.relative(direction), direction.getOpposite());
            if (neighbor == null) {
                continue;
            }
            FluidUtil.tryFluidTransfer(neighbor, buffer, Math.min(buffer.getFluidAmount(), PUSH_LIMIT), true);
            if (buffer.isEmpty()) {
                return;
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        buffer.writeToNBT(registries, tag);
        tag.putDouble("carry", accumulator.carry());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        buffer.readFromNBT(registries, tag);
        accumulator.setCarry(tag.getDouble("carry"));
    }
}
