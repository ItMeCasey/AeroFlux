package com.itmecasey.aeroflux.block.entity;

import com.itmecasey.aeroflux.AeroFluxConfig;
import com.itmecasey.aeroflux.block.FluxCollectorBlock;
import com.itmecasey.aeroflux.core.CollectionMath;
import com.itmecasey.aeroflux.core.DimensionRules;
import com.itmecasey.aeroflux.core.FluxAccumulator;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.fluid.OutputOnlyFluidHandler;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.itmecasey.aeroflux.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

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
    private final FluidStacksResourceHandler buffer;
    private final ResourceHandler<FluidResource> outputView;
    private final FluxAccumulator accumulator = new FluxAccumulator();
    private boolean skyVisible;

    public FluxCollectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COLLECTOR.get(), pos, state);
        this.tier = ((FluxCollectorBlock) state.getBlock()).tier();
        // A single slot, so the buffer can only ever hold one kind of flux.
        this.buffer = new FluidStacksResourceHandler(1, AeroFluxConfig.collectorBuffer(tier)) {
            @Override
            public boolean isValid(int index, FluidResource resource) {
                return ModFluids.isFlux(resource);
            }

            @Override
            protected void onContentsChanged(int index, FluidStack previousContents) {
                setChanged();
            }
        };
        this.outputView = new OutputOnlyFluidHandler(buffer);
    }

    /** The buffer as other blocks see it: they may drain it, never fill it. */
    public ResourceHandler<FluidResource> outputView() {
        return outputView;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FluxCollectorBlockEntity collector) {
        collector.tick(level, pos);
    }

    private void tick(Level level, BlockPos pos) {
        long time = level.getGameTime();
        if (time % SKY_CHECK_INTERVAL == 0) {
            skyVisible = level.canSeeSky(pos.above());
        }

        produce(level, pos);

        if (time % PUSH_INTERVAL == 0 && !ResourceHandlerUtil.isEmpty(buffer)) {
            pushToNeighbors(level, pos);
        }
    }

    /** Adds this tick's flux, if the sky is open and the time of day suits the flux in the buffer. */
    private void produce(Level level, BlockPos pos) {
        DimensionRules rules = FluxDimensions.rulesFor(level);
        if (rules.requiresSky() && !skyVisible) {
            return;
        }

        long dimensionTime = level.getDefaultClockTime();
        long overworldTime = level.getOverworldClockTime();
        boolean raining = level.isRaining();
        boolean thundering = level.isThundering();
        int moonPhase = level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, pos).index();

        FluidResource held = buffer.getResource(0);
        for (FluxType type : FluxType.values()) {
            Fluid fluid = type == FluxType.AERO ? ModFluids.AERO_FLUX.get() : ModFluids.MOON_FLUX.get();
            if (!held.isEmpty() && !held.getFluid().isSame(fluid)) {
                continue;
            }
            double perTick = CollectionMath.outputPerTick(AeroFluxConfig.collectorRate(tier), type, rules,
                    dimensionTime, overworldTime, raining, thundering, moonPhase);
            if (perTick <= 0.0) {
                continue;
            }
            int whole = accumulator.add(perTick);
            if (whole > 0) {
                int stored;
                try (Transaction transaction = Transaction.openRoot()) {
                    stored = buffer.insert(FluidResource.of(fluid), whole, transaction);
                    transaction.commit();
                }
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
            ResourceHandler<FluidResource> neighbor = level.getCapability(Capabilities.Fluid.BLOCK,
                    pos.relative(direction), direction.getOpposite());
            if (neighbor == null) {
                continue;
            }
            ResourceHandlerUtil.move(buffer, neighbor, resource -> true, PUSH_LIMIT, null);
            if (ResourceHandlerUtil.isEmpty(buffer)) {
                return;
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        buffer.serialize(output.child("buffer"));
        output.putDouble("carry", accumulator.carry());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("buffer").ifPresent(buffer::deserialize);
        accumulator.setCarry(input.getDoubleOr("carry", 0.0));
    }
}
