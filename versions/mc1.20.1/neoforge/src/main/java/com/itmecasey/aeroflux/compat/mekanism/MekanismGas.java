package com.itmecasey.aeroflux.compat.mekanism;

import com.itmecasey.aeroflux.conduit.NeighborCapabilities;
import javax.annotation.Nullable;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

/**
 * The part of the Mekanism support that names Mekanism classes. Only code that has checked
 * {@link MekanismCompat#loaded()} reaches it.
 *
 * <p>Mekanism 10.4 keeps gases, infusion types, pigments and slurries apart, each with its own
 * capability. Gas pipes carry gases. Forge hands out one capability per interface, so asking for
 * the {@link IGasHandler} capability finds Mekanism's own without reaching into its internals.
 */
public final class MekanismGas {
    /** Gas handlers of blocks and items. */
    public static final Capability<IGasHandler> HANDLER = CapabilityManager.get(new CapabilityToken<>() {
    });

    private MekanismGas() {
    }

    /** The gas handler of the block at {@code pos} on {@code side}, or {@code null}. */
    public static @Nullable IGasHandler handler(Level level, BlockPos pos, @Nullable Direction side) {
        return NeighborCapabilities.find(level, pos, HANDLER, side);
    }

    /** The registry id of a gas, the form gas filters store. */
    public static String id(GasStack stack) {
        return stack.getTypeRegistryName().toString();
    }

    /** The id of the first gas a stack holds, or {@code null}. */
    static @Nullable String gasIn(ItemStack stack) {
        IGasHandler handler = stack.getCapability(HANDLER).orElse(null);
        if (handler == null) {
            return null;
        }
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            GasStack held = handler.getChemicalInTank(tank);
            if (!held.isEmpty()) {
                return id(held);
            }
        }
        return null;
    }
}
