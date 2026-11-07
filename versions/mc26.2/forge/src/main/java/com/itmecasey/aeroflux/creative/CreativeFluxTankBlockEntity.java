package com.itmecasey.aeroflux.creative;

import com.itmecasey.aeroflux.block.entity.TankView;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.save.SaveSchema;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.itmecasey.aeroflux.save.SaveVersion;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    private final LazyOptional<IFluidHandler> handlerOptional = LazyOptional.of(() -> handler);

    public CreativeFluxTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CREATIVE_TANK.get(), pos, state);
    }

    @Override
    public CreativeFluxHandler tank() {
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
            BlockPos neighborPos = pos.relative(direction);
            // Two creative tanks side by side would only pour into each other's void.
            if (level.getBlockEntity(neighborPos) instanceof CreativeFluxTankBlockEntity) {
                continue;
            }
            IFluidHandler neighbor = FluidUtil.getFluidHandler(level, neighborPos, direction.getOpposite())
                    .resolve().orElse(null);
            if (neighbor != null) {
                FluidUtil.tryFluidTransfer(neighbor, tank.handler, PUSH_LIMIT, true);
            }
        }
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        // The creative tank gives its flux and swallows any flux on every side.
        if (capability == ForgeCapabilities.FLUID_HANDLER) {
            return handlerOptional.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        handlerOptional.invalidate();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        SaveVersion.stamp(output, SaveSchema.Kind.CREATIVE_TANK);
        output.putString(TAG_FLUX, flux.id());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        // Version 1 is the only layout so far; reading it reports a save from a newer build.
        SaveVersion.read(SaveSchema.Kind.CREATIVE_TANK, input);
        String saved = input.getStringOr(TAG_FLUX, FluxType.AERO.id());
        try {
            flux = FluxType.valueOf(saved.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            flux = FluxType.AERO;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
