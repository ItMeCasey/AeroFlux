package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.converter.FluxConverterBlock;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.PipeTiers;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.machine.MachineUpgrades;
import com.itmecasey.aeroflux.lamp.FluxLampBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The creative tabs AeroFlux adds. */
public final class ModCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AeroFlux.MODID);

    /** The main tab; other content is added to its display list as it is built. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + AeroFlux.MODID))
                    .icon(() -> ModItems.FLUX_DUST.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.FLUX_DUST.get());
                        output.accept(ModItems.AERO_CRYSTAL.get());
                        output.accept(ModItems.MOON_CRYSTAL.get());
                        output.accept(ModItems.FLUXSTEEL_INGOT.get());
                        output.accept(ModItems.AEROGOLD_INGOT.get());
                        output.accept(ModItems.SAWDUST.get());
                        output.accept(ModItems.AERO_FLUX_BUCKET.get());
                        output.accept(ModItems.MOON_FLUX_BUCKET.get());
                        output.accept(ModItems.TWILIGHT_FLUX_BUCKET.get());
                        output.accept(ModItems.GUIDEBOOK.get());
                        output.accept(ModItems.FLUX_WRENCH.get());
                        output.accept(ModItems.CONDUIT_TOOL.get());
                        output.accept(ModItems.SPEED_UPGRADE.get());
                        output.accept(ModItems.CAPACITY_UPGRADE.get());
                        output.accept(ModItems.FILTER_UPGRADE.get());
                        output.accept(ModItems.PRIORITY_UPGRADE.get());
                        for (MachineUpgrades.Special kind : MachineUpgrades.Special.values()) {
                            output.accept(ModItems.machineUpgrade(kind).get());
                        }
                        for (Tier tier : Tier.values()) {
                            if (tier != Tier.COPPER) {
                                output.accept(ModItems.upgradeCard(tier).get());
                            }
                        }
                        for (PipeType type : PipeType.values()) {
                            if (PipeTiers.isTiered(type)) {
                                for (Tier tier : Tier.values()) {
                                    output.accept(ModItems.pipe(type, tier).get());
                                }
                            } else {
                                output.accept(ModItems.pipe(type, Tier.COPPER).get());
                            }
                        }
                        for (Tier tier : Tier.values()) {
                            output.accept(ModBlocks.collector(tier).get());
                            output.accept(ModBlocks.tank(tier).get());
                        }
                        output.accept(ModBlocks.CREATIVE_TANK.get());
                        for (MachineType machine : MachineType.values()) {
                            for (Tier tier : Tier.values()) {
                                output.accept(ModBlocks.machine(machine, tier).get());
                            }
                        }
                        for (Tier tier : Tier.values()) {
                            output.accept(ModBlocks.chunkLoader(tier).get());
                        }
                        for (DeferredBlock<FluxConverterBlock> converter : ModBlocks.converters()) {
                            output.accept(converter.get());
                        }
                        for (DeferredBlock<FluxLampBlock> lamp : ModBlocks.lamps()) {
                            output.accept(lamp.get());
                        }
                    })
                    .build());

    private ModCreativeTabs() {
    }

    /** Hooks this registry into the mod event bus; call once from the mod constructor. */
    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
