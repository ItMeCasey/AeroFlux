package com.itmecasey.aeroflux.block.entity;

import com.itmecasey.aeroflux.AeroFluxConfig;
import com.itmecasey.aeroflux.block.FluxTankBlock;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.itmecasey.aeroflux.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Holds the fluid of a {@link FluxTankBlock}: Aero Flux or Moon Flux, never both at once. */
public class FluxTankBlockEntity extends BlockEntity {
    private final FluidTank tank;
    private final LazyOptional<IFluidHandler> handler;

    public FluxTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TANK.get(), pos, state);
        int capacity = AeroFluxConfig.tankCapacity(((FluxTankBlock) state.getBlock()).tier());
        this.tank = new FluidTank(capacity, stack -> ModFluids.isFlux(stack.getFluid())) {
            @Override
            protected void onContentsChanged() {
                setChanged();
            }
        };
        this.handler = LazyOptional.of(() -> tank);
    }

    /** The tank itself, exposed to pipes and containers through the fluid capability. */
    public FluidTank tank() {
        return tank;
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        // Tanks accept and give fluid on every side.
        if (capability == ForgeCapabilities.FLUID_HANDLER) {
            return handler.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        handler.invalidate();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.writeTo(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tank.readFrom(input);
    }
}
