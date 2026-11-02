package com.itmecasey.aeroflux.creative;

import com.itmecasey.aeroflux.block.entity.TankView;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * The working part of a {@link CreativeFluxTankBlock}: one flux, never running out, pushed into
 * every neighbor that takes it each tick. The flux it gives is saved and sent to clients, which
 * draw it full in the glass.
 */
public class CreativeFluxTankBlockEntity extends BlockEntity implements TankView {
    /** The most pushed into one neighbor per tick; more than any pipe or machine takes. */
    private static final int PUSH_LIMIT = 100_000;
    private static final String TAG_FLUX = "flux";

    private FluxType flux = FluxType.AERO;
    private final CreativeFluxHandler handler = new CreativeFluxHandler(() -> flux);
    private final LazyOptional<IFluidHandler> capability = LazyOptional.of(() -> handler);

    public CreativeFluxTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CREATIVE_TANK.get(), pos, state);
    }

    @Override
    public IFluidHandler tank() {
        return handler;
    }

    /** The flux it gives. */
    public FluxType flux() {
        return flux;
    }

    /** Moves on to the next flux (Aero, Moon, Twilight, then Aero again) and returns it. */
    public FluxType cycleFlux() {
        FluxType[] all = FluxType.values();
        flux = all[(flux.ordinal() + 1) % all.length];
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        return flux;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CreativeFluxTankBlockEntity tank) {
        for (Direction direction : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(pos.relative(direction));
            // Two creative tanks side by side would only pour into each other's void.
            if (neighbor == null || neighbor instanceof CreativeFluxTankBlockEntity) {
                continue;
            }
            neighbor.getCapability(ForgeCapabilities.FLUID_HANDLER, direction.getOpposite())
                    .ifPresent(target -> FluidUtil.tryFluidTransfer(target, tank.handler, PUSH_LIMIT, true));
        }
    }

    /** It gives and swallows flux on every side. */
    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return capability.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        capability.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString(TAG_FLUX, flux.id());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        String saved = tag.contains(TAG_FLUX) ? tag.getString(TAG_FLUX) : FluxType.AERO.id();
        try {
            flux = FluxType.valueOf(saved.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            flux = FluxType.AERO;
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
