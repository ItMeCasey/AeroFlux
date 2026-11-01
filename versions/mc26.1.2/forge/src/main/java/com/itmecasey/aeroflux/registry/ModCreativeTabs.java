package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** The creative tabs AeroFlux adds. */
public final class ModCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AeroFlux.MODID);

    /** The main tab; other content is added to its display list as it is built. */
    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + AeroFlux.MODID))
                    .icon(() -> ModItems.FLUX_DUST.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.FLUX_DUST.get());
                        output.accept(ModItems.AERO_FLUX_BUCKET.get());
                        output.accept(ModItems.MOON_FLUX_BUCKET.get());
                        output.accept(ModItems.FLUX_WRENCH.get());
                        output.accept(ModItems.PIPE_NODE.get());
                        for (PipeType type : PipeType.values()) {
                            output.accept(ModItems.pipe(type).get());
                        }
                        for (Tier tier : Tier.values()) {
                            output.accept(ModBlocks.collector(tier).get());
                            output.accept(ModBlocks.tank(tier).get());
                        }
                    })
                    .build());

    private ModCreativeTabs() {
    }

    /** Hooks this registry into the mod event bus group; call once from the mod constructor. */
    public static void register(BusGroup modBusGroup) {
        TABS.register(modBusGroup);
    }
}
