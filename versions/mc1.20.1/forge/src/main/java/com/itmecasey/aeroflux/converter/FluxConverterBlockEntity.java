package com.itmecasey.aeroflux.converter;

import com.itmecasey.aeroflux.StorageSizes;
import com.itmecasey.aeroflux.compat.ae2.Ae2Compat;
import com.itmecasey.aeroflux.conduit.NeighborCapabilities;
import com.itmecasey.aeroflux.core.FillLevel;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.converter.ConverterMath;
import com.itmecasey.aeroflux.core.converter.ConverterType;
import com.itmecasey.aeroflux.core.save.SaveSchema;
import com.itmecasey.aeroflux.fluid.FluxFluidTank;
import com.itmecasey.aeroflux.fluid.InputOnlyFluidHandler;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.save.SaveVersion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A Flux converter: burns Aero or Moon Flux into another mod's power.
 *
 * <p>Each tick it burns as much Flux as its tier allows and its output buffer has room for (see
 * {@link ConverterMath}). The FE converter then pushes its buffer into every neighbor that takes
 * energy, and anything may also pull from it. The AE converter instead feeds its buffer into the
 * ME network it is part of every tick, through {@link Ae2Compat}, so it needs AE2 to do anything. Flux only
 * goes in, from pipes or buckets, on any side.
 */
public class FluxConverterBlockEntity extends BlockEntity implements MenuProvider, ConverterView {
    private static final String TAG_FLUX = "flux";
    private static final String TAG_ENERGY = "energy";
    private static final String TAG_AE = "ae";
    private static final String TAG_OUTPUTS = "outputs";
    /** Every face outputs until the player switches some off. */
    private static final int ALL_FACES = 0b111111;

    private final ConverterType type;
    private final Tier tier;
    private final FluidTank tank;
    private final IFluidHandler inputView;
    /** The FE buffer: nothing can fill it from outside, anything may draw from it. */
    private final Buffer energy;
    /** The AE buffer of the AE converter, drawn by the ME network. */
    private double aeStored;
    /** The AE2 grid node of the AE converter, when AE2 is installed; see {@link Ae2Compat}. */
    @Nullable
    private Object aeLink;
    /** The views Forge hands out; the energy one is rebuilt when a face is switched. */
    private final LazyOptional<IFluidHandler> fluidCap;
    private LazyOptional<IEnergyStorage> energyCap;
    /** How much Flux was burned last tick, for the screen. */
    private int lastBurn;
    /** The faces that put power out, one bit per {@link Face} index. Flux goes in on every face regardless. */
    private int outputs = ALL_FACES;

    public FluxConverterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONVERTER.get(), pos, state);
        FluxConverterBlock block = (FluxConverterBlock) state.getBlock();
        this.type = block.type();
        this.tier = block.tier();
        this.tank = new FluxFluidTank(StorageSizes.converterTank(tier), stack -> ModFluids.isFlux(stack.getFluid())) {
            /**
             * Never less than what the tank holds. A tank can end up overfull (its block shrank in an
             * update); it still takes nothing more, and probes such as Jade, which refuse a fill
             * above 100%, see a full tank instead of crashing.
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
        this.inputView = new InputOnlyFluidHandler(tank);
        this.energy = new Buffer(StorageSizes.converterBuffer(tier, ConverterType.ENERGY));
        this.fluidCap = LazyOptional.of(() -> inputView);
        this.energyCap = LazyOptional.of(() -> energy);
    }

    /** The FE buffer: anything may draw from it, nothing may fill it, and the converter sets it directly. */
    private final class Buffer extends EnergyStorage {
        Buffer(int capacity) {
            super(capacity, 0, capacity);
        }

        void set(int amount) {
            energy = Mth.clamp(amount, 0, capacity);
            setChanged();
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            int taken = super.extractEnergy(toExtract, simulate);
            if (taken > 0 && !simulate) {
                setChanged();
            }
            return taken;
        }
    }

    // --- Parts -------------------------------------------------------------------------------

    @Override
    public ConverterType type() {
        return type;
    }

    @Override
    public Tier tier() {
        return tier;
    }

    @Override
    public Direction facing() {
        return getBlockState().getValue(FluxConverterBlock.FACING);
    }

    public FluidTank tank() {
        return tank;
    }

    /** The comparator signal: how full the Flux tank is, from 0 to 15. */
    public int comparatorSignal() {
        return FillLevel.comparatorSignal(tank.getFluidAmount(), tank.getCapacity());
    }

    /** What pipes and buckets see: a tank that only fills. */
    public IFluidHandler fluidHandler() {
        return inputView;
    }

    /**
     * The FE buffer as other mods see it from one side, or {@code null} on converters that make no
     * FE and on faces switched off. A null side (no particular face) always sees it.
     */
    @Nullable
    public IEnergyStorage energyHandler(@Nullable Direction side) {
        return type == ConverterType.ENERGY && outputsOn(side) ? energy : null;
    }

    /** Whether power goes out through this side; a null side counts as on. */
    public boolean outputsOn(@Nullable Direction side) {
        return side == null || (outputs & (1 << side.get3DDataValue())) != 0;
    }

    /** The output faces as a bit mask, one bit per {@link Face} index. */
    @Override
    public int outputMask() {
        return outputs;
    }

    /** Switches power output on or off at one face, and tells the neighbors and AE2. */
    @Override
    public void toggleOutput(Face face) {
        outputs ^= 1 << face.index();
        setChanged();
        if (level != null && !level.isClientSide) {
            // Whoever cached the energy view must ask again, now that a face changed.
            energyCap.invalidate();
            energyCap = LazyOptional.of(() -> energy);
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            Ae2Compat.updateConverterSides(aeLink);
        }
    }

    @Override
    public int fluxAmount() {
        return tank.getFluidAmount();
    }

    @Override
    @Nullable
    public FluxType storedFlux() {
        return tank.isEmpty() ? null : ModFluids.fluxTypeOf(tank.getFluid().getFluid());
    }

    /** What the output buffer holds, in FE or AE. */
    @Override
    public int stored() {
        return type == ConverterType.AE ? (int) aeStored : energy.getEnergyStored();
    }

    @Override
    public int bufferCapacity() {
        return StorageSizes.converterBuffer(tier, type);
    }

    /** What last tick's burn made, in FE or AE. */
    @Override
    public int output() {
        return lastBurn * (type == ConverterType.AE ? ConverterMath.AE_PER_MB : ConverterMath.FE_PER_MB);
    }

    @Override
    public int lastBurn() {
        return lastBurn;
    }

    /** The AE converter's grid node, or {@code null}; only {@link Ae2Compat} looks inside it. */
    @Nullable
    public Object aeLink() {
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

    public void writeOpenData(FriendlyByteBuf buf) {
        ConverterMenu.writeOpenData(buf, worldPosition, type, tier, facing());
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
        if (tank.isEmpty()) {
            return 0;
        }
        int perMb = type == ConverterType.AE ? ConverterMath.AE_PER_MB : ConverterMath.FE_PER_MB;
        long room = bufferCapacity() - (long) stored();
        int burn = ConverterMath.mbToBurn(tier, fluxAmount(), room, perMb);
        if (burn <= 0) {
            return 0;
        }
        tank.drain(burn, IFluidHandler.FluidAction.EXECUTE);
        if (type == ConverterType.AE) {
            aeStored += (double) burn * perMb;
            setChanged();
        } else {
            energy.set(energy.getEnergyStored() + burn * perMb);
        }
        return burn;
    }

    /** Hands the FE buffer to every neighbor that takes energy, one side after the other. */
    private void pushEnergy(Level level) {
        for (Direction side : Direction.values()) {
            if (energy.getEnergyStored() <= 0) {
                return;
            }
            if (!outputsOn(side)) {
                continue;
            }
            IEnergyStorage target = NeighborCapabilities.find(level, worldPosition.relative(side), ForgeCapabilities.ENERGY, side.getOpposite());
            if (target == null || !target.canReceive()) {
                continue;
            }
            // Offer everything; take out of the buffer only what the neighbor accepted.
            int taken = target.receiveEnergy(energy.getEnergyStored(), false);
            if (taken > 0) {
                energy.extractEnergy(taken, false);
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
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        SaveVersion.stamp(tag, SaveSchema.Kind.CONVERTER);
        tag.put(TAG_FLUX, tank.writeToNBT(new CompoundTag()));
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        tag.putDouble(TAG_AE, aeStored);
        tag.putInt(TAG_OUTPUTS, outputs);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // Version 1 is the only layout so far; reading it reports a save from a newer build.
        SaveVersion.read(SaveSchema.Kind.CONVERTER, tag);
        tank.readFromNBT(tag.getCompound(TAG_FLUX));
        energy.set(tag.getInt(TAG_ENERGY));
        aeStored = tag.getDouble(TAG_AE);
        outputs = tag.contains(TAG_OUTPUTS) ? tag.getInt(TAG_OUTPUTS) : ALL_FACES;
    }

    // --- Capabilities ------------------------------------------------------------------------

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        LazyOptional<T> node = Ae2Compat.gridNode(cap, aeLink);
        if (node.isPresent()) {
            return node;
        }
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidCap.cast();
        }
        if (cap == ForgeCapabilities.ENERGY && energyHandler(side) != null) {
            return energyCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidCap.invalidate();
        energyCap.invalidate();
    }
}
