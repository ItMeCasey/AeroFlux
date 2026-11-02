package com.itmecasey.aeroflux.converter;

import com.itmecasey.aeroflux.compat.ae2.Ae2Compat;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.converter.ConverterMath;
import com.itmecasey.aeroflux.core.converter.ConverterType;
import com.itmecasey.aeroflux.fluid.InputOnlyFluidHandler;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.itmecasey.aeroflux.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * A Flux converter: burns Aero or Moon Flux into another mod's power.
 *
 * <p>Each tick it burns as much Flux as its tier allows and its output buffer has room for (see
 * {@link ConverterMath}). The FE converter then pushes its buffer into every neighbor that takes
 * energy, and anything may also pull from it. The AE converter instead feeds its buffer into the
 * ME network it is part of every tick, through {@link Ae2Compat}, so it needs AE2 to do anything. Flux only
 * goes in, from pipes or buckets, on any side.
 */
public class FluxConverterBlockEntity extends BlockEntity implements MenuProvider {
    private static final String TAG_FLUX = "flux";
    private static final String TAG_ENERGY = "energy";
    private static final String TAG_AE = "ae";
    private static final String TAG_OUTPUTS = "outputs";
    /** Every face outputs until the player switches some off. */
    private static final int ALL_FACES = 0b111111;

    private final ConverterType type;
    private final Tier tier;
    private final FluidStacksResourceHandler tank;
    private final ResourceHandler<FluidResource> inputView;
    /** The FE buffer: nothing can fill it from outside, anything may draw from it. */
    private final SimpleEnergyHandler energy;
    /** The AE buffer of the AE converter, drawn by the ME network. */
    private double aeStored;
    /** The AE2 grid node of the AE converter, when AE2 is installed; see {@link Ae2Compat}. */
    private @Nullable Object aeLink;
    /** How much Flux was burned last tick, for the screen. */
    private int lastBurn;
    /** The faces that put power out, one bit per {@link Face} index. Flux goes in on every face regardless. */
    private int outputs = ALL_FACES;

    public FluxConverterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONVERTER.get(), pos, state);
        FluxConverterBlock block = (FluxConverterBlock) state.getBlock();
        this.type = block.type();
        this.tier = block.tier();
        this.tank = new FluidStacksResourceHandler(1, ConverterMath.tankCapacity(tier)) {
            /**
             * Never less than what the tank holds. A tank can end up overfull (its block shrank in an
             * update, or a Capacity upgrade came out); it still takes nothing more, and probes such as
             * Jade, which refuse a fill above 100%, see a full tank instead of crashing.
             */
            @Override
            public long getCapacityAsLong(int index, FluidResource resource) {
                return Math.max(super.getCapacityAsLong(index, resource), getAmountAsLong(index));
            }

            @Override
            public boolean isValid(int index, FluidResource resource) {
                return ModFluids.isFlux(resource);
            }

            @Override
            protected void onContentsChanged(int index, FluidStack previousContents) {
                setChanged();
            }
        };
        this.inputView = new InputOnlyFluidHandler(tank);
        int buffer = ConverterMath.bufferCapacity(tier, ConverterType.ENERGY);
        this.energy = new SimpleEnergyHandler(buffer, 0, buffer) {
            @Override
            protected void onEnergyChanged(int previousAmount) {
                setChanged();
            }
        };
    }

    // --- Parts -------------------------------------------------------------------------------

    public ConverterType type() {
        return type;
    }

    public Tier tier() {
        return tier;
    }

    public FluidStacksResourceHandler tank() {
        return tank;
    }

    /** What pipes and buckets see: a tank that only fills. */
    public ResourceHandler<FluidResource> fluidHandler() {
        return inputView;
    }

    /**
     * The FE buffer as other mods see it from one side, or {@code null} on converters that make no
     * FE and on faces switched off. A null side (no particular face) always sees it.
     */
    public @Nullable EnergyHandler energyHandler(@Nullable Direction side) {
        return type == ConverterType.ENERGY && outputsOn(side) ? energy : null;
    }

    /** Whether power goes out through this side; a null side counts as on. */
    public boolean outputsOn(@Nullable Direction side) {
        return side == null || (outputs & (1 << side.get3DDataValue())) != 0;
    }

    /** The output faces as a bit mask, one bit per {@link Face} index. */
    public int outputMask() {
        return outputs;
    }

    /** Switches power output on or off at one face, and tells the neighbors and AE2. */
    public void toggleOutput(Face face) {
        outputs ^= 1 << face.index();
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.invalidateCapabilities(worldPosition);
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            Ae2Compat.updateConverterSides(aeLink);
        }
    }

    public int fluxAmount() {
        return tank.getAmountAsInt(0);
    }

    public @Nullable FluxType storedFlux() {
        FluidResource held = tank.getResource(0);
        return held.isEmpty() ? null : ModFluids.fluxTypeOf(held);
    }

    /** What the output buffer holds, in FE or AE. */
    public int stored() {
        return type == ConverterType.AE ? (int) aeStored : energy.getAmountAsInt();
    }

    public int bufferCapacity() {
        return ConverterMath.bufferCapacity(tier, type);
    }

    public int lastBurn() {
        return lastBurn;
    }

    /** The AE converter's grid node, or {@code null}; only {@link Ae2Compat} looks inside it. */
    public @Nullable Object aeLink() {
        return aeLink;
    }

    /** Takes up to {@code amount} AE out of the buffer, for the ME network. */
    public double extractAe(double amount, boolean simulate) {
        double taken = Math.min(amount, aeStored);
        if (!simulate && taken > 0) {
            aeStored -= taken;
            setChanged();
        }
        return taken;
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ConverterMenu(containerId, playerInventory, this);
    }

    public void writeOpenData(RegistryFriendlyByteBuf buf) {
        ConverterMenu.writeOpenData(buf, worldPosition, type, tier, getBlockState().getValue(FluxConverterBlock.FACING));
    }

    // --- Ticking -----------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, FluxConverterBlockEntity converter) {
        converter.tick(level);
    }

    private void tick(Level level) {
        lastBurn = burn();
        if (type == ConverterType.ENERGY) {
            pushEnergy(level);
        } else if (type == ConverterType.AE) {
            Ae2Compat.pushConverter(aeLink);
        }
        boolean working = lastBurn > 0;
        BlockState state = getBlockState();
        if (state.getValue(FluxConverterBlock.LIT) != working) {
            level.setBlock(worldPosition, state.setValue(FluxConverterBlock.LIT, working), Block.UPDATE_ALL);
        }
    }

    /** Burns this tick's Flux into the buffer and returns how many mB it burned. */
    private int burn() {
        FluidResource held = tank.getResource(0);
        if (held.isEmpty()) {
            return 0;
        }
        int perMb = type == ConverterType.AE ? ConverterMath.AE_PER_MB : ConverterMath.FE_PER_MB;
        long room = bufferCapacity() - (long) stored();
        int burn = ConverterMath.mbToBurn(tier, fluxAmount(), room, perMb);
        if (burn <= 0) {
            return 0;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            tank.extract(0, held, burn, transaction);
            transaction.commit();
        }
        if (type == ConverterType.AE) {
            aeStored += (double) burn * perMb;
            setChanged();
        } else {
            energy.set(energy.getAmountAsInt() + burn * perMb);
        }
        return burn;
    }

    /** Hands the FE buffer to every neighbor that takes energy, one side after the other. */
    private void pushEnergy(Level level) {
        for (Direction side : Direction.values()) {
            if (energy.getAmountAsInt() <= 0) {
                return;
            }
            if (!outputsOn(side)) {
                continue;
            }
            EnergyHandler target = level.getCapability(Capabilities.Energy.BLOCK, worldPosition.relative(side), side.getOpposite());
            if (target == null) {
                continue;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                // Offer everything; take out of the buffer only what the neighbor accepted.
                int taken = target.insert(energy.getAmountAsInt(), transaction);
                if (taken > 0) {
                    energy.extract(taken, transaction);
                }
                transaction.commit();
            }
        }
    }

    // --- AE2 ---------------------------------------------------------------------------------

    @Override
    public void onLoad() {
        super.onLoad();
        if (type == ConverterType.AE) {
            aeLink = Ae2Compat.startConverter(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        Ae2Compat.stop(aeLink);
        aeLink = null;
    }

    // --- Saving ------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output.child(TAG_FLUX));
        energy.serialize(output.child(TAG_ENERGY));
        output.putDouble(TAG_AE, aeStored);
        output.putInt(TAG_OUTPUTS, outputs);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child(TAG_FLUX).ifPresent(tank::deserialize);
        input.child(TAG_ENERGY).ifPresent(energy::deserialize);
        aeStored = input.getDoubleOr(TAG_AE, 0.0);
        outputs = input.getIntOr(TAG_OUTPUTS, ALL_FACES);
    }
}
