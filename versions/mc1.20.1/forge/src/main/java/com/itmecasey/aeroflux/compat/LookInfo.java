package com.itmecasey.aeroflux.compat;

import com.itmecasey.aeroflux.block.entity.FluxCollectorBlockEntity;
import com.itmecasey.aeroflux.block.entity.FluxDimensions;
import com.itmecasey.aeroflux.block.entity.FluxTankBlockEntity;
import com.itmecasey.aeroflux.chunkloader.FluxChunkLoaderBlockEntity;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.converter.ConverterView;
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
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

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
        // Java 17 has no type patterns in switch, so this is a chain of checks.
        if (entity instanceof ConduitBundleBlockEntity bundle) {
            pipes(bundle.bundle(), lines);
        } else if (entity instanceof FluxMachineBlockEntity machine) {
            machine(machine, lines);
        } else if (entity instanceof FluxCollectorBlockEntity) {
            collector(level, lines);
        } else if (entity instanceof FluxConverterBlockEntity converter) {
            lines.add(Component.translatable("tooltip.aeroflux.look.converter",
                    converter.lastBurn() * (converter.type() == ConverterType.AE ? ConverterMath.AE_PER_MB : ConverterMath.FE_PER_MB),
                    converter.type() == ConverterType.AE ? "AE" : "FE"));
        } else if (entity instanceof ConverterView stress && stress.type() == ConverterType.STRESS) {
            // The Stress converter is a Create block entity, so it is only known by what it shows.
            lines.add(Component.translatable("tooltip.aeroflux.look.stress", stress.output(), stress.stored()));
        } else if (entity instanceof FluxChunkLoaderBlockEntity loader) {
            lines.add(Component.translatable(
                    "gui.aeroflux.chunk_loader.status." + loader.status().name().toLowerCase(Locale.ROOT)));
        }
        return lines;
    }

    /**
     * The Flux an AeroFlux block holds, for tooltips that draw a bar of it.
     *
     * @param amount   mB held
     * @param capacity mB it can hold
     * @param type     the kind of Flux, or {@code null} when it is empty or holds some other fluid
     * @param fluid    the fluid held, which a Flux Tank may hold besides Flux, or {@code null} when empty
     */
    public record Flux(long amount, long capacity, @Nullable FluxType type, @Nullable Fluid fluid) {
    }

    /** The Flux a block entity holds, or {@code null} for blocks that hold none. */
    @Nullable
    public static Flux flux(BlockEntity entity) {
        IFluidHandler handler = null;
        if (entity instanceof FluxTankBlockEntity tank) {
            handler = tank.tank();
        } else if (entity instanceof FluxCollectorBlockEntity collector) {
            handler = collector.buffer();
        } else if (entity instanceof FluxMachineBlockEntity machine) {
            handler = machine.fluxTank();
        } else if (entity instanceof FluxConverterBlockEntity converter) {
            handler = converter.tank();
        } else if (entity instanceof FluxChunkLoaderBlockEntity loader) {
            handler = loader.tank();
        } else if (entity instanceof ConverterView stress && stress.type() == ConverterType.STRESS) {
            // The Stress converter is a Create block entity: its tank is only reachable through the capability.
            handler = entity.getCapability(ForgeCapabilities.FLUID_HANDLER).orElse(null);
        }
        if (handler == null || handler.getTanks() == 0) {
            return null;
        }
        FluidStack held = handler.getFluidInTank(0);
        return new Flux(held.getAmount(), handler.getTankCapacity(0),
                held.isEmpty() ? null : ModFluids.fluxTypeOf(held.getFluid()), held.isEmpty() ? null : held.getFluid());
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
