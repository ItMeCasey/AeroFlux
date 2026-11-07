package com.itmecasey.aeroflux.compat;

import com.itmecasey.aeroflux.block.entity.FluxCollectorBlockEntity;
import com.itmecasey.aeroflux.block.entity.FluxDimensions;
import com.itmecasey.aeroflux.block.entity.FluxTankBlockEntity;
import com.itmecasey.aeroflux.chunkloader.FluxChunkLoaderBlockEntity;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.converter.FluxConverterBlockEntity;
import com.itmecasey.aeroflux.core.DimensionRules;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.conduit.BundleState;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.converter.ConverterMath;
import com.itmecasey.aeroflux.core.converter.ConverterType;
import com.itmecasey.aeroflux.core.machine.MachineStatus;
import com.itmecasey.aeroflux.machine.FluxMachineBlockEntity;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

/**
 * The lines AeroFlux adds to look-at tooltips, worked out on the server where the real state
 * lives. The Jade and The One Probe plugins both show these, so the two mods say the same thing.
 * Things those mods already show on their own (the fluid in a tank, the items in a machine) are
 * left to them.
 */
public final class LookInfo {
    private LookInfo() {
    }

    /** The extra lines for a block entity; empty when AeroFlux has nothing to add. */
    public static List<Component> lines(Level level, BlockEntity entity) {
        List<Component> lines = new ArrayList<>();
        switch (entity) {
            case ConduitBundleBlockEntity bundle -> pipes(bundle.bundle(), lines);
            case FluxMachineBlockEntity machine -> machine(machine, lines);
            case FluxCollectorBlockEntity collector -> collector(level, lines);
            case FluxConverterBlockEntity converter -> lines.add(Component.translatable("tooltip.aeroflux.look.converter",
                    converter.lastBurn() * (converter.type() == ConverterType.AE ? ConverterMath.AE_PER_MB : ConverterMath.FE_PER_MB),
                    converter.type() == ConverterType.AE ? "AE" : "FE"));
            case FluxChunkLoaderBlockEntity loader -> lines.add(Component.translatable(
                    "gui.aeroflux.chunk_loader.status." + loader.status().name().toLowerCase(Locale.ROOT)));
            default -> {
            }
        }
        return lines;
    }

    /**
     * The Flux an AeroFlux block holds, for tooltips that draw a bar of it.
     *
     * @param amount   mB held
     * @param capacity mB it can hold
     * @param type     the kind of Flux, or {@code null} when it is empty
     */
    public record Flux(long amount, long capacity, @Nullable FluxType type) {
    }

    /** The Flux a block entity holds, or {@code null} for blocks that hold none. */
    public static @Nullable Flux flux(BlockEntity entity) {
        ResourceHandler<FluidResource> handler = switch (entity) {
            case FluxTankBlockEntity tank -> tank.tank();
            case FluxCollectorBlockEntity collector -> collector.outputView();
            case FluxMachineBlockEntity machine -> machine.fluxTank();
            case FluxConverterBlockEntity converter -> converter.tank();
            case FluxChunkLoaderBlockEntity loader -> loader.tank();
            default -> null;
        };
        if (handler == null || handler.size() == 0) {
            return null;
        }
        FluidResource held = handler.getResource(0);
        // The capacity is the same for either flux, so ask about Aero Flux when the tank is empty.
        FluidResource probe = held.isEmpty() ? FluidResource.of(ModFluids.AERO_FLUX.get()) : held;
        return new Flux(handler.getAmountAsLong(0), handler.getCapacityAsLong(0, probe),
                held.isEmpty() ? null : ModFluids.fluxTypeOf(held));
    }

    /** One line per pipe in the bundle, by its item name, which carries the tier. */
    private static void pipes(BundleState bundle, List<Component> lines) {
        for (PipeType type : bundle.set().types()) {
            lines.add(new ItemStack(ModItems.pipe(type, bundle.tier(type.lane())).get()).getHoverName().copy()
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /** What the machine is doing, why it stopped if it did, and its upgrades. */
    private static void machine(FluxMachineBlockEntity machine, List<Component> lines) {
        MachineStatus status = machine.redstoneDisabled() ? MachineStatus.IDLE : machine.status();
        String key = machine.redstoneDisabled() ? "disabled" : status.name().toLowerCase(Locale.ROOT);
        lines.add(Component.translatable("machine.aeroflux.status." + key));
        int upgrades = machine.speedUpgrades() + machine.capacityUpgrades();
        if (upgrades > 0) {
            lines.add(Component.translatable("tooltip.aeroflux.look.upgrades", machine.speedUpgrades(), machine.capacityUpgrades())
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /** Only worth a line when this dimension's rules switch collecting off. */
    private static void collector(Level level, List<Component> lines) {
        DimensionRules rules = FluxDimensions.rulesFor(level);
        if (!rules.aeroEnabled() && !rules.moonEnabled()) {
            lines.add(Component.translatable("tooltip.aeroflux.look.dimension_off").withStyle(ChatFormatting.RED));
        }
    }
}
